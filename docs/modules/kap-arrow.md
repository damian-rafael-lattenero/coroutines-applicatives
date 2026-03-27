# kap-arrow

Arrow integration for parallel validation with error accumulation.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-arrow:2.3.0")
```

**Depends on:** `kap-core` + Arrow Core.
**Platforms:** JVM only.
**Tests:** 223 tests across 10 test classes.

---

## The Problem — Validation Round Trips

=== "Raw Coroutines (short-circuits)"

    ```kotlin
    // Sequential: returns FIRST error, user must fix and resubmit for each one
    suspend fun registerUser(name: String, email: String, age: Int, username: String): User {
        val validName = validateName(name)       // ← fails here? stops
        val validEmail = validateEmail(email)     // ← never reached
        val validAge = validateAge(age)           // ← never reached
        val validUsername = checkUsername(username) // ← never reached
        return User(validName, validEmail, validAge, validUsername)
    }
    // 5 invalid fields = 5 round trips = terrible UX
    ```

=== "Arrow (max 9 validators)"

    ```kotlin
    val result = Either.zipOrAccumulate(
        { validateName(name) },
        { validateEmail(email) },
        { validateAge(age) },
        { checkUsername(username) },
    ) { n, e, a, u -> User(n, e, a, u) }
    // All errors at once — but maxes out at 9 parameters
    ```

=== "KAP (max 22 validators, parallel)"

    ```kotlin
    val result: Either<NonEmptyList<RegError>, User> = Async {
        zipV(
            { validateName(name) },
            { validateEmail(email) },
            { validateAge(age) },
            { checkUsername(username) },
        ) { n, e, a, u -> User(n, e, a, u) }
    }
    // All errors at once + all validators run in PARALLEL + scales to 22
    ```

---

## Writing Validators

Each validator returns `Either<NonEmptyList<E>, A>`:

```kotlin
sealed class RegError(val message: String) {
    class InvalidName(msg: String) : RegError(msg)
    class InvalidEmail(msg: String) : RegError(msg)
    class InvalidAge(msg: String) : RegError(msg)
    class WeakPassword(msg: String) : RegError(msg)
    class UsernameTaken(msg: String) : RegError(msg)
}

data class ValidName(val value: String)
data class ValidEmail(val value: String)
data class ValidAge(val value: Int)
data class ValidUsername(val value: String)
data class ValidPassword(val value: String)
data class User(val name: ValidName, val email: ValidEmail, val age: ValidAge, val username: ValidUsername)

suspend fun validateName(name: String): Either<NonEmptyList<RegError>, ValidName> {
    delay(20)
    return if (name.length >= 2) Either.Right(ValidName(name))
    else Either.Left(nonEmptyListOf(RegError.InvalidName("Name must be >= 2 chars")))
}

suspend fun validateEmail(email: String): Either<NonEmptyList<RegError>, ValidEmail> {
    delay(15)
    return if ("@" in email) Either.Right(ValidEmail(email))
    else Either.Left(nonEmptyListOf(RegError.InvalidEmail("Invalid email: $email")))
}

suspend fun validateAge(age: Int): Either<NonEmptyList<RegError>, ValidAge> {
    delay(10)
    return if (age >= 18) Either.Right(ValidAge(age))
    else Either.Left(nonEmptyListOf(RegError.InvalidAge("Must be >= 18, got $age")))
}

