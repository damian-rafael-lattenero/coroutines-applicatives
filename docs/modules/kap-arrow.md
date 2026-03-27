# kap-arrow

Arrow integration for parallel validation with error accumulation.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-arrow:2.3.0")
```

**Depends on:** `kap-core` + Arrow Core.
**Platforms:** JVM only.

---

## Core Concept: Validated

`Validated<E, A>` is a type alias for `Kap<Either<NonEmptyList<E>, A>>` — a suspended computation that produces either accumulated errors or a success value.

## Parallel Validation

### zipV (2-22 args)

Run validators in parallel, collect ALL errors:

```kotlin
val result: Either<NonEmptyList<RegError>, User> = Async {
    zipV(
        { validateName("Alice") },
        { validateEmail("alice@example.com") },
        { validateAge(25) },
        { checkUsername("alice") },
    ) { name, email, age, username -> User(name, email, age, username) }
}
```

Scales to **22 validators** (Arrow's `zipOrAccumulate` maxes at 9).

### kapV + withV

Curried builder style:

```kotlin
val result = Async {
    kapV(::User)
        .withV { validateName("Alice") }
        .withV { validateEmail("alice@example.com") }
        .withV { validateAge(25) }
}
```

## Entry Points

| Function | Semantics |
|---|---|
| `valid(a)` | Wrap a success value |
| `invalid(e)` | Wrap a single error |
| `invalidAll(errors)` | Wrap multiple errors |
| `catching(toError) { }` | Catch exceptions as validation errors |

## Phase Barriers

| Combinator | Semantics |
|---|---|
| `thenV { }` | Barrier with short-circuit on error |
| `andThenV { }` | Value-dependent sequencing |

```kotlin
zipV(
    { validateName(name) },
    { validateEmail(email) },
) { n, e -> BasicInfo(n, e) }
    .thenV { info ->
        // Only runs if both pass
        zipV(
            { checkUniqueness(info) },
            { verifyDomain(info.email) },
        ) { u, d -> FullReg(info, u, d) }
    }
```

## Transforms

| Combinator | Semantics |
|---|---|
| `mapV { }` | Transform the success value |
| `mapError { }` | Transform the error type |
| `recoverV { }` | Recover from validation errors |
| `orThrow()` | Unwrap or throw |
| `ensureV(error) { pred }` | Guard with predicate |
| `ensureVAll(errors) { pred }` | Guard returning multiple errors |

## Collection Operations

| Combinator | Semantics |
|---|---|
| `traverseV(f)` | Validate each element, accumulate all errors |
| `sequenceV()` | Sequence validated computations |

## Arrow Interop

| Combinator | Semantics |
|---|---|
| `.attempt()` | Catch to `Either<Throwable, A>` |
| `raceEither(fa, fb)` | Race two different types, result as `Either` |
| `Kap<Either<E, A>>` extensions | Bridge between KAP and Arrow types |

## validated { } Builder

Short-circuit builder with `.bindV()`:

```kotlin
val result = Async {
    validated<RegError, User> {
        val name = validateName("Alice").bindV()
        val email = validateEmail("alice@ex.com").bindV()
        User(name, email)
    }
}
```

!!! warning
    The `validated { }` builder short-circuits on first error (monadic). Use `zipV` for parallel error accumulation (applicative).

## Tests

223 tests across 10 test classes.
