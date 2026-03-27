import kap.*
import kotlinx.coroutines.delay

// ── Class example ──────────────────────────────────────────────

@KapTypeSafe
data class User(val firstName: String, val lastName: String, val age: Int)

suspend fun fetchFirstName(): String { delay(30); return "Alice" }
suspend fun fetchLastName(): String { delay(20); return "Smith" }
suspend fun fetchAge(): Int { delay(10); return 30 }

// ── Function example ───────────────────────────────────────────

data class Dashboard(val userName: String, val cartSummary: String, val promoCode: String)

@KapTypeSafe
fun buildDashboard(userName: String, cartSummary: String, promoCode: String): Dashboard =
    Dashboard(userName, cartSummary, promoCode)

suspend fun fetchUserName(): String { delay(30); return "Alice" }
suspend fun fetchCartSummary(): String { delay(20); return "3 items, $147.50" }
suspend fun fetchPromoCode(): String { delay(10); return "SAVE20" }

// ── Main ───────────────────────────────────────────────────────

suspend fun main() {
    println("=== KSP Type-Safe Demo ===\n")

    // Class: unsafe vs safe
    val unsafeUser = Async {
        kap(::User)
            .with { fetchFirstName() }
            .with { fetchLastName() }
            .with { fetchAge() }
    }
    println("  Unsafe class:    $unsafeUser")

    val safeUser = Async {
        kapSafe(::User)
            .with { UserFirstName(fetchFirstName()) }
            .with { UserLastName(fetchLastName()) }
            .with { UserAge(fetchAge()) }
    }
    println("  Safe class:      $safeUser")

    // Function: unsafe vs safe
    val unsafeDash = Async {
        kap(::buildDashboard)
            .with { fetchUserName() }
            .with { fetchCartSummary() }
            .with { fetchPromoCode() }
    }
    println("  Unsafe function: $unsafeDash")

    val safeDash = Async {
        kapSafeBuildDashboard(::buildDashboard)
            .with { BuildDashboardUserName(fetchUserName()) }
            .with { BuildDashboardCartSummary(fetchCartSummary()) }
            .with { BuildDashboardPromoCode(fetchPromoCode()) }
    }
    println("  Safe function:   $safeDash")

    println("\n  Swap any same-typed .with in the safe variants — the compiler rejects it!")
}