suspend fun checkUsername(username: String): Either<NonEmptyList<RegError>, ValidUsername> {
    delay(25)  // async DB check
    return if (username.length >= 3) Either.Right(ValidUsername(username))
    else Either.Left(nonEmptyListOf(RegError.UsernameTaken("Username too short")))
}
```

---

## `zipV` — Parallel Validation (2-22 args)

### All pass

```kotlin
val valid: Either<NonEmptyList<RegError>, User> = Async {
    zipV(
        { validateName("Alice") },
        { validateEmail("alice@example.com") },
        { validateAge(25) },
        { checkUsername("alice") },
    ) { name, email, age, username -> User(name, email, age, username) }
}
// Right(User(ValidName(Alice), ValidEmail(alice@example.com), ValidAge(25), ValidUsername(alice)))
```

### All fail — every error collected

```kotlin
val invalid: Either<NonEmptyList<RegError>, User> = Async {
    zipV(
        { validateName("A") },           // ← too short
        { validateEmail("bad") },         // ← no @
        { validateAge(10) },              // ← under 18
        { checkUsername("al") },          // ← too short
    ) { name, email, age, username -> User(name, email, age, username) }
}
// Left(NonEmptyList(InvalidName, InvalidEmail, InvalidAge, UsernameTaken))
// ALL 4 errors in ONE response. All ran in parallel.
```

Scales to **22 validators**. Arrow's `zipOrAccumulate` maxes at 9.

---

## `kapV` + `withV` — Curried Builder

Same parallel execution and error accumulation, typed chain syntax:

```kotlin
val result = Async {
    kapV<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
        .withV { validateName("Alice") }
        .withV { validateEmail("alice@example.com") }
        .withV { validateAge(25) }
        .withV { checkUsername("alice") }
}
```

Swap two `.withV` lines? **Compile error** — same type safety as `kap` + `.with`.

---

## Phased Validation — `thenV` / `andThenV`

Some validations depend on earlier results. Phase 1 collects all basic errors. Only if all pass does phase 2 run:

```kotlin
data class Identity(val name: ValidName, val email: ValidEmail, val age: ValidAge)
data class Clearance(val notBlocked: Boolean, val available: Boolean)
data class Registration(val identity: Identity, val clearance: Clearance)

val result: Either<NonEmptyList<RegError>, Registration> = Async {
    accumulate {
        // Phase 1: validate basic fields in parallel, collect ALL errors
        val identity = zipV(
            { validateName("Alice") },
            { validateEmail("alice@example.com") },
            { validateAge(25) },
        ) { name, email, age -> Identity(name, email, age) }
            .bindV()  // short-circuits if phase 1 fails

        // Phase 2: only runs if phase 1 passed — uses identity result
        val cleared = zipV(
            { checkNotBlacklisted(identity) },
            { checkUsernameAvailable(identity.email.value) },
        ) { a, b -> Clearance(a, b) }
            .bindV()

        Registration(identity, cleared)
    }
}
```

---

## Entry Points

### `valid(a)` / `invalid(e)` / `invalidAll(errors)`

```kotlin
val success: Validated<RegError, ValidName> = valid(ValidName("Alice"))
val failure: Validated<RegError, ValidName> = invalid(RegError.InvalidName("too short"))
val multiError: Validated<RegError, ValidName> = invalidAll(
    nonEmptyListOf(RegError.InvalidName("too short"), RegError.InvalidName("no numbers"))
)
```

### `catching(toError) { }` — Exception to error bridge

```kotlin
val result = Async {
    catching<RegError, String>({ e -> RegError.InvalidName(e.message ?: "unknown") }) {
        riskyOperation()
    }
}
```

---

## Guards — `ensureV` / `ensureVAll`

```kotlin
val result = Async {
    valid(ValidAge(15))
        .ensureV(RegError.InvalidAge("Must be 18+")) { it.value >= 18 }
}
// Left(NonEmptyList(InvalidAge("Must be 18+")))

