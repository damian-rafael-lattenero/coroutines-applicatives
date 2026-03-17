package applicative

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for newly added API: [Nel] typealias, [accumulate] builder, and [raceAgainst] extension.
 */
class NewApiTest {

    // ════════════════════════════════════════════════════════════════════════
    // Nel typealias
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Nel is a typealias for NonEmptyList`() = runTest {
        val nel: Nel<String> = NonEmptyList.of("a", "b", "c")
        assertEquals(3, nel.size)
        assertEquals("a", nel.head)
        assertEquals(listOf("b", "c"), nel.tail)
    }

    @Test
    fun `Nel works in validated computation signatures`() = runTest {
        val result: Either<Nel<String>, Int> = Either.Left(NonEmptyList.of("err1", "err2"))
        assertIs<Either.Left<Nel<String>>>(result)
        assertEquals(2, result.value.size)
    }

    @Test
    fun `Nel is interchangeable with NonEmptyList in function types`() = runTest {
        fun accepts(nel: Nel<Int>): Int = nel.head
        val nel: NonEmptyList<Int> = NonEmptyList.of(42, 99)
        assertEquals(42, accepts(nel))
    }

    @Test
    fun `Nel works in zipV result type`() = runTest {
        val result = Async {
            zipV(
                { Either.Right("ok") as Either<Nel<String>, String> },
                { Either.Left("bad".toNonEmptyList()) as Either<Nel<String>, String> },
            ) { a, b -> "$a|$b" }
        }
        assertIs<Either.Left<Nel<String>>>(result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // accumulate {} builder
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `accumulate is an alias for validated`() = runTest {
        val result = Async {
            accumulate<String, String> {
                val a = (Either.Right(42) as Either<NonEmptyList<String>, Int>).bind()
                val b = (Either.Right("hello") as Either<NonEmptyList<String>, String>).bind()
                "$b=$a"
            }
        }
        assertEquals(Either.Right("hello=42"), result)
    }

    @Test
    fun `accumulate short-circuits on first Left`() = runTest {
        var secondPhaseRan = false
        val result = Async {
            accumulate<String, String> {
                val a = (Either.Left("err1".toNonEmptyList()) as Either<Nel<String>, Int>).bind()
                secondPhaseRan = true
                "should not reach: $a"
            }
        }
        assertIs<Either.Left<Nel<String>>>(result)
        assertEquals(listOf("err1"), result.value.toList())
        assertEquals(false, secondPhaseRan)
    }

    @Test
    fun `accumulate with parallel zipV phases and sequential short-circuit`() = runTest {
        val result = Async {
            accumulate<String, String> {
                // Phase 1: parallel accumulation — both pass
                val (name, email) = zipV(
                    { Either.Right("Alice") as Either<Nel<String>, String> },
                    { Either.Right("alice@test.com") as Either<Nel<String>, String> },
                ) { n, e -> n to e }
                    .bindV()

                // Phase 2: depends on phase 1
                val greeting = zipV(
                    { Either.Right("Hello, $name!") as Either<Nel<String>, String> },
                    { Either.Right("Contact: $email") as Either<Nel<String>, String> },
                ) { g, c -> "$g | $c" }
                    .bindV()

                greeting
            }
        }
        assertEquals(Either.Right("Hello, Alice! | Contact: alice@test.com"), result)
    }

    @Test
    fun `accumulate short-circuits between phases, skipping phase 2`() = runTest {
        var phase2Ran = false
        val result = Async {
            accumulate<String, String> {
                // Phase 1: accumulates errors from both branches
                val identity = zipV(
                    { Either.Left("name-bad".toNonEmptyList()) as Either<Nel<String>, String> },
                    { Either.Left("email-bad".toNonEmptyList()) as Either<Nel<String>, String> },
                ) { n, e -> "$n|$e" }
                    .bindV()  // should short-circuit here with 2 errors

                phase2Ran = true
                identity
            }
        }
        assertIs<Either.Left<Nel<String>>>(result)
        assertEquals(listOf("name-bad", "email-bad"), result.value.toList())
        assertEquals(false, phase2Ran, "Phase 2 should not run when phase 1 fails")
    }

    // ════════════════════════════════════════════════════════════════════════
    // raceAgainst extension
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `raceAgainst returns faster computation result`() = runTest {
        val result = Async {
            Computation { delay(100); "slow" }
                .raceAgainst(Computation { delay(10); "fast" })
        }
        assertEquals("fast", result)
    }

    @Test
    fun `raceAgainst is equivalent to race top-level function`() = runTest {
        val primary = Computation { delay(10); "primary" }
        val secondary = Computation { delay(100); "secondary" }

        val viaExtension = Async { primary.raceAgainst(secondary) }
        val viaTopLevel = Async { race(primary, secondary) }
        assertEquals(viaTopLevel, viaExtension)
    }

    @Test
    fun `raceAgainst falls back when primary fails`() = runTest {
        val result = Async {
            Computation<String> { error("primary failed") }
                .raceAgainst(Computation { delay(10); "fallback" })
        }
        assertEquals("fallback", result)
    }

    @Test
    fun `raceAgainst chains with other combinators`() = runTest {
        val result = Async {
            Computation { delay(200); "slow-primary" }
                .raceAgainst(Computation { delay(10); "fast-replica" })
                .map { it.uppercase() }
        }
        assertEquals("FAST-REPLICA", result)
    }
}
