---
hide:
  - toc
---

# Playground

Interactive examples powered by [Kotlin Playground](https://kotlinlang.org/docs/kotlin-tour-hello-world.html). Edit and run directly in your browser.

<script src="https://unpkg.com/kotlin-playground@1" data-selector=".kotlin-playground"></script>

<style>
.kotlin-playground { margin: 1.5rem 0; }
</style>

---

## Parallel Execution — `.with`

Three services fetched in parallel. Total time = max(individual), not sum.

<div class="kotlin-playground" theme="darcula">

import kotlinx.coroutines.*

data class Dashboard(val user: String, val cart: String, val promos: String)

suspend fun fetchUser(): String { delay(300); return "Alice" }
suspend fun fetchCart(): String { delay(200); return "3 items" }
suspend fun fetchPromos(): String { delay(100); return "SAVE20" }

suspend fun main() {
    val start = System.currentTimeMillis()

    // Parallel execution with coroutineScope
    val result = coroutineScope {
        val dUser = async { fetchUser() }
        val dCart = async { fetchCart() }
        val dPromos = async { fetchPromos() }
        Dashboard(dUser.await(), dCart.await(), dPromos.await())
    }

    val elapsed = System.currentTimeMillis() - start
    println("Result: $result")
    println("Time: ${elapsed}ms (not 600ms — parallel!)")

    // With KAP this would be:
    // val result = Async {
    //     kap(::Dashboard)
    //         .with { fetchUser() }
    //         .with { fetchCart() }
    //         .with { fetchPromos() }
    // }
}
</div>

!!! info "Note"
    KAP itself can't run in Kotlin Playground (it needs a Maven dependency). These examples show the **coroutine patterns** that KAP simplifies. The commented KAP code shows the equivalent.

---

## Phase Barriers — `.then`

Phase 2 waits for phase 1 to complete:

<div class="kotlin-playground" theme="darcula">

import kotlinx.coroutines.*

suspend fun fetchA(): String { delay(200); return "A" }
suspend fun fetchB(): String { delay(150); return "B" }
suspend fun validate(a: String, b: String): String { delay(100); return "valid($a,$b)" }

suspend fun main() {
    val start = System.currentTimeMillis()

    val result = coroutineScope {
        // Phase 1: parallel
        val dA = async { fetchA() }
        val dB = async { fetchB() }
        val a = dA.await()
        val b = dB.await()

        // Phase 2: barrier — needs A and B
        val validated = validate(a, b)

        Triple(a, b, validated)
    }

    val elapsed = System.currentTimeMillis() - start
    println("Result: $result")
    println("Time: ${elapsed}ms (200ms parallel + 100ms barrier = ~300ms)")

    // With KAP:
    // kap(::Triple)
    //     .with { fetchA() }       // ┐ parallel
    //     .with { fetchB() }       // ┘
    //     .then { validate() }     // ── barrier
}
</div>

---

## Value-Dependent Phases — `.andThen`

Phase 2 uses phase 1's result:

<div class="kotlin-playground" theme="darcula">

import kotlinx.coroutines.*

data class UserContext(val profile: String, val prefs: String)
data class Enriched(val recs: String, val promos: String)

suspend fun fetchProfile(id: String): String { delay(200); return "profile-$id" }
suspend fun fetchPrefs(id: String): String { delay(150); return "prefs-$id" }
suspend fun fetchRecs(profile: String): String { delay(100); return "recs-for-$profile" }
suspend fun fetchPromos(prefs: String): String { delay(80); return "promos-for-$prefs" }

suspend fun main() {
    val start = System.currentTimeMillis()

    // Phase 1
    val ctx = coroutineScope {
        val dProfile = async { fetchProfile("user-42") }
        val dPrefs = async { fetchPrefs("user-42") }
        UserContext(dProfile.await(), dPrefs.await())
    }

    // Phase 2 — depends on ctx
    val enriched = coroutineScope {
        val dRecs = async { fetchRecs(ctx.profile) }
        val dPromos = async { fetchPromos(ctx.prefs) }
        Enriched(dRecs.await(), dPromos.await())
    }

    val elapsed = System.currentTimeMillis() - start
    println("Context: $ctx")
    println("Enriched: $enriched")
    println("Time: ${elapsed}ms (200ms + 100ms = ~300ms, not 530ms)")

    // With KAP — one flat chain:
    // kap(::UserContext)
    //     .with { fetchProfile("user-42") }
    //     .with { fetchPrefs("user-42") }
    //     .andThen { ctx ->
    //         kap(::Enriched)
    //             .with { fetchRecs(ctx.profile) }
    //             .with { fetchPromos(ctx.prefs) }
    //     }
}
</div>

---

## Partial Failure — `.settled()`

One service fails, the rest still complete:

<div class="kotlin-playground" theme="darcula">

import kotlinx.coroutines.*

data class Dashboard(val user: String, val cart: String, val config: String)

suspend fun fetchUserMayFail(): String { throw RuntimeException("user service down") }
suspend fun fetchCart(): String { delay(100); return "cart-ok" }
suspend fun fetchConfig(): String { delay(80); return "config-ok" }

suspend fun main() {
    // With raw coroutines: supervisorScope + try/catch
    val result = supervisorScope {
        val dUser = async { fetchUserMayFail() }
        val dCart = async { fetchCart() }
        val dConfig = async { fetchConfig() }
        val user = try { dUser.await() } catch (e: Exception) { "anonymous" }
        Dashboard(user, dCart.await(), dConfig.await())
    }

    println("Result: $result")
    println("User failed but dashboard still built with fallback!")

    // With KAP:
    // kap { user: Result<String>, cart: String, config: String ->
    //     Dashboard(user.getOrDefault("anonymous"), cart, config)
    // }
    //     .with(Kap { fetchUserMayFail() }.settled())
    //     .with { fetchCart() }
    //     .with { fetchConfig() }
}
</div>

---

## Racing — First to Succeed Wins

<div class="kotlin-playground" theme="darcula">

import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

suspend fun fetchUS(): String { delay(300); return "US-data" }
suspend fun fetchEU(): String { delay(100); return "EU-data" }
suspend fun fetchAP(): String { delay(200); return "AP-data" }

suspend fun main() {
    val start = System.currentTimeMillis()

    // Race three regions
    val winner = coroutineScope {
        val us = async { fetchUS() }
        val eu = async { fetchEU() }
        val ap = async { fetchAP() }
        select {
            us.onAwait { it }
            eu.onAwait { it }
            ap.onAwait { it }
        }.also {
            us.cancel(); eu.cancel(); ap.cancel()
        }
    }

    val elapsed = System.currentTimeMillis() - start
    println("Winner: $winner in ${elapsed}ms")
    println("EU won at ~100ms. US and AP cancelled.")

    // With KAP:
    // raceN(
    //     Kap { fetchUS() },
    //     Kap { fetchEU() },
    //     Kap { fetchAP() },
    // )
}
</div>

---

## Bounded Concurrency — `traverse(concurrency)`

<div class="kotlin-playground" theme="darcula">

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

suspend fun fetchUser(id: Int): String { delay(100); return "user-$id" }

suspend fun main() {
    val ids = (1..20).toList()
    val start = System.currentTimeMillis()

    // Bounded parallel: max 5 concurrent
    val semaphore = Semaphore(5)
    val results = coroutineScope {
        ids.map { id ->
            async { semaphore.withPermit { fetchUser(id) } }
        }.awaitAll()
    }

    val elapsed = System.currentTimeMillis() - start
    println("Fetched ${results.size} users in ${elapsed}ms")
    println("With concurrency=5: ~400ms (4 batches × 100ms)")
    println("Sequential would be: ${ids.size * 100}ms")

    // With KAP:
    // ids.traverse(concurrency = 5) { id -> Kap { fetchUser(id) } }
}
</div>

---

Ready to try KAP? [Get Started](guide/quickstart.md){ .md-button .md-button--primary }
