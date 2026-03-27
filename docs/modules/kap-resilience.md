# kap-resilience

Retry, resource safety, and protection patterns. All composable in the KAP chain.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-resilience:2.3.0")
```

**Depends on:** `kap-core`.
**Platforms:** JVM, JS (IR), Linux X64, macOS (x64/ARM64), iOS (x64/ARM64/Simulator).

---

## Schedule — Composable Retry Policies

Build complex retry strategies from simple building blocks:

```kotlin
val policy = Schedule.times<Throwable>(5) and
    Schedule.exponential(10.milliseconds, maxDelay = 5.seconds) and
    Schedule.doWhile<Throwable> { it is IOException }
```

### Building Blocks

| Schedule | Behavior |
|---|---|
| `times(n)` | Retry up to N times |
| `spaced(d)` | Fixed delay between retries |
| `exponential(base, max)` | Exponential backoff with optional cap |
| `fibonacci(base)` | Fibonacci-sequence delays |
| `linear(base)` | Linearly increasing delays |
| `forever()` | Retry indefinitely |

### Modifiers

| Modifier | Behavior |
|---|---|
| `.jittered()` | Random jitter to prevent thundering herd |
| `.withMaxDuration(d)` | Stop after total elapsed time |
| `.doWhile { }` | Continue while predicate holds |
| `.doUntil { }` | Continue until predicate holds |

### Composition

| Operator | Behavior |
|---|---|
| `s1 and s2` | Both must agree to continue (intersection) |
| `s1 or s2` | Either can continue (union) |

### Retry Variants

| Combinator | Semantics |
|---|---|
| `.retry(schedule)` | Retry on failure |
| `.retryOrElse(schedule, fallback)` | Fallback after exhaustion |
| `.retryWithResult(schedule)` | Returns `RetryResult(value, attempts, totalDelay)` |

## CircuitBreaker

Protect downstream services from cascading failure:

```kotlin
val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

Kap { fetchUser() }.withCircuitBreaker(breaker)
```

**States:** Closed (normal) -> Open (rejecting after N failures) -> HalfOpen (testing one request) -> Closed.

Thread-safe via `Mutex`.

## timeoutRace — Parallel Fallback

```kotlin
Kap { fetchFromPrimary() }
    .timeoutRace(100.milliseconds, Kap { fetchFromFallback() })
```

Both start at t=0. If primary completes before the timeout, use it. If not, fallback is already running. **2.6x faster** than sequential timeout.

## raceQuorum — N-of-M

```kotlin
raceQuorum(
    required = 2,
    Kap { fetchReplicaA() },
    Kap { fetchReplicaB() },
    Kap { fetchReplicaC() },
)
// Returns the 2 fastest. Third cancelled.
```

Supports arities 2-22.

## Resource Safety

### bracket

```kotlin
bracket(
    acquire = { openConnection() },
    use = { conn -> Kap { conn.query("SELECT 1") } },
    release = { conn -> conn.close() },  // guaranteed, even on cancellation
)
```

### bracketCase

```kotlin
bracketCase(
    acquire = { beginTransaction() },
    use = { tx -> Kap { tx.execute(query) } },
    release = { tx, exitCase ->
        when (exitCase) {
            is ExitCase.Completed -> tx.commit()
            else -> tx.rollback()
        }
    },
)
```

### Resource

Composable resource with acquire/release:

```kotlin
val db = Resource(acquire = { openDb() }, release = { it.close() })
val cache = Resource(acquire = { openCache() }, release = { it.close() })

// Compose up to 22 resources:
Resource.zip(db, cache) { d, c -> Pair(d, c) }
    .use { (d, c) -> d.query("...") + c.get("...") }
```

### guarantee / guaranteeCase

```kotlin
guarantee(
    fa = { riskyOperation() },
    finalizer = { cleanup() },
)
```

## Tests

164 tests across 16 test classes including Schedule laws and CircuitBreaker concurrency tests.
