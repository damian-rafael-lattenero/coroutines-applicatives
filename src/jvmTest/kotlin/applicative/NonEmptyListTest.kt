package applicative

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NonEmptyListTest {

    @Test
    fun `nel wraps single value`() {
        val nel = 42.toNonEmptyList()
        assertEquals(42, nel.head)
        assertEquals(emptyList(), nel.tail)
    }

    @Test
    fun `Nel of creates from varargs`() {
        val nonEmptyList = NonEmptyList.of(1, 2, 3)
        assertEquals(1, nonEmptyList.head)
        assertEquals(listOf(2, 3), nonEmptyList.tail)
    }

    @Test
    fun `Nel of with single element`() {
        val nonEmptyList = NonEmptyList.of("only")
        assertEquals("only", nonEmptyList.head)
        assertEquals(emptyList(), nonEmptyList.tail)
    }

    @Test
    fun `size returns 1 for single element`() {
        assertEquals(1, NonEmptyList(42).size)
    }

    @Test
    fun `size returns head plus tail`() {
        assertEquals(4, NonEmptyList.of(1, 2, 3, 4).size)
    }

    @Test
    fun `get index 0 returns head`() {
        val nonEmptyList = NonEmptyList.of("a", "b", "c")
        assertEquals("a", nonEmptyList[0])
    }

    @Test
    fun `get index gt 0 returns tail element`() {
        val nonEmptyList = NonEmptyList.of("a", "b", "c")
        assertEquals("b", nonEmptyList[1])
        assertEquals("c", nonEmptyList[2])
    }

    @Test
    fun `isEmpty always returns false`() {
        assertFalse(NonEmptyList(1).isEmpty())
        assertFalse(NonEmptyList.of(1, 2, 3).isEmpty())
    }

    @Test
    fun `plus concatenates two Nels preserving order`() {
        val a = NonEmptyList.of(1, 2)
        val b = NonEmptyList.of(3, 4)
        val result = a + b
        assertEquals(listOf(1, 2, 3, 4), result.toList())
    }

    @Test
    fun `plus with single element Nels`() {
        val result = NonEmptyList(1) + NonEmptyList(2)
        assertEquals(listOf(1, 2), result.toList())
    }

    @Test
    fun `toString formats as NonEmptyList(elements)`() {
        assertEquals("NonEmptyList(1, 2, 3)", NonEmptyList.of(1, 2, 3).toString())
        assertEquals("NonEmptyList(42)", NonEmptyList(42).toString())
    }

    @Test
    fun `toList returns all elements in order`() {
        assertEquals(listOf("a", "b", "c"), NonEmptyList.of("a", "b", "c").toList())
    }

    @Test
    fun `can iterate with forEach`() {
        val collected = mutableListOf<Int>()
        NonEmptyList.of(10, 20, 30).forEach { collected.add(it) }
        assertEquals(listOf(10, 20, 30), collected)
    }

    @Test
    fun `can be used in validated computations`() {
        val left: Either<NonEmptyList<String>, Int> = Either.Left(NonEmptyList("error"))
        assertTrue(left is Either.Left)
        assertEquals("error", (left as Either.Left).value.head)
    }
}
