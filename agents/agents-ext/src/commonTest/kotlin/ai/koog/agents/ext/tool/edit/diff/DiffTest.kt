package ai.koog.agents.ext.tool.edit.diff

import kotlin.test.Test
import kotlin.test.assertEquals

class DiffTest {
    @Test
    fun testEmptyDiff() {
        val diff = Diff<String>(emptyList())
        val original = listOf("a", "b", "c")
        val result = diff.apply(original)
        assertEquals(emptyList(), result)
    }

    @Test
    fun testNoOpDiffKeepAllElements() {
        val operations = listOf(
            DiffOperation(DiffOperation.Type.KEEP, "a"),
            DiffOperation(DiffOperation.Type.KEEP, "b"),
            DiffOperation(DiffOperation.Type.KEEP, "c")
        )
        val diff = Diff(operations)
        val original = listOf("a", "b", "c")
        val result = diff.apply(original)
        assertEquals(original, result)
    }

    @Test
    fun testInsertionAtBeginning() {
        val operations = listOf(
            DiffOperation(DiffOperation.Type.INSERT, "x"),
            DiffOperation(DiffOperation.Type.KEEP, "a"),
            DiffOperation(DiffOperation.Type.KEEP, "b"),
            DiffOperation(DiffOperation.Type.KEEP, "c")
        )
        val diff = Diff(operations)
        val original = listOf("a", "b", "c")
        val result = diff.apply(original)
        assertEquals(listOf("x", "a", "b", "c"), result)
    }

    @Test
    fun testInsertionInMiddle() {
        val operations = listOf(
            DiffOperation(DiffOperation.Type.KEEP, "a"),
            DiffOperation(DiffOperation.Type.INSERT, "x"),
            DiffOperation(DiffOperation.Type.KEEP, "b"),
            DiffOperation(DiffOperation.Type.KEEP, "c")
        )
        val diff = Diff(operations)
        val original = listOf("a", "b", "c")
        val result = diff.apply(original)
        assertEquals(listOf("a", "x", "b", "c"), result)
    }

    @Test
    fun testInsertionAtEnd() {
        val operations = listOf(
            DiffOperation(DiffOperation.Type.KEEP, "a"),
            DiffOperation(DiffOperation.Type.KEEP, "b"),
            DiffOperation(DiffOperation.Type.KEEP, "c"),
            DiffOperation(DiffOperation.Type.INSERT, "x")
        )
        val diff = Diff(operations)
        val original = listOf("a", "b", "c")
        val result = diff.apply(original)
        assertEquals(listOf("a", "b", "c", "x"), result)
    }

    @Test
    fun testDeletionAtBeginning() {
        val operations = listOf(
            DiffOperation(DiffOperation.Type.DELETE, "a"),
            DiffOperation(DiffOperation.Type.KEEP, "b"),
            DiffOperation(DiffOperation.Type.KEEP, "c")
        )
        val diff = Diff(operations)
        val original = listOf("a", "b", "c")
        val result = diff.apply(original)
        assertEquals(listOf("b", "c"), result)
    }

    @Test
    fun testDeletionInMiddle() {
        val operations = listOf(
            DiffOperation(DiffOperation.Type.KEEP, "a"),
            DiffOperation(DiffOperation.Type.DELETE, "b"),
            DiffOperation(DiffOperation.Type.KEEP, "c")
        )
        val diff = Diff(operations)
        val original = listOf("a", "b", "c")
        val result = diff.apply(original)
        assertEquals(listOf("a", "c"), result)
    }

    @Test
    fun testDeletionAtEnd() {
        val operations = listOf(
            DiffOperation(DiffOperation.Type.KEEP, "a"),
            DiffOperation(DiffOperation.Type.KEEP, "b"),
            DiffOperation(DiffOperation.Type.DELETE, "c")
        )
        val diff = Diff(operations)
        val original = listOf("a", "b", "c")
        val result = diff.apply(original)
        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun testReplacementAtBeginning() {
        val operations = listOf(
            DiffOperation(DiffOperation.Type.DELETE, "a"),
            DiffOperation(DiffOperation.Type.INSERT, "x"),
            DiffOperation(DiffOperation.Type.KEEP, "b"),
            DiffOperation(DiffOperation.Type.KEEP, "c")
        )
        val diff = Diff(operations)
        val original = listOf("a", "b", "c")
        val result = diff.apply(original)
        assertEquals(listOf("x", "b", "c"), result)
    }

    @Test
    fun testReplacementInMiddle() {
        val operations = listOf(
            DiffOperation(DiffOperation.Type.KEEP, "a"),
            DiffOperation(DiffOperation.Type.DELETE, "b"),
            DiffOperation(DiffOperation.Type.INSERT, "x"),
            DiffOperation(DiffOperation.Type.KEEP, "c")
        )
        val diff = Diff(operations)
        val original = listOf("a", "b", "c")
        val result = diff.apply(original)
        assertEquals(listOf("a", "x", "c"), result)
    }

    @Test
    fun testReplacementAtEnd() {
        val operations = listOf(
            DiffOperation(DiffOperation.Type.KEEP, "a"),
            DiffOperation(DiffOperation.Type.KEEP, "b"),
            DiffOperation(DiffOperation.Type.DELETE, "c"),
            DiffOperation(DiffOperation.Type.INSERT, "x")
        )
        val diff = Diff(operations)
        val original = listOf("a", "b", "c")
        val result = diff.apply(original)
        assertEquals(listOf("a", "b", "x"), result)
    }

    @Test
    fun testMultipleOperations() {
        val operations = listOf(
            DiffOperation(DiffOperation.Type.INSERT, "w"), // Insert at beginning
            DiffOperation(DiffOperation.Type.KEEP, "a"),
            DiffOperation(DiffOperation.Type.DELETE, "b"), // Delete in middle
            DiffOperation(DiffOperation.Type.INSERT, "x"), // Replace with x
            DiffOperation(DiffOperation.Type.INSERT, "y"), // Insert another
            DiffOperation(DiffOperation.Type.KEEP, "c"),
            DiffOperation(DiffOperation.Type.INSERT, "z") // Insert at end
        )
        val diff = Diff(operations)
        val original = listOf("a", "b", "c")
        val result = diff.apply(original)
        assertEquals(listOf("w", "a", "x", "y", "c", "z"), result)
    }

    @Test
    fun testVarargConstructor() {
        val diff = Diff(
            DiffOperation(DiffOperation.Type.KEEP, "a"),
            DiffOperation(DiffOperation.Type.INSERT, "x"),
            DiffOperation(DiffOperation.Type.KEEP, "b")
        )
        val original = listOf("a", "b")
        val result = diff.apply(original)
        assertEquals(listOf("a", "x", "b"), result)
    }
}
