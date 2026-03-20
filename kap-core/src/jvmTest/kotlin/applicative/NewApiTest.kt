package applicative

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for newly added API: [raceAgainst] extension.
 */
class NewApiTest {

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
