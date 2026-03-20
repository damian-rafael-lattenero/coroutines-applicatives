package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.time.Duration

// ── Deferred ↔ Computation ───────────────────────────────────────────────

/**
 * Wraps an already-started [Deferred] into a [Computation] that awaits it.
 */
fun <A> Deferred<A>.toComputation(): Computation<A> = Computation {
    this@toComputation.await()
}

/**
 * Eagerly starts this computation as a [Deferred] in [scope].
 */
fun <A> Computation<A>.toDeferred(scope: CoroutineScope): Deferred<A> =
    scope.async { with(this@toDeferred) { execute() } }

// ── Flow → Computation ───────────────────────────────────────────────────

/**
 * Creates a [Computation] that collects the first emission from this [Flow].
 */
fun <A> Flow<A>.firstAsComputation(): Computation<A> = Computation {
    this@firstAsComputation.first()
}

// ── suspend lambda → Computation ─────────────────────────────────────────

/**
 * Wraps a suspend lambda into an explicit [Computation].
 *
 * This is the same conversion that [ap]'s lambda overload does internally,
 * but useful when you need a `Computation` value to pass around.
 */
fun <A> (suspend () -> A).toComputation(): Computation<A> = Computation {
    this@toComputation()
}

// ── delayed: computation that waits then returns a value ────────────────

/**
 * Creates a [Computation] that delays for [duration] then returns [value].
 *
 * Useful for testing and for composing timed sequences.
 *
 * ```
 * race(
 *     Computation { fetchFromService() },
 *     delayed(2.seconds, fallbackValue),
 * )
 * ```
 */
fun <A> delayed(duration: Duration, value: A): Computation<A> = Computation {
    kotlinx.coroutines.delay(duration)
    value
}

/**
 * Creates a [Computation] that delays for [duration] then executes [block].
 */
fun <A> delayed(duration: Duration, block: suspend () -> A): Computation<A> = Computation {
    kotlinx.coroutines.delay(duration)
    block()
}

// ── catching: exception-safe computation builder ────────────────────────

/**
 * Creates a [Computation] that catches non-cancellation exceptions and wraps
 * the outcome in a [Result].
 *
 * Unlike [kotlin.runCatching], this **never catches [CancellationException]** —
 * structured concurrency cancellation always propagates.
 *
 * ```
 * val safe: Computation<Result<User>> = catching { fetchUser() }
 * ```
 */
fun <A> catching(block: suspend CoroutineScope.() -> A): Computation<Result<A>> = Computation {
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