val result2 = Async {
    valid(ValidPassword("123"))
        .ensureVAll { password ->
            buildList {
                if (password.value.length < 8) add(RegError.WeakPassword("Too short"))
                if (!password.value.any { it.isUpperCase() }) add(RegError.WeakPassword("No uppercase"))
                if (!password.value.any { it.isDigit() }) add(RegError.WeakPassword("No digit"))
            }.let { if (it.isEmpty()) null else nonEmptyListOf(it.first(), *it.drop(1).toTypedArray()) }
        }
}
// Left(NonEmptyList(WeakPassword("Too short"), WeakPassword("No uppercase")))
```

---

## Transforms

### `.mapV { }` — Transform success

```kotlin
val result = Async {
    valid(ValidName("alice"))
        .mapV { it.value.uppercase() }
}
// Right("ALICE")
```

### `.mapError { }` — Transform error type

```kotlin
val result = Async {
    invalid(RegError.InvalidName("too short"))
        .mapError { ApiError(it.message) }
}
```

### `.recoverV { }` — Recover from validation errors

```kotlin
val result = Async {
    invalid(RegError.InvalidName("too short"))
        .recoverV { errors -> ValidName("default-${errors.size}-errors") }
}
// Right(ValidName("default-1-errors"))
```

### `.orThrow()` — Unwrap or throw

```kotlin
val user: User = Async {
    zipV(
        { validateName("Alice") },
        { validateEmail("alice@example.com") },
        { validateAge(25) },
        { checkUsername("alice") },
    ) { name, email, age, username -> User(name, email, age, username) }
        .orThrow()  // Right → value, Left → throws
}
```

---

## Collection Operations

### `traverseV` — Validate each element

```kotlin
val emails = listOf("alice@example.com", "bad", "bob@example.com", "also-bad")
val result = Async {
    emails.traverseV { email -> validateEmail(email) }
}
// Left(NonEmptyList(InvalidEmail("bad"), InvalidEmail("also-bad")))
// ALL invalid emails reported, not just the first
```

### `sequenceV` — Sequence validated computations

```kotlin
val validated: List<Validated<RegError, ValidEmail>> = emails.map { email ->
    Kap { validateEmail(email) }
}
val result = Async { validated.sequenceV() }
```

---

## Arrow Interop

### `.attempt()` — Catch to Either

=== "Raw Coroutines"

    ```kotlin
    val result: Either<Throwable, String> = try {
        Either.Right(riskyOperation())
    } catch (e: Exception) {
        Either.Left(e)
    }
    ```

=== "KAP"

    ```kotlin
    val success: Either<Throwable, String> = Async {
        Kap { "hello" }.attempt()
    }
    // Right("hello")

    val failure: Either<Throwable, String> = Async {
        Kap<String> { throw RuntimeException("boom") }.attempt()
    }
    // Left(RuntimeException("boom"))
    ```

### `raceEither(fa, fb)` — Race two different types

```kotlin
val result: Either<String, Int> = Async {
    raceEither(
        fa = Kap { delay(30); "fast-string" },
        fb = Kap { delay(100); 42 },
    )
}
// Left("fast-string") — String won the race
// Loser cancelled automatically
```

---

## `accumulate { }` Builder

For imperative-style validation with `.bindV()`:

```kotlin
val result = Async {
    accumulate<RegError, Registration> {
        val identity = zipV(
            { validateName("Alice") },
            { validateEmail("alice@example.com") },
            { validateAge(25) },
        ) { name, email, age -> Identity(name, email, age) }
            .bindV()  // short-circuits if phase 1 fails

        val cleared = zipV(
            { checkNotBlacklisted(identity) },
            { checkUsernameAvailable(identity.email.value) },
        ) { a, b -> Clearance(a, b) }
            .bindV()

        Registration(identity, cleared)
    }
}
```

!!! warning "Short-circuit vs parallel"
    `.bindV()` short-circuits (monadic): if phase 1 fails, phase 2 never runs.
    `zipV` accumulates (applicative): all validators run, all errors collected.
    Use `zipV` within a phase, `bindV` between phases.

---

## Type Alias

```kotlin
typealias Validated<E, A> = Kap<Either<NonEmptyList<E>, A>>
```

This means all `Kap` combinators (`.map`, `.timeout`, `.retry`, `.traced`, etc.) work on validated computations too.
