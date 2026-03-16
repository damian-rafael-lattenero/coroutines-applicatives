# Coroutines Applicatives

**Type-safe parallel coroutine composition — your code shape *is* the execution plan.**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![Coroutines](https://img.shields.io/badge/Coroutines-1.9.0-blue.svg)](https://github.com/Kotlin/kotlinx.coroutines)
[![Tests](https://img.shields.io/badge/Tests-457%20passing-brightgreen.svg)](#empirical-data)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Multiplatform](https://img.shields.io/badge/Multiplatform-JVM%20%7C%20JS%20%7C%20Native-orange.svg)](#)

> **Zero-overhead declarative orchestration for Kotlin coroutines.**
> - `.ap` = parallel. `.followedBy` = barrier. That's the whole model.
> - Swap two `.ap` lines → **compiler error** (curried types enforce parameter order)
> - `zipV` runs all validators in parallel and collects **every** error
> - One dependency (`kotlinx-coroutines-core`), ~2,100 lines, all platforms

```kotlin
// 11 service calls. 5 phases. 12 lines. Zero overhead vs raw coroutines.
val checkout: CheckoutResult = Async {
    lift11(::CheckoutResult)
        .ap { fetchUser(userId) }          // ┐
        .ap { fetchCart(userId) }           // ├─ phase 1: 4 calls, parallel
        .ap { fetchPromos(userId) }         // │
        .ap { fetchInventory(userId) }      // ┘
        .followedBy { validateStock() }     // ── phase 2: barrier (waits for phase 1)
        .ap { calcShipping() }             // ┐
        .ap { calcTax() }                  // ├─ phase 3: 3 calls, parallel
        .ap { calcDiscounts() }            // ┘
        .followedBy { reservePayment() }   // ── phase 4: barrier
        .ap { generateConfirmation() }     // ┐ phase 5: 2 calls, parallel
        .ap { sendReceiptEmail() }         // ┘
}
// Verified: 130ms virtual time (vs 460ms sequential). See ConcurrencyProofTest.kt
```

**Swap any two `.ap` lines and the compiler rejects it.** Each service returns a distinct type — the curried type chain locks the order at compile time.

---

## Quick Start

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.applicative.coroutines:coroutines-applicatives:1.0.0")
}
```

```kotlin
import applicative.*

data class Dashboard(val user: String, val cart: String, val promos: String)

suspend fun main() {
    val result = Async {
        lift3(::Dashboard)
            .ap { fetchUser() }    // all three run in parallel
            .ap { fetchCart() }    // total time = max(individual times)
            .ap { fetchPromos() }  // not sum
    }
    println(result) // Dashboard(user=Alice, cart=3 items, promos=SAVE20)
}
```

Only dependency: `kotlinx-coroutines-core`. That's it.

---

## What You Get

| Problem | Solution | Code |
|---|---|---|
| 15 parallel service calls | `lift15` + `.ap` chain | 17 lines, flat |
| Multi-phase orchestration | `.followedBy` barriers | 1 line per barrier |
| 12-field parallel validation | `zipV(12 validators)` | All errors accumulated |
| Retry + exponential + jitter | `Schedule` composition | 3 lines |
| Resource safety in parallel | `bracket` / `Resource` | Guaranteed cleanup |
| First-to-succeed racing | `raceN(...)` | Losers auto-cancelled |
| Cache expensive computation | `.memoize()` | Execute once, share result |
| Retry with fallback | `.retryOrElse(schedule) { }` | No throw on exhaustion |

---

## When to Use (and When Not To)

**Use this library when:**
- 4+ concurrent operations with sequential phases
- Error accumulation across parallel validators
- The dependency graph should be visible in code shape
- You want compile-time parameter order safety

**Don't use this library when:**
- 2-3 simple parallel calls — `coroutineScope { async {} }` is enough
- Purely sequential — regular `suspend` functions
- Stream processing — use `Flow`
- Full FP ecosystem (optics, typeclasses) — use Arrow

**Target audience:** BFF layers, checkout/booking flows, dashboard aggregation, multi-service orchestration.

---

## Empirical Data

All claims backed by JMH benchmarks and deterministic virtual-time proofs. No flaky timing assertions — `runTest` + `currentTime` gives provably correct results.

### JMH Benchmarks (`./gradlew :benchmarks:jmh`)

**Simple parallel (5 calls @ 50ms each):**

| Approach | ms/op | vs Sequential |
|---|---|---|
| Sequential baseline | 267.6 | 1x |
| Raw coroutines | 53.7 | 5x |
| Arrow (`parZip`) | 53.6 | 5x |
| **This library** | **53.8** | **5x** |

**Framework overhead (trivial compute, no I/O):**

| Approach | Arity 3 | Arity 9 |
|---|---|---|
| Raw coroutines | 0.001ms | 0.001ms |
| **This library** | **0.001ms** | **0.002ms** |
| Arrow (`parZip`) | 0.008ms | 0.023ms |

> **8x less overhead** than Arrow on trivial workloads. Negligible for real I/O.

**Multi-phase checkout (9 calls, 4 phases):**

| Approach | ms/op | Flat code? |
|---|---|---|
| Sequential baseline | 441.7 | Yes |
| Raw coroutines | 193.6 | No — nested blocks per phase |
| Arrow (`parZip`) | 195.5 | No — nested `parZip` per phase |
| **This library** | **194.6** | **Yes — single flat chain** |

Same wall-clock time. Only this library keeps the code flat regardless of phase count.

**Parallel validation with error accumulation (4 validators @ 40ms):**

| Approach | ms/op | Collects all errors? | Parallel? |
|---|---|---|---|
| Sequential | 173.9 | Yes | No |
| Raw coroutines | N/A | No (structured concurrency cancels siblings) | Yes |
| Arrow (`zipOrAccumulate`) | 43.6 | Yes | Yes |
| **This library** (`zipV`) | **43.6** | **Yes** | **Yes** |

### Virtual-Time Proofs

Every concurrency property verified with `runTest` + `currentTime` — deterministic, not flaky:

| Proof | Virtual time | Sequential | Speedup |
|---|---|---|---|
| 5 parallel calls @ 50ms | **50ms** | 250ms | **5x** |
| 10 parallel calls @ 30ms | **30ms** | 300ms | **10x** |
| 14-call 5-phase BFF | **130ms** | 460ms | **3.5x** |
| `followedBy` true barrier | **110ms** | — | C waits for barrier |
| Bounded traverse (9 items, concurrency=3) | **90ms** | 270ms | **3x** |
| Mass cancellation (1 fail, 9 siblings) | — | — | All 9 cancelled |
| `timeoutRace` vs `timeout`+fallback | **50ms** vs 150ms | — | **3x faster** |

### Algebraic Laws (property-based via Kotest)

Functor, applicative, and monad laws verified with random inputs:

- **Identity:** `map id == id`, `pure id <*> v == v`
- **Composition:** `map (g . f) == map g . map f`
- **Homomorphism:** `pure f <*> pure x == pure (f x)`
- **Associativity:** `(m >>= f) >>= g == m >>= (a -> f a >>= g)`

Source: [`ApplicativeLawsTest.kt`](src/jvmTest/kotlin/applicative/ApplicativeLawsTest.kt)

**457 tests across 26 suites. All passing.**

---

## Why This Exists

The same 11-call checkout with raw coroutines:

```kotlin
// Raw coroutines: 30+ lines, 11 shuttle variables, invisible phase boundaries
val checkout = coroutineScope {
    val dUser      = async { fetchUser(userId) }
    val dCart      = async { fetchCart(userId) }
    val dPromos    = async { fetchPromos(userId) }
    val dInventory = async { fetchInventory(userId) }
    val user       = dUser.await()         // ─┐
    val cart       = dCart.await()          //  │ 4 variables just to
    val promos     = dPromos.await()        //  │ bridge async → await
    val inventory  = dInventory.await()     // ─┘

    val stock = validateStock(inventory)    // barrier — invisible without comments

    val dShipping  = async { calcShipping(cart) }
    val dTax       = async { calcTax(cart) }
    val dDiscounts = async { calcDiscounts(promos) }
    val shipping   = dShipping.await()
    val tax        = dTax.await()
    val discounts  = dDiscounts.await()

    val payment = reservePayment(user, cart)  // barrier — also invisible

    val dConfirmation = async { generateConfirmation(payment) }
    val dEmail        = async { sendReceiptEmail(user) }

    CheckoutResult(user, cart, promos, inventory, stock,
                   shipping, tax, discounts, payment,
                   dConfirmation.await(), dEmail.await())
}
```

Move one `await()` above its `async` and you silently serialize. The compiler won't say a word.

**This library reduces 30+ lines to 12, with compile-time safety and visible phase boundaries.**

---

## The Model: Two Primitives

| Combinator | Semantics | Mental model |
|---|---|---|
| `.ap { }` | Launch in parallel | "and at the same time..." |
| `.followedBy { }` | Wait, then gate | "then, once that's done..." |

That's it. `ap` = parallel. `followedBy` = barrier. The code reads top-to-bottom as the execution timeline.

Two more for advanced use:

| Combinator | Semantics | When |
|---|---|---|
| `.flatMap { }` | Barrier + pass the value | Next phase *needs* the result |
| `.thenValue { }` | Sequential fill, no barrier | Overlap for max performance |

---

## Hero Examples

### 1. 12-Field Parallel Validation — collect *every* error

```kotlin
val onboarding = Async {
    zipV(
        { valFirstName(input.firstName) },   // ┐
        { valLastName(input.lastName) },      // │
        { valEmail(input.email) },            // │ all 12 validators
        { valPhone(input.phone) },            // │ run in parallel
        { valPassword(input.password) },      // │
        { valBirthDate(input.birthDate) },    // │ errors accumulate —
        { valCountry(input.country) },        // │ you get ALL failures,
        { valCity(input.city) },              // │ not just the first
        { valZipCode(input.zipCode) },        // │
        { valAddress(input.address) },        // │
        { valTaxId(input.taxId) },            // │
        { valTerms(input.acceptedTerms) },    // ┘
    ) { fn, ln, em, ph, pw, bd, co, ci, zc, ad, tx, tm ->
        UserOnboarding(fn, ln, em, ph, pw, bd, co, ci, zc, ad, tx, tm)
    }
}
// 7 of 12 fail → Left(Nel(FirstName(...), Email(...), ..., Terms(...)))
// Scales to 22 fields with full type inference
```

Raw coroutines **cannot** do parallel error accumulation — structured concurrency cancels siblings on first failure.

### 2. Phased Validation with Short-Circuit

Phase 1 validates in parallel. Phase 2 only runs if phase 1 passes:

```kotlin
val result = Async {
    zipV(
        { valFirstName(input.firstName) },
        { valLastName(input.lastName) },
        { valEmail(input.email) },
    ) { fn, ln, em -> IdentityInfo(fn, ln, em) }
    .flatMapV { identity ->
        zipV(
            { checkUsernameAvailable(identity.email) },
            { checkNotBlacklisted(identity) },
        ) { username, cleared -> Registration(identity, username, cleared) }
    }
}
// Phase 1 fails → errors returned, phase 2 never runs (saves network calls)
```

### 3. Value-Dependent Phases with `flatMap`

When a later phase *needs* the result of an earlier phase:

```kotlin
val dashboard = Async {
    lift4(::UserContext)
        .ap { fetchProfile(userId) }       // ┐ phase 1: parallel
        .ap { fetchPreferences(userId) }   // │
        .ap { fetchLoyaltyTier(userId) }   // │
        .ap { fetchRecentOrders(userId) }  // ┘
    .flatMap { ctx ->                      // ── barrier: phase 2 NEEDS ctx
        lift3(::PersonalizedDashboard)
            .ap { fetchRecommendations(ctx.profile) }   // ┐ phase 2: parallel
            .ap { fetchPromotions(ctx.loyalty) }         // │
            .ap { fetchTrending(ctx.preferences) }       // ┘
    }
}
```

### 4. Production Resilience — compose everything in parallel branches

```kotlin
val result = Async {
    lift4(::CheckoutResult)
        .ap { fetchUser() }
        .ap { flakyService().retry(3, delay = 10.milliseconds) }
        .ap { slowService().timeout(40.milliseconds, "cached") }
        .ap { race(primaryAPI(), cacheAPI()) }
}
// All 4 branches run in parallel. Each handles its own resilience.
// Total wall time = max(branch times). Verified: 40ms virtual time.
```

Stack them for defense in depth:

```kotlin
Computation { fetchUser() }
    .timeout(500.milliseconds)
    .retry(Schedule.recurs<Throwable>(3) and Schedule.exponential(100.milliseconds))
    .recover { UserProfile.cached() }
    .traced("fetchUser", tracer)
```

### 5. Composable Retry Policies with `Schedule`

Build retry policies from composable pieces:

```kotlin
val policy = Schedule.recurs<Throwable>(5) and
    Schedule.exponential(100.milliseconds, max = 10.seconds) and
    Schedule.doWhile { it is IOException }

Computation { fetchUser() }.retry(policy)

// Add jitter to prevent thundering herd:
val withJitter = Schedule.exponential<Throwable>(100.milliseconds)
    .jittered()                               // ±50% random spread
    .withMaxDuration(30.seconds)              // stop after 30s total

// Retry with fallback instead of throwing:
Computation { fetchUser() }
    .retryOrElse(policy) { err -> User.cached() }  // never throws
```

`and` = both must agree (max delay). `or` = either can continue (min delay).

### 6. Resource Safety with `bracket`

Acquire resources, use them in parallel, guarantee release even on failure:

```kotlin
val result = Async {
    lift3 { db: String, cache: String, api: String -> "$db|$cache|$api" }
        .ap(bracket(
            acquire = { openDbConnection() },
            use = { conn -> Computation { conn.query("SELECT ...") } },
            release = { conn -> conn.close() },  // runs even if siblings fail
        ))
        .ap(bracket(
            acquire = { openCacheConnection() },
            use = { conn -> Computation { conn.get("key") } },
            release = { conn -> conn.close() },
        ))
        .ap(bracket(
            acquire = { openHttpClient() },
            use = { client -> Computation { client.get("/api") } },
            release = { client -> client.close() },
        ))
}
// All 3 resources acquired and used in parallel.
// If any branch fails → ALL resources released (NonCancellable).
```

Or compose with the `Resource` monad (supports `zip` up to 8 resources):

```kotlin
val infra = Resource.zip(
    Resource({ openDb() }, { it.close() }),
    Resource({ openCache() }, { it.close() }),
    Resource({ openHttpClient() }, { it.close() }),
) { db, cache, http -> Triple(db, cache, http) }

val result = Async {
    infra.useComputation { (db, cache, http) ->
        lift3(::DashboardData)
            .ap { Computation { db.query("SELECT ...") } }
            .ap { Computation { cache.get("user:prefs") } }
            .ap { Computation { http.get("/recommendations") } }
    }
}
// All released in reverse order, even on failure. NonCancellable.
```

### 7. Racing: First to Succeed Wins

```kotlin
val fastest = Async {
    raceN(
        Computation { fetchFromRegionUS() },   // slow (200ms)
        Computation { fetchFromRegionEU() },   // fast (50ms)
        Computation { fetchFromRegionAP() },   // medium (100ms)
    )
}
// Returns EU response at 50ms. US and AP cancelled. All fail → throws with suppressed.
```

---

## Type Safety via Currying

With raw coroutines, you pass 15 variables to a constructor by position. Hope you got the order right.

With this library, the curried type chain enforces it:

```kotlin
Async {
    lift15(::DashboardPage)
        .ap { fetchProfile(userId) }       // returns UserProfile     — slot 1
        .ap { fetchPreferences(userId) }   // returns UserPreferences — slot 2

    // Swap these two lines?
    //  .ap { fetchLoyaltyTier(userId) }   // returns LoyaltyTier — expected UserPreferences
    //  .ap { fetchPreferences(userId) }   // COMPILE ERROR
}
```

For parameters of the same type, use value classes:

```kotlin
@JvmInline value class ValidName(val value: String)
@JvmInline value class ValidEmail(val value: String)
// Now swap-safe — compiler enforces the order
```

---

## Execution Model

`Computation` is a **description**, not an execution. Nothing runs until `Async {}`:

```kotlin
val graph = lift3(::build)
    .ap { fetchUser() }    // NOT executed yet
    .ap { fetchCart() }    // NOT executed yet

val result = Async { graph }  // NOW everything runs
```

### `followedBy` vs `thenValue` vs `flatMap`

| Scenario | Use | Subsequent `ap` behavior |
|---|---|---|
| Independent, no values needed | `followedBy` | Gated — waits for barrier |
| Needs the value | `flatMap` | Gated — waits, passes value |
| Should overlap for performance | `thenValue` | Ungated — launches at t=0 |

```
// followedBy: true barrier
lift4(::R) .ap{A} .ap{B} .followedBy{C} .ap{D}
// A,B launch at t=0. C starts at t=30. D starts at t=80. Total: 110ms

// thenValue: no barrier
lift4(::R) .ap{A} .ap{B} .thenValue{C} .ap{D}
// A,B,D all launch at t=0. C sequential. Total: 80ms
```

---

## Comparison: Raw Coroutines vs Arrow vs This Library

| | Raw Coroutines | Arrow | This Library |
|---|---|---|---|
| **15 parallel calls** | 30+ lines, shuttle vars | `parZip` supports up to 9 | `lift15` + 15 `.ap` — flat |
| **Multi-phase** | Manual, phases invisible | Nested `parZip` blocks | `followedBy` — visible |
| **Value dependencies** | Manual sequencing | Sequential blocks | `flatMap` |
| **Error accumulation** | Not possible in parallel | `zipOrAccumulate` (up to 9) | `zipV` up to 22 |
| **Arg order safety** | None — positional | Named args in lambda | Compile-time via currying |
| **Code size** | stdlib | ~15k lines (Core) | **~2,100 lines** |
| **Dependencies** | stdlib | Arrow Core + modules | `kotlinx-coroutines-core` only |
| **JMH overhead** | 0.001ms | 0.008–0.023ms | **0.001–0.002ms** |

> All three approaches tested side-by-side: [`ThreeWayComparisonTest.kt`](src/jvmTest/kotlin/applicative/ThreeWayComparisonTest.kt)
> JMH benchmarks: [`OrchestrationBenchmark.kt`](benchmarks/src/jmh/kotlin/applicative/benchmarks/OrchestrationBenchmark.kt)

---

## Observability

```kotlin
val tracer = ComputationTracer { event ->
    when (event) {
        is TraceEvent.Started -> logger.info("${event.name} started")
        is TraceEvent.Succeeded -> metrics.timer(event.name).record(event.duration)
        is TraceEvent.Failed -> logger.error("${event.name} failed", event.error)
    }
}

val result = Async {
    lift3(::Dashboard)
        .ap { fetchUser().traced("user", tracer) }
        .ap { fetchConfig().traced("config", tracer) }
        .ap { fetchCart().traced("cart", tracer) }
}
```

No logging framework coupled. Bring your own.

---

## API Reference

### Core & Collections

| Combinator | Semantics | Parallelism |
|---|---|---|
| `lift2`..`lift22` + `ap` | N-way fan-out | Parallel |
| `followedBy` | True phase barrier | Sequential (gates) |
| `thenValue` | Sequential value fill, no barrier | Sequential (no gate) |
| `flatMap` | Monadic bind (value-dependent) | Sequential |
| `map` / `pure` / `unit` | Transform / wrap value | — |
| `zip` (2-5 arity) / `mapN` | Combine computations | Parallel |
| `traverse(n?)` / `sequence(n?)` / `parMap(n?)` | Collection operations | Parallel (bounded) |
| `race` / `raceN` / `raceAll` | First to succeed wins | Competitive |
| `memoize` | Cache computation result | — |
| `on` / `named` / `context` | Dispatcher, name, context | — |

### Error Handling, Retry & Schedule

| Combinator | Semantics |
|---|---|
| `recover` / `recoverWith` / `fallback` | Catch and recover |
| `timeout(duration)` / `timeout(d, default)` / `timeout(d, comp)` | Time-bounded |
| `timeoutRace(duration, fallback)` | Parallel timeout (fallback starts immediately) |
| `retry(n, delay, backoff, shouldRetry, onRetry)` | Configurable retry |
| `retry(schedule)` / `retryOrElse(schedule, fallback)` | Schedule-based retry |
| `Schedule.recurs` / `.spaced` / `.exponential` / `.fibonacci` | Backoff strategies |
| `Schedule.doWhile` / `.jittered` / `.withMaxDuration` | Filters and limits |
| `s1 and s2` / `s1 or s2` | Schedule composition |

### Validation (Error Accumulation)

| Combinator | Semantics |
|---|---|
| `zipV` (2-22 args) | Parallel validation, all errors accumulated |
| `liftV2`..`liftV22` + `apV` | Curried parallel validation |
| `followedByV` / `flatMapV` | Phase barriers / sequential short-circuit |
| `valid` / `invalid` / `catching` / `validate` | Entry points |
| `recoverV` / `mapV` / `mapError` / `orThrow` | Transforms |
| `traverseV` / `sequenceV` | Collection operations with accumulation |

### Resource Safety

| Combinator | Semantics |
|---|---|
| `bracket(acquire, use, release)` | Guaranteed cleanup (NonCancellable) |
| `guarantee` / `guaranteeCase` | Finalizers with optional `ExitCase` |
| `Resource(acquire, release)` | Composable resource monad |
| `Resource.zip(r1..r8, f)` | Combine up to 8 resources |
| `resource.use` / `resource.useComputation` | Terminal operations |

### Interop & Observability

| Combinator | Semantics |
|---|---|
| `Deferred.toComputation()` / `Computation.toDeferred(scope)` | Deferred bridge |
| `Flow.firstAsComputation()` / `(suspend () -> A).toComputation()` | Flow/lambda bridge |
| `Result.toEither()` / `Either.toResult()` / `Result.toValidated()` | Kotlin Result bridge |
| `traced(name, tracer)` / `traced(name, onStart, onSuccess, onError)` | Observability hooks |
| `delayed(duration, value)` / `catching { }` / `apOrNull` | Utilities |

---

## Examples

Full working examples in [`/examples`](examples/): [ecommerce-checkout](examples/ecommerce-checkout/) (11-service, 5-phase), [dashboard-aggregator](examples/dashboard-aggregator/) (14-service BFF), [validated-registration](examples/validated-registration/) (12-field validation).

Arrow interop module: [`/arrow-interop`](arrow-interop/) — optional bridges for `Either`, `Nel`, `parZip`.

---

## Building

```bash
./gradlew jvmTest              # 457 tests
./gradlew :arrow-interop:test  # Arrow interop tests
./gradlew :benchmarks:jmh      # JMH benchmarks
./gradlew dokkaHtml             # API docs
./gradlew generateAll           # regenerate curry/lift/validated overloads (arities 2-22)
```

See [PUBLISHING.md](PUBLISHING.md) for Maven Central publishing instructions.

## License

Apache 2.0
