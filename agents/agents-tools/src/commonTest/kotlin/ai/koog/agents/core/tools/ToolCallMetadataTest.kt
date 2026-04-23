package ai.koog.agents.core.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ToolCallMetadataTest {

    @Test
    fun testEmptyIsEmpty() {
        assertTrue(ToolCallMetadata.EMPTY.isEmpty())
        assertFalse(ToolCallMetadata.EMPTY.isNotEmpty())
        assertTrue(ToolCallMetadata.EMPTY.asMap().isEmpty())
        assertTrue(ToolCallMetadata.EMPTY.keys.isEmpty())
    }

    @Test
    fun testOfWithNoPairsReturnsSharedEmpty() {
        assertSame(ToolCallMetadata.EMPTY, ToolCallMetadata.of())
    }

    @Test
    fun testOfWithPairsBuildsMap() {
        val m = ToolCallMetadata.of("trace.span.id" to "abc", "attempt" to 3)

        assertFalse(m.isEmpty())
        assertTrue(m.isNotEmpty())
        assertEquals("abc", m["trace.span.id"])
        assertEquals(3, m["attempt"])
        assertEquals(setOf("trace.span.id", "attempt"), m.keys)
    }

    @Test
    fun testGetOnMissingKeyReturnsNull() {
        val m = ToolCallMetadata.of("present" to "value")
        assertNull(m["absent"])
    }

    @Test
    fun testContainsDistinguishesAbsentFromNullValue() {
        val m = ToolCallMetadata(mapOf("present-null" to null))

        assertTrue("present-null" in m)
        assertFalse("absent" in m)
        assertNull(m["present-null"])
        assertNull(m["absent"])
    }

    @Test
    fun testPlusOverwritesOnKeyCollision() {
        val a = ToolCallMetadata.of("shared" to 1, "only-a" to "a")
        val b = ToolCallMetadata.of("shared" to 2, "only-b" to "b")

        val merged = a + b

        assertEquals(2, merged["shared"])
        assertEquals("a", merged["only-a"])
        assertEquals("b", merged["only-b"])
    }

    @Test
    fun testPlusWithEmptyReturnsSameInstance() {
        val a = ToolCallMetadata.of("k" to "v")

        assertSame(a, a + ToolCallMetadata.EMPTY)
        assertSame(a, ToolCallMetadata.EMPTY + a)
    }

    @Test
    fun testPlusMapOverwritesOnKeyCollision() {
        val a = ToolCallMetadata.of("shared" to 1, "only-a" to "a")

        val merged = a + mapOf("shared" to 2, "only-b" to "b")

        assertEquals(2, merged["shared"])
        assertEquals("a", merged["only-a"])
        assertEquals("b", merged["only-b"])
    }

    @Test
    fun testPlusMapEmptyReturnsSameInstance() {
        val a = ToolCallMetadata.of("k" to "v")
        assertSame(a, a + emptyMap())
    }

    @Test
    fun testEqualsByValue() {
        val a = ToolCallMetadata.of("k" to 1)
        val b = ToolCallMetadata.of("k" to 1)
        val c = ToolCallMetadata.of("k" to 2)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotSame(a, b)
        assertFalse(a == c)
    }
}
