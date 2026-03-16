package applicative

/**
 * A non-empty list — guarantees at least one element at the type level.
 *
 * Used as the error accumulator in [validated][apV] computations.
 * Extends [AbstractList] for zero-allocation `List` compliance — `size` and
 * `get` are computed on the fly from [head] and [tail] without creating a
 * backing list.
 *
 * **Note on equality:** [equals] and [hashCode] are inherited from [AbstractList],
 * which compares element-by-element. Two `NonEmptyList` instances with the same elements
 * in the same order are equal regardless of how they were constructed.
 */
class NonEmptyList<out A>(val head: A, val tail: List<A> = emptyList()) : AbstractList<A>() {

    override val size: Int get() = 1 + tail.size

    override fun get(index: Int): A =
        if (index == 0) head else tail[index - 1]

    override fun isEmpty(): Boolean = false

    companion object {
        /** Creates a [NonEmptyList] from one or more elements. */
        fun <A> of(head: A, vararg tail: A): NonEmptyList<A> = NonEmptyList(head, tail.toList())
    }

    override fun toString(): String = "NonEmptyList(${joinToString(", ")})"
}

/** Concatenates two non-empty lists, preserving all elements in order. */
operator fun <A> NonEmptyList<A>.plus(other: NonEmptyList<A>): NonEmptyList<A> =
    NonEmptyList(head, buildList(tail.size + other.size) {
        addAll(tail)
        add(other.head)
        addAll(other.tail)
    })

/** Wraps a single value into a [NonEmptyList]. */
fun <A> A.toNonEmptyList(): NonEmptyList<A> = NonEmptyList(this)