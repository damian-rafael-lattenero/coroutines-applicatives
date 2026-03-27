# kap-core

The foundation module. Provides parallel orchestration with type-safe phases.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-core:2.3.0")
```

**Depends on:** `kotlinx-coroutines-core` only.
**Platforms:** JVM, JS (IR), Linux X64, macOS (x64/ARM64), iOS (x64/ARM64/Simulator).

---

## Orchestration Primitives

| Combinator | Semantics | Parallelism |
|---|---|---|
| `kap` + `.with` | N-way fan-out (typed, safe ordering) | Parallel |
| `combine` | Lifting with suspend lambdas or Kaps | Parallel |
| `pair(fa, fb)` / `triple(fa, fb, fc)` | Parallel into Pair/Triple | Parallel |
| `.then` | True phase barrier | Sequential (gates) |
| `.thenValue` | Sequential value fill, no barrier | Sequential (no gate) |
| `.andThen` | Value-dependent sequencing | Sequential |

## Construction

| Combinator | Semantics |
|---|---|
| `Kap { }` | Wrap a suspend lambda |
| `Kap.of(value)` | Wrap a pure value |
| `Kap.empty()` | Unit computation |
| `Kap.failed(error)` | Wrap a failure |
| `Kap.defer { }` | Lazy construction |
| `kap(f)` | Curry function `f` and wrap for `.with` chains |

## Transforms & Guards

| Combinator | Semantics |
|---|---|
| `map` | Transform the result |
| `.ensure(error) { pred }` | Guard with predicate |
| `.ensureNotNull(error) { extract }` | Guard against null |
| `.discard()` / `.peek { }` | Discard result / side-effect |
| `.on(context)` | Switch dispatcher |
| `.named(name)` | Set coroutine name |

## Error Handling

| Combinator | Semantics |
|---|---|
| `.timeout(d, default)` / `.timeout(d, fallback)` | Timeout with fallback |
| `.recover { }` / `.recoverWith { }` | Recover from failure |
| `.fallback(other)` / `.orElse(other)` | Try another computation |
| `firstSuccessOf(c1, c2, ...)` | First to succeed in order |
| `.retry(maxAttempts, delay, backoff)` | Simple retry |
| `.settled()` | Wrap in `Result` (no sibling cancellation) |
| `catching { }` | Exception-safe `Result<A>` |

## Memoization

| Combinator | Semantics |
|---|---|
| `.memoize()` | Cache result (success or failure) |
| `.memoizeOnSuccess()` | Cache only successes — failures retry |

## Collections

| Combinator | Semantics |
|---|---|
| `zip` (2-22) / `combine` (2-22) | Combine computations in parallel |
| `traverse(f)` / `traverse(n, f)` | Map + parallel execution (bounded) |
| `traverseDiscard(f)` | Fire-and-forget parallel |
| `sequence()` / `sequence(n)` | Execute collection in parallel |
| `traverseSettled(f)` / `traverseSettled(n, f)` | Collect ALL results |
| `race` / `raceN` / `raceAll` | First to succeed wins |

## Racing

| Combinator | Semantics |
|---|---|
| `race(fa, fb)` | First to succeed, loser cancelled |
| `raceN(c1, c2, ..., cN)` | N-way race |
| `raceAll(list)` | Race a list |

## Flow Integration

| Combinator | Semantics |
|---|---|
| `Flow.mapEffect(concurrency) { }` | Parallel Flow processing |
| `Flow.mapEffectOrdered(concurrency) { }` | Parallel with upstream order |
| `Flow.firstAsKap()` | First emission as Kap |

## Interop

| Combinator | Semantics |
|---|---|
| `Deferred.toKap()` / `Kap.toDeferred(scope)` | Deferred bridge |
| `(suspend () -> A).toKap()` | Lambda bridge |
| `computation { }` | Imperative builder with `.bind()` |
| `delayed(d, value)` | Delayed value |

## Observability

```kotlin
val tracer = KapTracer { event ->
    when (event) {
        is TraceEvent.Started -> logger.info("${event.name} started")
        is TraceEvent.Succeeded -> metrics.timer(event.name).record(event.duration)
        is TraceEvent.Failed -> logger.error("${event.name} failed", event.error)
    }
}

Kap { fetchUser() }.traced("user-fetch", tracer)
```

## Tests

438 tests across 33 test classes including property-based tests and algebraic law verification.
