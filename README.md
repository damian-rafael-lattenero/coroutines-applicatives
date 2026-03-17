# Coroutines Applicatives

**Your code shape *is* the execution plan.**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![Coroutines](https://img.shields.io/badge/Coroutines-1.9.0-blue.svg)](https://github.com/Kotlin/kotlinx.coroutines)
[![Tests](https://img.shields.io/badge/Tests-790%20passing-brightgreen.svg)](#empirical-data)

[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Multiplatform](https://img.shields.io/badge/Multiplatform-JVM%20%7C%20JS%20%7C%20Native-orange.svg)](#)
[![Lines](https://img.shields.io/badge/Hand--written-2.5k%20lines-informational.svg)](#)

> **Declarative parallel orchestration for Kotlin coroutines. Zero overhead.**
>
> Write your dependency graph as a flat chain — the library turns it into an optimal execution plan automatically. Swap two lines and the **compiler rejects it**.
>
> **Why not just `coroutineScope { async {} }`?** Because with 11 service calls across 5 phases, raw coroutines give you 30+ lines of shuttle variables with invisible phase boundaries. This library gives you 12 lines where the code shape *is* the execution plan — and the compiler enforces parameter order.

```kotlin
// 11 service calls. 5 phases. One flat chain. Zero overhead vs raw coroutines.
val checkout: CheckoutResult = Async {
    lift11(::CheckoutResult)
        .ap { fetchUser(userId) }          // ┐
        .ap { fetchCart(userId) }           // ├─ phase 1: parallel
        .ap { fetchPromos(userId) }        // │
        .ap { fetchInventory(userId) }     // ┘
        .followedBy { validateStock() }    // ── phase 2: barrier
        .ap { calcShipping() }            // ┐
        .ap { calcTax() }                 // ├─ phase 3: parallel
        .ap { calcDiscounts() }           // ┘
        .followedBy { reservePayment() }  // ── phase 4: barrier
        .ap { generateConfirmation() }    // ┐ phase 5: parallel
        .ap { sendReceiptEmail() }        // ┘
}
```

**Swap any two `.ap` lines → compiler error.** Each service returns a distinct type — the curried chain locks parameter order at compile time.

**130ms virtual time** (vs 460ms sequential) — verified in [`ConcurrencyProofTest.kt`](src/jvmTest/kotlin/applicative/ConcurrencyProofTest.kt).

---

## 30-Second Mental Model

The entire library is **two primitives**:

| Primitive | What it does | Think of it as |
|---|---|---|
| `.ap { }` | Launch in parallel | "and at the same time..." |
| `.followedBy { }` | Wait for everything above, then continue | "then, once that's done..." |

That's it. `.ap` = parallel. `.followedBy` = barrier. Your code reads top-to-bottom as the execution timeline.

```kotlin
// This:
lift4(::Page)
    .ap { fetchA() }             // ┐ parallel
    .ap { fetchB() }             // ┘
    .followedBy { validate() }   // waits for A and B
    .ap { fetchC() }             // starts after validate

// Means exactly this execution:
//  t=0   ──── fetchA() ────┐
//  t=0   ──── fetchB() ────┤
//             validate() ──┘──── fetchC() ───→ Page
```

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
            .ap { fetchUser() }    // ┐ all three in parallel
            .ap { fetchCart() }    // │ total time = max(individual)
            .ap { fetchPromos() }  // ┘ not sum
    }
    println(result) // Dashboard(user=Alice, cart=3 items, promos=SAVE20)
}
```

**One dependency:** `kotlinx-coroutines-core`. ~2,500 hand-written lines + codegen. All platforms (JVM, JS, Native).

> **Tip:** Use `Nel<E>` as a shorthand for `NonEmptyList<E>` in validated signatures — it's a built-in typealias.

### Choose Your Style

Three ways to compose parallel operations — pick what fits:

```kotlin
// Style 1: lift + ap — compile-time parameter order safety via curried types (recommended for 6+ args)
lift3(::Dashboard)
    .ap { fetchUser() }
    .ap { fetchCart() }
    .ap { fetchPromos() }

// Style 2: liftA — Haskell-named, parZip-like ergonomics (great for 2-5 args)
liftA3(
    { fetchUser() },
    { fetchCart() },
    { fetchPromos() },
) { user, cart, promos -> Dashboard(user, cart, promos) }

// Style 3: mapN / zip — takes pre-built Computations (familiar to Arrow users)
mapN(
    Computation { fetchUser() },
    Computation { fetchCart() },
    Computation { fetchPromos() },
) { user, cart, promos -> Dashboard(user, cart, promos) }

// Bonus: product — returns Pair/Triple (Haskell: (,) <$> fa <*> fb)
val (user, cart) = Async { product({ fetchUser() }, { fetchCart() }) }
```

All styles are parallel by default. `lift+ap` gives stronger type safety; `liftA`/`mapN` give simpler syntax.

---

## Before / After

### Scenario: 5-Phase Checkout (11 service calls)

**Before — Raw Coroutines (30+ lines, invisible phases):**

```kotlin
val checkout = coroutineScope {
    val dUser      = async { fetchUser(userId) }
    val dCart       = async { fetchCart(userId) }
    val dPromos    = async { fetchPromos(userId) }
    val dInventory = async { fetchInventory(userId) }
    val user       = dUser.await()         // ─┐
    val cart        = dCart.await()          //  │ 4 shuttle variables
    val promos     = dPromos.await()        //  │ just to bridge
    val inventory  = dInventory.await()     // ─┘ async → await

    val stock = validateStock(inventory)    // barrier — invisible

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
// Move one await() above its async → silently serialized. Compiler won't warn.
```

**After — This Library (12 lines, visible phases):**

```kotlin
val checkout = Async {
    lift11(::CheckoutResult)
        .ap { fetchUser(userId) }          // ┐ phase 1
        .ap { fetchCart(userId) }           // │
        .ap { fetchPromos(userId) }        // │
        .ap { fetchInventory(userId) }     // ┘
        .followedBy { validateStock() }    // barrier
        .ap { calcShipping() }            // ┐ phase 3
        .ap { calcTax() }                 // │
        .ap { calcDiscounts() }           // ┘
        .followedBy { reservePayment() }  // barrier
        .ap { generateConfirmation() }    // ┐ phase 5
        .ap { sendReceiptEmail() }        // ┘
}
// Same wall-clock time. Phases visible. Swap = compiler error.
```

### Scenario: Parallel Validation with Error Accumulation

**Before — Raw Coroutines (impractical without manual error collection):**

```kotlin
// Structured concurrency cancels siblings on first failure.
// You'd need supervisorScope + manual Result wrapping to collect all errors.
coroutineScope {
    val a = async { validateName(input) }    // if this throws...
    val b = async { validateEmail(input) }   // ...this gets cancelled
    // You never see email's error.
}
```

**After — This Library:**

```kotlin
val result = Async {
    zipV(
        { validateName(input) },
        { validateEmail(input) },
    ) { name, email -> UserData(name, email) }
}
// Both errors collected. Left(NonEmptyList(NameError, EmailError))
```

---

## Hero Examples

### 1. Parallel Validation — Collect *Every* Error

Raw coroutines **cannot** do parallel error accumulation — structured concurrency cancels siblings on first failure. This library solves that:

```kotlin
val result = Async {
    zipV(
        { validateName(input.name) },        // ┐
        { validateEmail(input.email) },      // │ all 12 validators
        { validatePhone(input.phone) },      // │ run in parallel
        { validatePassword(input.password) },// │
        { validateBirth(input.birthDate) },  // │ errors accumulate —
        { validateCountry(input.country) },  // │ you get ALL failures,
        { validateCity(input.city) },        // │ not just the first
        { validateZip(input.zipCode) },      // │
        { validateAddress(input.address) },  // │
        { validateTaxId(input.taxId) },      // │
        { validateTerms(input.terms) },      // │
        { validateCaptcha(input.captcha) },  // ┘
    ) { name, email, phone, pass, birth, country, city, zip, addr, tax, terms, captcha ->
        UserRegistration(name, email, phone, pass, birth, country, city, zip, addr, tax, terms, captcha)
    }
}
// 7 of 12 fail? → Left(NonEmptyList(NameTooShort, InvalidEmail, ..., CaptchaExpired))
// All pass?     → Right(UserRegistration(...))
// Scales to 22 validators. Full type inference.
```

**43.6 ms** (vs 173.9ms sequential) — JMH verified.

### 2. Production Resilience Stack

Each parallel branch handles its own resilience — all run concurrently:

```kotlin
val result = Async {
    lift4(::CheckoutResult)
        .ap { fetchUser() }
        .ap { flakyService().retry(3, delay = 10.milliseconds) }
        .ap { slowService().timeout(40.milliseconds, default = "cached") }
        .ap { race(primaryAPI(), cacheAPI()) }
}
// Total wall time = max(branch times). Verified: 40ms virtual time.
```

Stack combinators for defense in depth:

```kotlin
val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

Computation { fetchUser() }
    .timeout(500.milliseconds)
    .withCircuitBreaker(breaker)
    .retry(Schedule.recurs<Throwable>(3) and Schedule.exponential(100.milliseconds))
    .recover { UserProfile.cached() }
    .traced("fetchUser", tracer)
// timeout → circuit breaker → retry → recover → trace. All composable.
```

Short-circuit guards inside computation chains:

```kotlin
Computation { fetchUser(id) }
    .ensure({ InactiveUserException(id) }) { it.isActive }         // throws if inactive
    .ensureNotNull({ ProfileMissing(id) }) { it.profile }          // throws if null
    .flatMap { profile -> loadDashboard(profile) }
```

### 3. Resource Safety with `bracket` and `Resource`

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
// Any branch fails → ALL resources released (NonCancellable context).
```

Use `bracketCase` when release behavior depends on the outcome:

```kotlin
bracketCase(
    acquire = { openTransaction() },
    use = { tx -> Computation { tx.execute("INSERT ...") } },
    release = { tx, case ->
        when (case) {
            is ExitCase.Completed -> tx.commit()
            else -> tx.rollback()  // Failed or Cancelled
        }
        tx.close()
    },
)
```

Or compose with the `Resource` monad (supports `zip` up to 22 resources):

```kotlin
val infra = Resource.zip(
    Resource({ openDb() }, { it.close() }),
    Resource({ openCache() }, { it.close() }),
    Resource({ openHttp() }, { it.close() }),
) { db, cache, http -> Triple(db, cache, http) }

val result = Async {
    infra.useComputation { (db, cache, http) ->
        lift3(::DashboardData)
            .ap { db.query("SELECT ...") }
            .ap { cache.get("user:prefs") }
            .ap { http.get("/recommendations") }
    }
}
// Released in reverse order, even on failure. NonCancellable.
```

### 4. Racing: First to Succeed Wins

```kotlin
val fastest = Async {
    raceN(
        Computation { fetchFromRegionUS() },   // slow (200ms)
        Computation { fetchFromRegionEU() },   // fast (50ms)
        Computation { fetchFromRegionAP() },   // medium (100ms)
    )
}
// Returns EU response at 50ms. US and AP cancelled immediately.
// All fail → throws last error with others as suppressed exceptions.
```

Chain racing as an extension method:

```kotlin
val result = Async {
    Computation { fetchFromPrimary() }
        .raceAgainst(Computation { fetchFromReplica() })
}
```

Race with **different types** using `raceEither`:

```kotlin
val result: Either<CachedUser, FreshUser> = Async {
    raceEither(
        fa = Computation { fetchFromCache(userId) },     // fast but stale
        fb = Computation { fetchFromDatabase(userId) },  // slow but fresh
    )
}
// Left(cachedUser) if cache wins, Right(freshUser) if DB wins.
```

### 5. Value-Dependent Phases with `flatMap`

When a later phase *needs* the result of an earlier one:

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
            .ap { fetchPromotions(ctx.loyalty) }         // │ uses data from phase 1
            .ap { fetchTrending(ctx.preferences) }       // ┘
    }
}
```

<details>
<summary><b>More Examples</b> — Schedule, traverse, attempt, phased validation</summary>

#### Composable Retry Policies with `Schedule`

Build retry policies from composable, reusable pieces:

```kotlin
// Exponential backoff, max 5 retries, only on IOExceptions
val policy = Schedule.recurs<Throwable>(5) and
    Schedule.exponential(100.milliseconds, max = 10.seconds) and
    Schedule.doWhile { it is IOException }

Computation { fetchUser() }.retry(policy)

// Add jitter to prevent thundering herd:
val withJitter = Schedule.exponential<Throwable>(100.milliseconds)
    .jittered()                               // ±50% random spread
    .withMaxDuration(30.seconds)              // hard time limit

// Fibonacci backoff: base, base, 2*base, 3*base, 5*base, 8*base...
val fibonacci = Schedule.fibonacci<Throwable>(100.milliseconds)

// Linear backoff: 100ms, 200ms, 300ms, 400ms...
val linear = Schedule.linear<Throwable>(100.milliseconds)

// Infinite retries with backoff (common for consumer loops):
val forever = Schedule.forever<Throwable>()
    .and(Schedule.exponential(100.milliseconds, max = 30.seconds))

// Retry with fallback — never throws:
Computation { fetchUser() }
    .retryOrElse(policy) { err -> User.cached() }
```

`and` = both must agree to continue (max delay). `or` = either can continue (min delay).

#### Bounded Parallel Collection Processing

```kotlin
val users = Async {
    userIds.traverse(concurrency = 5) { id ->
        Computation { fetchUser(id) }
    }
}
// Processes all userIds, max 5 in-flight at once.
// 9 items @ 30ms each, concurrency=3 → 90ms (not 270ms). Virtual-time verified.
```

#### `attempt` — Catch to Either Without Breaking the Chain

```kotlin
val result: Either<Throwable, User> = Async {
    Computation { fetchUser() }.attempt()
}
// Right(User(...)) on success, Left(exception) on failure
// CancellationException is NEVER caught — structured concurrency always propagates
```

#### Phased Validation with Short-Circuit

Phase 1 runs validators in parallel. Phase 2 only executes if phase 1 passes:

```kotlin
val result = Async {
    zipV(
        { validateName(input.name) },
        { validateEmail(input.email) },
        { validateAge(input.age) },
    ) { name, email, age -> IdentityInfo(name, email, age) }
    .flatMapV { identity ->
        zipV(
            { checkUsernameAvailable(identity.email) },
            { checkNotBlacklisted(identity) },
        ) { username, cleared -> Registration(identity, username, cleared) }
    }
}
// Phase 1 fails → errors returned immediately, phase 2 never runs (saves network calls)
```

#### Imperative Sequential with `computation { }`

When later steps depend on earlier results, use `computation` for imperative monadic syntax:

```kotlin
val result = Async {
    computation {
        val user = bind { fetchUser(userId) }
        val cart = bind { fetchCart(user.cartId) }           // needs user
        val recs = bind { fetchRecommendations(user.prefs) } // needs user
        Dashboard(user, cart, recs)
    }
}
// Sequential execution with value dependencies — sugar over flatMap chains.
// For parallel branches, use lift+ap instead.
```

#### Short-Circuit Validated Builder

Combine parallel validation with sequential phases using `accumulate` (or `validated`) + `bindV`:

```kotlin
val result = Async {
    accumulate { // parallel within phases, short-circuits between phases
        val identity = zipV(
            { validateName(input.name) },
            { validateEmail(input.email) },
            { validateAge(input.age) },
        ) { name, email, age -> Identity(name, email, age) }
            .bindV() // executes + unwraps Right, short-circuits on Left

        val cleared = zipV(
            { checkNotBlacklisted(identity) },
            { checkUsernameAvailable(identity.email) },
        ) { a, b -> Clearance(a, b) }
            .bindV()

        Registration(identity, cleared)
    }
}
// Phase 1 (parallel) → bindV → Phase 2 (parallel) → bindV → result
// Any phase Left? → short-circuit, skip remaining phases
// No nested Async {} needed — bindV executes the Computation directly
```

</details>

---

## What Problems Does This Solve?

| Problem | Without this library | With this library |
|---|---|---|
| 15 parallel calls | 30+ lines, shuttle variables, invisible phases | `lift15` + `.ap` chain — flat, readable |
| Multi-phase orchestration | Nested `coroutineScope`/`async` blocks | `.followedBy` — one line per barrier |
| Parameter order bugs | Silent runtime errors (wrong order compiles fine) | Compiler error on swap |
| 12-field parallel validation | Structured concurrency cancels siblings on first error | `zipV` collects **every** error |
| Retry + backoff + jitter | 20+ lines of boilerplate | `Schedule` composition — 3 lines |
| Resource cleanup in parallel | Manual try/finally spaghetti | `bracket` / `Resource` — guaranteed |
| First-to-succeed racing | Complex `select` clauses | `raceN(...)` — losers auto-cancelled |
| Transaction commit/rollback | Manual ExitCase tracking | `bracketCase` — automatic |
| Cascading failures to downstream | Manual circuit breaker impl | `CircuitBreaker` — 3 states, composable |
| Null/predicate guards in chains | `if` checks + early returns | `.ensure` / `.ensureNotNull` — declarative |
| Transient-failure caching | `memoize()` caches errors forever | `.memoizeOnSuccess()` — retries on failure |

---

## How It Works Under the Hood

**Step 1: Currying.** `lift11(::CheckoutResult)` takes your 11-parameter constructor and curries it into a chain of single-argument functions: `(A) -> (B) -> ... -> (K) -> CheckoutResult`. This is wrapped in a `Computation` — a lazy description that hasn't executed yet.

**Step 2: Parallel fork.** Each `.ap { fetchX() }` takes the next curried function and applies one argument. The right-hand side (your lambda) launches as `async` in the current `CoroutineScope`, while the left spine stays inline. This means N `.ap` calls produce N concurrent coroutines with O(N) stack depth — not O(N^2).

**Step 3: Barrier join.** `.followedBy { }` awaits all pending parallel work before continuing. It acts as a phase boundary: everything above the barrier runs in parallel, everything below waits. `.flatMap` does the same but passes the result value into the next phase.

The key insight is that **applicative composition is statically analyzable** — the library knows at build time that `.ap` branches are independent and can run concurrently. Monadic composition (`flatMap`) forces sequencing because later steps depend on earlier values. Your code shape directly encodes the dependency graph.

---

## Cancellation Safety

`CancellationException` is **never** caught by any combinator in this library. This is a deliberate design choice to respect Kotlin's structured concurrency:

- `recover`, `attempt`, `retry`, `recoverV` — all re-throw `CancellationException`
- `bracket` / `bracketCase` / `guarantee` — release/finalizer always runs in `NonCancellable` context
- `race` / `raceN` — losers are cancelled via structured concurrency, winner propagates normally
- When any `.ap` branch fails, all sibling branches are cancelled (standard `coroutineScope` behavior)

This means you can safely nest this library inside any coroutine hierarchy without breaking cancellation propagation.

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
        // .ap { fetchLoyaltyTier(userId) }  // returns LoyaltyTier — COMPILE ERROR
        // .ap { fetchPreferences(userId) }  // expected LoyaltyTier, got UserPreferences
}
```

For same-type parameters, use value classes for swap-safety:

```kotlin
@JvmInline value class ValidName(val value: String)
@JvmInline value class ValidEmail(val value: String)
// Now compiler enforces the order even for "string-like" types
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

| Scenario | Use | Subsequent `.ap` behavior |
|---|---|---|
| Independent phases | `followedBy` | Gated — waits for barrier |
| Next phase needs the value | `flatMap` | Gated — waits, passes value |
| Maximum overlap | `thenValue` | Ungated — launches immediately |

```
// followedBy: true barrier
lift4(::R) .ap{A} .ap{B} .followedBy{C} .ap{D}
// A,B at t=0. C starts when A,B done. D starts when C done. Total: 110ms

// thenValue: no barrier (D overlaps with C)
lift4(::R) .ap{A} .ap{B} .thenValue{C} .ap{D}
// A,B,D all at t=0. C sequential. Total: 80ms
```

---

## Empirical Data

All claims backed by **28 JMH benchmarks** (2 forks × 5 measurement iterations each) and deterministic virtual-time proofs. No flaky timing assertions — `runTest` + `currentTime` gives provably correct results.

> **Environment:** JDK 21.0.9 (Amazon Corretto), OpenJDK 64-Bit Server VM, macOS. JMH 1.36 with Compiler Blackholes.

### JMH Benchmarks (`./gradlew :benchmarks:jmh`)

#### 1. Simple Parallel — 5 calls @ 50ms each

The simplest test: five independent network calls that each take 50ms. Sequential execution takes ~268ms. Parallel should compress to ~50ms. Does the library add overhead?

| Approach | ms/op | vs Sequential | Overhead vs raw |
|---|---|---|---|
| Sequential baseline | 267.7 | 1x | — |
| Raw coroutines (`async/await`) | 53.9 | **5.0x** | — |
| Arrow (`parZip`) | 54.0 | **5.0x** | +0.1ms |
| **This library** (`lift+ap`) | **53.7** | **5.0x** | **−0.2ms** |
| **This library** (`liftA5`) | **53.6** | **5.0x** | **−0.3ms** |

> **Verdict:** All three parallel approaches deliver the theoretical 5x speedup. The library matches raw coroutines to within the margin of error. The `liftA5` style (Haskell-named, parZip-like) performs identically to `lift+ap` — choose whichever reads better for your use case.

#### 2. Framework Overhead — trivial compute, no I/O

Isolates pure framework cost by running trivial `compute()` functions with no delay. This measures only the overhead of creating coroutines, currying, and combining results.

| Approach | Arity 3 | Arity 5 | Arity 9 | Arity 15 |
|---|---|---|---|---|
| Raw coroutines | 0.001ms | — | 0.002ms | — |
| **This library** (`lift+ap`) | **0.001ms** | — | **0.002ms** | **0.003ms** |
| **This library** (`liftA`) | **0.001ms** | **0.001ms** | — | — |
| Arrow (`parZip`) | 0.008ms | — | 0.021ms | — |

> **Verdict:** The library's overhead is **indistinguishable from raw coroutines** — both measure ~1μs at arity 3. Arrow's `parZip` is 8–10x higher in pure overhead, though still negligible for real I/O workloads. At arity 15, the curried chain adds only 3μs total — invisible when your network calls take 50–500ms. The `liftA` style matches `lift+ap` exactly: no overhead penalty for the ergonomic syntax.

#### 3. Multi-Phase Checkout — 9 calls, 4 phases

A realistic BFF scenario: Phase 1 fans out 4 calls in parallel, a validation barrier, Phase 3 fans out 3 more calls, then a payment barrier. This tests whether the library's phase barrier mechanism (`followedBy`) adds latency compared to manual `coroutineScope` nesting.

| Approach | ms/op | Flat code? | vs Sequential |
|---|---|---|---|
| Sequential baseline | 442.9 | Yes | 1x |
| Raw coroutines (nested blocks) | 195.0 | No — 4 async/await groups | **2.3x** |
| Arrow (nested `parZip`) | 193.9 | No — 4 nested `parZip` blocks | **2.3x** |
| **This library** (`lift+ap+followedBy`) | **195.1** | **Yes — single flat chain** | **2.3x** |

> **Verdict:** Identical wall-clock time across all three parallel approaches (~195ms vs 443ms sequential). The crucial difference: **raw coroutines and Arrow require nested blocks per phase** — the code shape doesn't reveal the execution plan. This library keeps a single flat chain where `.followedBy` visually marks each barrier. Same performance, radically better readability.

#### 4. Parallel Validation — 4 validators @ 40ms each

Four validators run in parallel with error accumulation. Raw coroutines **cannot** do this — structured concurrency cancels siblings on first failure. This is one of the library's key differentiators.

| Approach | ms/op | Collects all errors? | Parallel? | vs Sequential |
|---|---|---|---|---|
| Sequential | 174.6 | Yes | No | 1x |
| Raw coroutines | N/A | **No** (cancels siblings) | Yes | — |
| Arrow (`zipOrAccumulate`) | 43.8 | Yes | Yes | **4.0x** |
| **This library** (`zipV`) | **44.2** | **Yes** | **Yes** | **3.9x** |

> **Verdict:** Both this library and Arrow deliver ~4x speedup over sequential validation while collecting every error. The 0.4ms difference (44.2 vs 43.8) is within the margin of error (±0.9ms). Raw coroutines cannot even compete here — they'd need `supervisorScope` + manual `Result` wrapping to avoid cancellation, adding significant complexity. This library and Arrow both solve it elegantly, but this library scales to **22 validators** (vs Arrow's 9-arg limit on `zipOrAccumulate`).

#### 5. Resilience Stack — retry, timeout, race

These benchmarks measure the real-world overhead of composable resilience combinators when things go right (no failures to handle).

| Benchmark | What it tests | ms/op | Analysis |
|---|---|---|---|
| `retry_succeed_first` | `retry(schedule)` on a 30ms call that succeeds immediately | 33.7 | **3.7ms overhead** over the 30ms call — the Schedule evaluation, CancellationException checks, and retry loop cost almost nothing when no retry is needed |
| `timeout_with_default` | `timeout(100ms, default)` on a 200ms call | 103.6 | Fires at exactly **100ms** as expected. The 3.6ms margin is coroutine scheduling jitter — the timeout mechanism itself is precise |
| `race_two` | `race(primary@100ms, replica@50ms)` | 54.0 | Winner at **~50ms**, loser cancelled. Racing adds ~4ms overhead over the 50ms theoretical minimum — competitive with Arrow's race |

> **Verdict:** The resilience combinators add negligible overhead in the happy path. `retry` costs ~3.7ms when no retry is needed. `timeout` fires within 3.6ms of the deadline. `race` returns the winner within 4ms of its completion. These are production-ready numbers — the resilience mechanisms are essentially free until they're needed.

#### 6. Collection Processing — traverse with bounded concurrency

Processing 20 items that each take 30ms. Unbounded launches all 20 in parallel; bounded limits to 5 concurrent.

| Benchmark | Concurrency | ms/op | Theoretical | Overhead |
|---|---|---|---|---|
| `traverse_unbounded_20` | ∞ (all 20) | 33.5 | 30ms | **+3.5ms** |
| `traverse_bounded_20_concurrency5` | 5 | 134.5 | 120ms (4 batches × 30ms) | **+14.5ms** |

> **Verdict:** Unbounded traverse achieves near-theoretical performance — 20 parallel items at 30ms each complete in 33.5ms (just 3.5ms over the ideal 30ms). Bounded concurrency with `Semaphore` adds ~14.5ms overhead for 4 batch transitions — roughly 3.6ms per batch switch. For real-world API rate limiting (where you'd set `concurrency = 5` to avoid overwhelming a downstream service), this overhead is invisible compared to the 30ms+ per-call latency.

#### 7. Memoization — cache hit vs cold miss

Measures the overhead of thread-safe memoization using `Mutex`.

| Benchmark | ms/op | Analysis |
|---|---|---|
| `memoize_cold_miss` | ≈ 0.0001 | First execution: acquire Mutex, run computation, store result |
| `memoize_warm_hit` | ≈ 0.0001 | Cache hit: acquire Mutex, return stored result |

> **Verdict:** Both cold miss and warm hit measure at **~100 nanoseconds** — below JMH's reliable measurement threshold. The `Mutex`-based thread safety is essentially free. `memoizeOnSuccess()` uses the same mechanism but allows retries on failure — same overhead, production-safe semantics.

#### 8. High-Arity Overhead — lift15 with 15 parallel branches

Tests whether the curried function chain degrades at high arities.

| Arity | ms/op | Per-branch cost |
|---|---|---|
| 3 (`lift+ap`) | 0.001ms | ~0.3μs |
| 5 (`liftA5`) | 0.001ms | ~0.2μs |
| 9 (`lift+ap`) | 0.002ms | ~0.2μs |
| **15** (`lift+ap`) | **0.003ms** | **~0.2μs** |

> **Verdict:** The curried chain scales linearly with arity — each additional `.ap` adds roughly 0.2μs. At 15 parallel branches (a realistic BFF scenario), total framework overhead is 3μs. Even at arity 22 (the maximum), projected overhead would be ~4.4μs. For comparison, a single network call takes 50,000–500,000μs. The currying mechanism is not the bottleneck and never will be.

### Comparison Summary

| Dimension | Raw Coroutines | Arrow | This Library |
|---|---|---|---|
| **Framework overhead** (arity 3) | 0.001ms | 0.008ms | **0.001ms** |
| **Framework overhead** (arity 9) | 0.002ms | 0.021ms | **0.002ms** |
| **Simple parallel** (5 × 50ms) | 53.9ms | 54.0ms | **53.6ms** |
| **Multi-phase** (9 calls, 4 phases) | 195.0ms | 193.9ms | **195.1ms** |
| **Validation** (4 × 40ms) | N/A | 43.8ms | **44.2ms** |
| **Max arity** | ∞ (manual) | 9 (`parZip`) | **22** (`lift+ap`) |
| **Flat multi-phase code** | No | No | **Yes** |
| **Compile-time arg order safety** | No | No | **Yes** |
| **Parallel error accumulation** | No | Yes (max 9) | **Yes (max 22)** |

> The performance story is simple: **this library matches raw coroutines exactly.** The value proposition is not speed — it's safety, readability, and composability at zero cost.

### Virtual-Time Proofs

Every concurrency property verified with `runTest` + `currentTime` — deterministic, not flaky:

| Proof | Virtual time | Sequential | Speedup |
|---|---|---|---|
| 5 parallel calls @ 50ms | **50ms** | 250ms | **5x** |
| 10 parallel calls @ 30ms | **30ms** | 300ms | **10x** |
| 14-call 5-phase BFF | **130ms** | 460ms | **3.5x** |
| `followedBy` true barrier | **110ms** | — | C waits for barrier |
| Bounded traverse (9 items, concurrency=3) | **90ms** | 270ms | **3x** |
| Bounded traverse (500 items, concurrency=50) | **300ms** | 15,000ms | **50x** |
| Mass cancellation (1 fail, 9 siblings) | — | — | All 9 cancelled |
| `timeoutRace` vs `timeout`+fallback | **50ms** vs 150ms | — | **3x faster** |
| 100 parallel traverse | **50ms** | 5,000ms | **100x** |
| lift22 (22 parallel branches) | **30ms** | 660ms | **22x** |

### Algebraic Laws — Mathematically Verified

Unlike most Kotlin libraries, every algebraic law is **property-based tested** with random inputs via Kotest. This means refactoring with these combinators is provably safe — the laws guarantee substitutability.

**`Computation` satisfies Functor, Applicative, and Monad laws:**

| Law | Property | What it guarantees |
|---|---|---|
| Functor Identity | `fa.map { it } == fa` | `map` with identity is a no-op |
| Functor Composition | `fa.map(g).map(f) == fa.map { f(g(it)) }` | Fusing two maps is safe |
| Applicative Identity | `pure(id) ap fa == fa` | Lifting identity does nothing |
| Applicative Homomorphism | `pure(f) ap pure(x) == pure(f(x))` | Pure values compose purely |
| Monad Associativity | `(m >>= f) >>= g == m >>= { f(it) >>= g }` | FlatMap chains are associative |

**`Validated` (apV/zipV) satisfies Applicative laws** (but intentionally NOT Monad — error accumulation requires applicative semantics).

**`NonEmptyList` satisfies Functor and Monad laws** — identity, composition, and associativity all verified.

Source: [`ApplicativeLawsTest.kt`](src/jvmTest/kotlin/applicative/ApplicativeLawsTest.kt)

**790 tests across 44 suites. All passing.**

---

## Comparison: Raw Coroutines vs Arrow vs This Library

| | Raw Coroutines | Arrow | This Library |
|---|---|---|---|
| **N parallel calls** | Manual async/await, shuttle vars | `parZip` max 9 args | `lift2`..`lift22` + `.ap` — flat |
| **Multi-phase** | Manual, phases invisible | Nested `parZip` blocks | `.followedBy` — visible |
| **Value dependencies** | Manual sequencing | Sequential blocks | `flatMap` |
| **Error accumulation** | Not possible in parallel | `zipOrAccumulate` max 9 | `zipV` up to 22 |
| **Arg order safety** | None — positional | Named args in lambda | Compile-time via currying |
| **Code size** | stdlib | ~15k lines (Core) | **~2,500 lines** + codegen |
| **Dependencies** | stdlib | Arrow Core + modules | `kotlinx-coroutines-core` only |
| **JMH overhead** | 0.001ms | 0.008–0.023ms | **0.001–0.002ms** |

> Side-by-side comparison: [`ThreeWayComparisonTest.kt`](src/jvmTest/kotlin/applicative/ThreeWayComparisonTest.kt)
> JMH benchmarks: [`OrchestrationBenchmark.kt`](benchmarks/src/jmh/kotlin/applicative/benchmarks/OrchestrationBenchmark.kt)

### Coming from Arrow?

| Arrow | This Library | Notes |
|---|---|---|
| `parZip(f1, f2, ...) { a, b, ... -> }` | `liftA3(f1, f2, f3) { }` or `lift3(::R).ap{f1}.ap{f2}.ap{f3}` | liftA for 2-5, lift+ap for 6-22 |
| Nested `parZip` for phases | `.followedBy { }` | Visible barriers |
| `zipOrAccumulate(f1, ...) { }` | `zipV(f1, ...) { }` | Same API, up to 22 |
| `either { ... }` | `validated { }` / `flatMapV { }` | Short-circuit on error with `bind()` |
| `Resource({ }, { })` | `Resource({ }, { })` | Identical API |
| `Schedule.recurs(n)` | `Schedule.recurs(n)` | Identical API |
| `parMap(concurrency) { }` | `traverse(concurrency) { }` | Standard FP naming |

---

## Migration from Arrow — Before/After

Already using Arrow and curious about switching? Here are concrete side-by-side comparisons:

**Multi-phase orchestration:**

```kotlin
// Arrow: nested parZip blocks
val phase1 = parZip({ fetchUser() }, { fetchCart() }, { fetchPromos() }) { u, c, p -> Triple(u, c, p) }
val validated = validateStock(phase1)
val phase3 = parZip({ calcShipping() }, { calcTax() }) { s, t -> s to t }

// This library: one flat chain
val checkout = Async {
    lift5(::Checkout)
        .ap { fetchUser() }.ap { fetchCart() }.ap { fetchPromos() }
        .followedBy { validateStock() }
        .ap { calcShipping() }.ap { calcTax() }
}
```

**Parallel validation with error accumulation:**

```kotlin
// Arrow: zipOrAccumulate (max 9 args, not parallel)
either {
    zipOrAccumulate(
        { validateName(input).bind() },
        { validateEmail(input).bind() },
    ) { name, email -> UserData(name, email) }
}

// This library: zipV (up to 22 args, runs in parallel)
Async {
    zipV(
        { validateName(input) },
        { validateEmail(input) },
    ) { name, email -> UserData(name, email) }
}
```

**Phased validation (parallel within, sequential between):**

```kotlin
// Arrow: manual nesting
val phase1 = either {
    zipOrAccumulate({ validateName(input).bind() }, { validateEmail(input).bind() }) { n, e -> Identity(n, e) }
}
val phase2 = phase1.flatMap { id ->
    either { zipOrAccumulate({ checkBlocked(id).bind() }, { checkAvailable(id).bind() }) { a, b -> Reg(id, a, b) } }
}

// This library: accumulate builder
Async {
    accumulate {
        val identity = zipV({ validateName(input) }, { validateEmail(input) }) { n, e -> Identity(n, e) }.bindV()
        val cleared = zipV({ checkBlocked(identity) }, { checkAvailable(identity) }) { a, b -> a to b }.bindV()
        Registration(identity, cleared)
    }
}
```

---

## When to Use (and When Not To)

**This library shines when:**
- You have 4+ concurrent operations with sequential phases (BFF, checkout, booking)
- You need parallel error accumulation across validators
- The dependency graph should be visible in code shape
- You want compile-time parameter order safety
- You need composable retry/timeout/race policies

**Use something else when:**
- 2-3 simple parallel calls — `coroutineScope { async {} }` is enough
- Purely sequential code — regular `suspend` functions
- Stream processing — use `Flow`
- Full FP ecosystem (optics, typeclasses) — use Arrow

**Target audience:** BFF layers, checkout/booking flows, dashboard aggregation, multi-service orchestration.

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

All arities are **unified at 22** — the maximum supported by Kotlin's function types.

### Core

| Combinator | Semantics | Parallelism |
|---|---|---|
| `lift2`..`lift22` + `.ap` | N-way fan-out (curried, type-safe ordering) | Parallel |
| `liftA2`..`liftA5` | Haskell-style applicative lifting (parZip-like) | Parallel |
| `product(fa, fb)` / `product(fa, fb, fc)` | Parallel into Pair/Triple | Parallel |
| `.followedBy` | True phase barrier | Sequential (gates) |
| `.thenValue` | Sequential value fill, no barrier | Sequential (no gate) |
| `.flatMap` | Monadic bind (value-dependent) | Sequential |
| `map` / `pure` / `unit` | Transform / wrap value | — |
| `Computation.failed(error)` | Immediately failing computation | — |
| `Computation.defer { }` | Lazy computation construction | — |
| `.memoize()` | Cache result, execute once (thread-safe via `Mutex`) | — |
| `.memoizeOnSuccess()` | Like `memoize` but retries on failure — production-safe | — |
| `.ensure(error) { pred }` | Short-circuit guard: throws if predicate fails | — |
| `.ensureNotNull(error) { extract }` | Extract non-null or throw — avoids nested null checks | — |
| `.on(context)` / `.named(name)` | Dispatcher / coroutine name | — |
| `.void()` / `.tap { }` | Discard result / side-effect | — |
| `.attempt()` | Catch to `Either<Throwable, A>` | — |
| `.await()` | Execute a `Computation` from any suspend context | — |
| `.orElse(other)` | Sequential fallback on failure (CancellationException always propagates) | Sequential |
| `firstSuccessOf(c1, c2, ...)` | Try each sequentially, return first success | Sequential |
| `computation { }` | Imperative builder with `.bind()` — sequential monadic DSL | Sequential |
| `.raceAgainst(other)` | Extension sugar for `race(this, other)` | Competitive |
| `.zipLeft` / `.zipRight` | Parallel, keep one result | Parallel |

### Collections

| Combinator | Semantics | Parallelism |
|---|---|---|
| `zip` (2-22 arity) / `mapN` (2-22) | Combine computations | Parallel |
| `traverse(f)` / `traverse(n, f)` | Map + parallel execution | Parallel (bounded) |
| `sequence()` / `sequence(n)` | Execute collection | Parallel (bounded) |

| `race` / `raceN` / `raceAll` | First to succeed | Competitive |
| `raceEither(fa, fb)` | First to succeed, different types | Competitive |

> **Design choice — no `parZip` / `parMap`:** All composition is parallel by default. The `par` prefix (Arrow/Cats naming) implies parallelism is a special mode — here it's the only mode. **Equivalents:** `parZip(f1, f2) { }` → `mapN(f1, f2) { }` or `lift2(::R).ap{f1}.ap{f2}`. `parMap { }` → `traverse { }`.

### Error Handling, Retry & Schedule

| Combinator | Semantics |
|---|---|
| `recover` / `recoverWith` / `fallback` | Catch and recover |
| `timeout(d)` / `timeout(d, default)` / `timeout(d, comp)` | Time-bounded |
| `timeoutRace(d, fallback)` | Parallel timeout (fallback starts immediately) |
| `retry(n, delay, backoff, shouldRetry, onRetry)` | Configurable retry |
| `retry(schedule)` / `retry(scheduleFactory)` / `retryOrElse(schedule, fallback)` | Schedule-based retry (factory overload for stateful schedules) |
| `retryWithResult(schedule)` | Retry returning `RetryResult(value, attempts, totalDelay)` |
| `Schedule.recurs` / `.spaced` / `.exponential` / `.fibonacci` / `.linear` / `.forever` | Backoff strategies |
| `Schedule.doWhile` / `.doUntil` / `.jittered` / `.withMaxDuration` | Filters and limits |
| `schedule.collect()` / `schedule.zipWith(other, f)` | Accumulate inputs / custom delay merge |
| `s1 and s2` / `s1 or s2` | Schedule composition |
| `schedule.fold(init) { acc, a -> }` | Accumulate values across retries |
| `CircuitBreaker(maxFailures, resetTimeout)` | Protect downstream from cascading failures |
| `.withCircuitBreaker(breaker)` | Wrap computation with circuit breaker |

### Validation (Error Accumulation)

| Combinator | Semantics |
|---|---|
| `zipV` (2-22 args) | Parallel validation, all errors accumulated |
| `liftV2`..`liftV22` + `apV` | Curried parallel validation |
| `followedByV` / `thenValueV` / `flatMapV` | Phase barriers / sequential short-circuit |
| `validated { }` / `accumulate { }` | Short-circuit builder with `.bind()` / `.bindV()` — sequential validation DSL |
| `valid` / `invalid` / `catching` / `validate` | Entry points |
| `Nel<A>` (typealias) | Shorthand for `NonEmptyList<A>` — reduces validated type verbosity |
| `recoverV` / `mapV` / `mapError` / `orThrow` | Transforms |
| `traverseV` / `sequenceV` | Collection operations with accumulation |

### Resource Safety

| Combinator | Semantics |
|---|---|
| `bracket(acquire, use, release)` | Guaranteed cleanup (NonCancellable) |
| `bracketCase(acquire, use, release)` | Cleanup with `ExitCase` (commit/rollback) |
| `guarantee` / `guaranteeCase` | Finalizers with optional `ExitCase` |
| `Resource(acquire, release)` | Composable resource monad |
| `Resource.zip(r1..r22, f)` | Combine up to 22 resources |
| `Resource.defer { }` | Lazy/conditional resource construction |
| `resource.use` / `resource.useWithTimeout` / `resource.useComputation` | Terminal operations |

### Interop & Observability

| Combinator | Semantics |
|---|---|
| `Deferred.toComputation()` / `Computation.toDeferred(scope)` | Deferred bridge |
| `Flow.firstAsComputation()` / `(suspend () -> A).toComputation()` | Flow/lambda bridge |
| `Computation.toFlow()` / `Flow.collectAsComputation()` | Flow ↔ Computation |
| `Flow.mapComputation(concurrency) { }` / `Flow.filterComputation { }` | Flow + Computation pipeline |
| `Result.toEither()` / `Either.toResult()` / `Result.toValidated()` | Kotlin Result bridge |
| `Either.catch { }` / `Either.catchNonFatal { }` | Exception-safe Either construction |
| `Either.ensure` / `.filterOrElse` / `.getOrHandle` / `.handleErrorWith` | Either combinators |
| `Either.recover` / `.orNull` / `.tapLeft` | Recovery and inspection |
| `Either.zip(other) { }` / `.toValidatedNel()` | Either composition & bridge |
| `Iterable.traverseEither(f)` / `Iterable<Either>.sequence()` | Collection operations |
| `traced(name, tracer)` / `traced(name, onStart, onSuccess, onError)` | Observability hooks |
| `delayed(d, value)` / `catching { }` / `apOrNull` | Utilities |

---

## Examples

Full working examples in [`/examples`](examples/):

- **[ecommerce-checkout](examples/ecommerce-checkout/)** — 11 services, 5 phases
- **[dashboard-aggregator](examples/dashboard-aggregator/)** — 14-service BFF
- **[validated-registration](examples/validated-registration/)** — 12-field parallel validation

Arrow interop module: [`/arrow-interop`](arrow-interop/) — optional bridges for `Either`, `NonEmptyList`, `parZip`.

---

## Building

```bash
./gradlew jvmTest              # 790 tests
./gradlew :arrow-interop:test  # Arrow interop tests
./gradlew :benchmarks:jmh      # JMH benchmarks
./gradlew dokkaHtml             # API docs
./gradlew generateAll           # regenerate all overloads (arities 2-22)
```

> **Native targets (iOS, macOS, Linux):** `./gradlew build` compiles all Kotlin/Native targets.
> Apple targets (iOS, macOS) require Xcode and its command-line tools (`xcode-select --install`).
> If you only need JVM/JS, use `./gradlew jvmTest` or `./gradlew jsTest` instead.

See [PUBLISHING.md](PUBLISHING.md) for Maven Central publishing instructions.

## License

Apache 2.0
