# Core Concepts

KAP's entire model is built on three primitives. Master these and you can express any parallel orchestration pattern.

## The Three Primitives

| Primitive | What it does | Think of it as |
|---|---|---|
| `.with { }` | Launch in parallel with everything above | "and at the same time..." |
| `.then { }` | Wait for everything above, then continue | "then, once that's done..." |
| `.andThen { ctx -> }` | Wait, pass the result, then continue | "then, using what we got..." |

## `.with` — Parallel Execution

Every `.with` runs concurrently with all other `.with` calls in the same phase:

```kotlin
val result = Async {
    kap(::Triple)
        .with { fetchUser() }     // starts at t=0
        .with { fetchCart() }     // starts at t=0
        .with { fetchPromos() }   // starts at t=0
}
// Total time = max(user, cart, promos), not sum
```

The typed function chain enforces argument order. Each `.with` must provide the next expected type:

```kotlin
// kap(::Dashboard) expects (UserProfile, CartData, PromoList) -> Dashboard
kap(::Dashboard)
    .with { fetchUser() }     // must return UserProfile  — slot 1
    .with { fetchCart() }     // must return CartData      — slot 2
    .with { fetchPromos() }   // must return PromoList     — slot 3
// Swap any two? COMPILE ERROR.
```

## `.then` — Phase Barrier

`.then` creates a synchronization point. Everything above must complete before anything below starts:

```kotlin
kap(::CheckoutResult)
    .with { fetchUser() }           // ┐ phase 1
    .with { fetchCart() }            // ┘
    .then { validateStock() }        // ── barrier: waits for phase 1
    .with { calcShipping() }         // ┐ phase 3: starts only after validateStock
    .with { calcTax() }              // ┘
```

```
t=0ms   ─── fetchUser ────┐
t=0ms   ─── fetchCart ────┘ phase 1 (parallel)
t=50ms  ─── validateStock ── phase 2 (barrier)
t=60ms  ─── calcShipping ─┐
t=60ms  ─── calcTax ──────┘ phase 3 (parallel)
```

## `.andThen` — Value-Dependent Sequencing

When the next phase **needs** the previous result:

```kotlin
kap(::UserContext)
    .with { fetchProfile(userId) }       // ┐ phase 1
    .with { fetchPreferences(userId) }   // ┘
    .andThen { ctx ->                    // ── barrier: ctx has the UserContext
        kap(::EnrichedContent)
            .with { fetchRecommendations(ctx.profile) }  // ┐ phase 2
            .with { fetchPromotions(ctx.tier) }           // ┘ uses ctx
    }
```

The dependency graph **is** the code shape. You can't accidentally read `ctx` before it's ready.

## How `Kap` Works

`Kap<A>` is a suspended computation that produces `A` when executed inside `Async { }`:

```kotlin
val effect: Kap<String> = Kap { fetchUser() }  // nothing runs yet
val result: String = Async { effect }            // runs now
```

- `Kap { }` — wraps a suspend lambda
- `Kap.of(value)` — wraps a pure value
- `Kap.failed(error)` — wraps a failure
- `Async { }` — executes a Kap, providing the coroutine scope

## Composition

`Kap` satisfies **Functor**, **Applicative**, and **Monad** laws (property-tested via Kotest):

```kotlin
// Functor: map
val user: Kap<String> = Kap { fetchUser() }
val name: Kap<String> = user.map { it.name }

// Applicative: with (parallel)
val dashboard = kap(::Dashboard).with { fetchUser() }.with { fetchCart() }

// Monad: andThen (sequential, value-dependent)
val enriched = kap(::Context)
    .with { fetchProfile() }
    .andThen { ctx -> kap(::Content).with { fetchRecs(ctx) } }
```

!!! info "Algebraic Laws"
    All laws are verified in [`ApplicativeLawsTest.kt`](https://github.com/damian-rafael-lattenero/kap/blob/master/kap-core/src/jvmTest/kotlin/kap/ApplicativeLawsTest.kt). See [LAWS.md](https://github.com/damian-rafael-lattenero/kap/blob/master/LAWS.md) for details.

## Execution Model

- **Structured concurrency**: All parallel branches run inside `coroutineScope`. If one fails, siblings cancel.
- **Cancellation safety**: `CancellationException` is never caught. All combinators re-throw it.
- **Context propagation**: `Async(MDCContext()) { ... }` propagates context to all branches.
- **No reflection**: All type safety is compile-time. No runtime overhead.

## What's Next?

- [Parallel API Aggregation](parallel-aggregation.md) — Build a real BFF with multiple phases
- [Resilient Services](resilient-services.md) — Add retry, circuit breaker, and timeouts
- [Validated Forms](validated-forms.md) — Parallel validation with error accumulation
