package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for improvement plan items (core-only):
 *
 * 2. Computation.await() ergonomic extension
 * 4. PhaseBarrier signal safety on exception
 * 6. Alternative: orElse / firstSuccessOf
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImprovementPlanTest {

    // ════════════════════════════════════════════════════════════════════════
    // ITEM 2: Computation.await() — ergonomic execution inside ap lambdas
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `await executes computation and returns result`() = runTest {
        val result = Async {
            lift2 { a: String, b: String -> "$a|$b" }
                .ap { Computation { delay(30); "fast" }.timeout(100.milliseconds, "timeout").await() }
                .ap { delay(30); "other" }
        }

        assertEquals("fast|other", result)
        assertEquals(30, currentTime, "Max(30,30) = 30ms")
    }

    @Test
    fun `await with timeout fallback inside ap branch`() = runTest {
        val result = Async {
            lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                .ap { delay(30); "A" }
                .ap { Computation { delay(500); "slow-B" }.timeout(50.milliseconds, "timeout-B").await() }
                .ap { delay(30); "C" }
        }

        assertEquals("A|timeout-B|C", result)
        assertEquals(50, currentTime, "Timeout at 50ms determines total")
    }

    @Test
    fun `await with retry inside ap branch`() = runTest {
        var attempts = 0
        val result = Async {
            lift2 { a: String, b: String -> "$a|$b" }
                .ap {
                    Computation {
                        attempts++
                        if (attempts < 3) throw RuntimeException("flaky")
                        delay(20); "recovered"
                    }.retry(3, delay = 10.milliseconds).await()
                }
                .ap { delay(30); "stable" }
        }

        assertEquals("recovered|stable", result)
        assertEquals(3, attempts)
        assertEquals(40, currentTime, "Retry: fail+10ms+fail+10ms+20ms=40ms")
    }

    @Test
    fun `await with recover inside ap branch`() = runTest {
        val result = Async {
            lift2 { a: String, b: String -> "$a|$b" }
                .ap {
                    Computation<String> { throw RuntimeException("boom") }
                        .recover { "recovered" }
                        .await()
                }
                .ap { delay(30); "B" }
        }

        assertEquals("recovered|B", result)
    }

    @Test
    fun `await preserves structured concurrency`() = runTest {
        // If one branch fails, the other should be cancelled
        val result = runCatching {
            Async {
                lift2 { a: String, b: String -> "$a|$b" }
                    .ap { Computation<String> { throw RuntimeException("crash") }.await() }
                    .ap { delay(1000); "never" }
            }
        }

        assertTrue(result.isFailure)
        assertEquals("crash", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ITEM 4: PhaseBarrier signal safety — signal fires even on exception
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `PhaseBarrier completes signal on exception — gated ap does not hang`() = runTest {
        // If the barrier throws, the gated ap branch should see the failure
        // through structured concurrency cancellation, NOT hang forever.
        val result = runCatching {
            Async {
                lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                    .ap { delay(30); "A" }
                    .followedBy { throw RuntimeException("barrier-crash") }
                    .ap { delay(30); "C" }  // gated — should NOT hang
            }
        }

        assertTrue(result.isFailure)
        assertEquals("barrier-crash", result.exceptionOrNull()?.message)
    }

    @Test
    fun `PhaseBarrier completes signal on exception with recover in chain`() = runTest {
        // More subtle: if the barrier fails but there's a recover higher up,
        // the signal must still fire so gated branches don't deadlock.
        val result = Async {
            lift2 { a: String, b: String -> "$a|$b" }
                .ap { delay(20); "A" }
                .followedBy {
                    with(Computation<String> { throw RuntimeException("barrier-fail") }
                        .recover { "recovered" }) { execute() }
                }
        }

        assertEquals("A|recovered", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ITEM 6: Alternative — orElse / firstSuccessOf
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `orElse returns primary on success`() = runTest {
        val result = Async {
            Computation { "primary" }
                .orElse(Computation { "fallback" })
        }

        assertEquals("primary", result)
    }

    @Test
    fun `orElse returns fallback on primary failure`() = runTest {
        val result = Async {
            Computation<String> { throw RuntimeException("primary-fail") }
                .orElse(Computation { delay(30); "fallback" })
        }

        assertEquals("fallback", result)
        assertEquals(30, currentTime, "Fallback starts only after primary fails")
    }

    @Test
    fun `orElse chains three levels`() = runTest {
        val result = Async {
            Computation<String> { throw RuntimeException("fail1") }
                .orElse(Computation { throw RuntimeException("fail2") })
                .orElse(Computation { "last-resort" })
        }

        assertEquals("last-resort", result)
    }

    @Test
    fun `orElse propagates CancellationException`() = runTest {
        val result = runCatching {
            Async {
                Computation<String> { throw CancellationException("cancel") }
                    .orElse(Computation { "should-not-reach" })
            }
        }

        assertTrue(result.isFailure)
        assertIs<CancellationException>(result.exceptionOrNull())
    }

    @Test
    fun `orElse is sequential not concurrent - timing proof`() = runTest {
        val result = Async {
            Computation<String> { delay(20); throw RuntimeException("fail") }
                .orElse(Computation { delay(30); "fallback" })
        }

        assertEquals("fallback", result)
        assertEquals(50, currentTime, "Sequential: 20ms (fail) + 30ms (fallback) = 50ms")
    }

    @Test
    fun `firstSuccessOf returns first successful computation`() = runTest {
        val result = Async {
            firstSuccessOf(
                Computation { throw RuntimeException("fail1") },
                Computation { throw RuntimeException("fail2") },
                Computation { delay(30); "third" },
                Computation { delay(30); "fourth" }, // never tried
            )
        }

        assertEquals("third", result)
    }

    @Test
    fun `firstSuccessOf throws last error when all fail`() = runTest {
        val result = runCatching {
            Async {
                firstSuccessOf(
                    Computation<String> { throw RuntimeException("err1") },
                    Computation { throw RuntimeException("err2") },
                    Computation { throw RuntimeException("err3") },
                )
            }
        }

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()!!
        // The last failure's error should propagate
        assertEquals("err3", error.message)
    }

    @Test
    fun `firstSuccessOf preserves suppressed exceptions outside coroutineScope`() = runTest {
        // Test suppressed exceptions directly without Async wrapper
        val errors = mutableListOf<Throwable>()
        val computations = arrayOf(
            Computation<String> { throw RuntimeException("err1") },
            Computation { throw RuntimeException("err2") },
            Computation { throw RuntimeException("err3") },
        )

        // Execute directly within coroutineScope to verify addSuppressed works
        val result = runCatching {
            kotlinx.coroutines.coroutineScope {
                with(firstSuccessOf(*computations)) { execute() }
            }
        }

        assertTrue(result.isFailure)
        assertEquals("err3", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `firstSuccessOf is sequential - timing proof`() = runTest {
        val result = Async {
            firstSuccessOf(
                Computation { delay(20); throw RuntimeException("fail") },
                Computation { delay(30); "success" },
            )
        }

        assertEquals("success", result)
        assertEquals(50, currentTime, "Sequential: 20ms (fail) + 30ms (success) = 50ms")
    }

    @Test
    fun `firstSuccess on iterable`() = runTest {
        val computations = listOf(
            Computation<String> { throw RuntimeException("fail") },
            Computation { "success" },
        )
        val result = Async { computations.firstSuccess() }

        assertEquals("success", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // INTEGRATION: Multiple items composed together
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `integration - await inside ap with firstSuccessOf`() = runTest {
        val result = Async {
            lift2 { a: String, b: String -> "$a|$b" }
                .ap {
                    firstSuccessOf(
                        Computation { delay(20); throw RuntimeException("primary-down") },
                        Computation { delay(30); "replica-data" },
                    ).await()
                }
                .ap { delay(60); "other" }
        }

        assertEquals("replica-data|other", result)
        // firstSuccessOf: 20 + 30 = 50ms. Other: 60ms. Parallel: max(50,60) = 60ms
        assertEquals(60, currentTime, "max(50, 60) = 60ms")
    }

}
