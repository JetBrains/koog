package ai.koog.agents.ext.tool.edit.diff.algorithm

import ai.koog.agents.ext.tool.edit.diff.DiffOperation
import ai.koog.agents.ext.tool.edit.diff.MyersDiffAlgorithm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MyersDiffAlgorithmTest {

    private val algorithm = MyersDiffAlgorithm<String>()

    @Test
    fun testIdenticalContent() {
        val content = listOf("Line 1", "Line 2", "Line 3")
        val result = algorithm.diff(content, content).operations

        assertEquals(content.size, result.size, "Result should have same number of operations as input lines")
        assertTrue(result.all { it.type == DiffOperation.Type.KEEP }, "All operations should be KEEP for identical content")
    }

    @Test
    fun testAddition() {
        val source = listOf("Line 1", "Line 3")
        val target = listOf("Line 1", "Line 2", "Line 3")
        val result = algorithm.diff(source, target).operations

        assertEquals(3, result.size, "Result should have 3 operations")
        assertEquals(DiffOperation.Type.KEEP, result[0].type, "First operation should be KEEP")
        assertEquals(DiffOperation.Type.INSERT, result[1].type, "Second operation should be INSERT")
        assertEquals("Line 2", result[1].value, "Inserted line should be 'Line 2'")
        assertEquals(DiffOperation.Type.KEEP, result[2].type, "Third operation should be KEEP")
    }

    @Test
    fun testRemoval() {
        val source = listOf("Line 1", "Line 2", "Line 3")
        val target = listOf("Line 1", "Line 3")
        val result = algorithm.diff(source, target).operations

        assertEquals(3, result.size, "Result should have 3 operations")
        assertEquals(DiffOperation.Type.KEEP, result[0].type, "First operation should be KEEP")
        assertEquals(DiffOperation.Type.DELETE, result[1].type, "Second operation should be DELETE")
        assertEquals("Line 2", result[1].value, "Deleted line should be 'Line 2'")
        assertEquals(DiffOperation.Type.KEEP, result[2].type, "Third operation should be KEEP")
    }

    @Test
    fun testModification() {
        val source = listOf("Line 1", "Line 2", "Line 3")
        val target = listOf("Line 1", "Modified Line 2", "Line 3")
        val result = algorithm.diff(source, target).operations

        // A modification is represented as a DELETE + INSERT around the changed line
        assertEquals(4, result.size, "Result should have 4 operations")
        assertEquals(DiffOperation.Type.KEEP, result[0].type)
        assertEquals(DiffOperation.Type.DELETE, result[1].type)
        assertEquals("Line 2", result[1].value)
        assertEquals(DiffOperation.Type.INSERT, result[2].type)
        assertEquals("Modified Line 2", result[2].value)
        assertEquals(DiffOperation.Type.KEEP, result[3].type)
    }

    @Test
    fun testAlgorithmId() {
        assertEquals("myers", algorithm.id, "Myers algorithm id should be 'myers'")
    }

    @Test
    fun testEmptyInputs() {
        val empty = emptyList<String>()
        val nonEmpty = listOf("A", "B")

        // Empty source, non-empty target
        var result = algorithm.diff(empty, nonEmpty).operations
        assertEquals(nonEmpty.size, result.size)
        assertTrue(result.all { it.type == DiffOperation.Type.INSERT })

        // Non-empty source, empty target
        result = algorithm.diff(nonEmpty, empty).operations
        assertEquals(nonEmpty.size, result.size)
        assertTrue(result.all { it.type == DiffOperation.Type.DELETE })

        // Both empty
        result = algorithm.diff(empty, empty).operations
        assertTrue(result.isEmpty())
    }

    @Test
    fun testCompletelyDifferentContent() {
        val source = listOf("A", "B", "C")
        val target = listOf("X", "Y", "Z")
        val result = algorithm.diff(source, target).operations

        assertEquals(
            source.size + target.size,
            result.size,
            "Result should have deletes for all source + inserts for all target"
        )
        assertEquals(source.size, result.count { it.type == DiffOperation.Type.DELETE })
        assertEquals(target.size, result.count { it.type == DiffOperation.Type.INSERT })
    }

    @Test
    fun testSingleElementReplace() {
        val source = listOf("A")
        val target = listOf("B")
        val result = algorithm.diff(source, target).operations

        // Expect: DELETE "A", INSERT "B"
        assertEquals(2, result.size, "Single-element change should be represented as delete+insert")
        assertEquals(DiffOperation.Type.DELETE, result[0].type)
        assertEquals("A", result[0].value)
        assertEquals(DiffOperation.Type.INSERT, result[1].type)
        assertEquals("B", result[1].value)
    }

    @Test
    fun testLongCommonPrefixSuffix() {
        val source = listOf("P1", "P2", "X1", "X2", "S1", "S2")
        val target = listOf("P1", "P2", "Y",  "X2", "S1", "S2")
        val result = algorithm.diff(source, target).operations

        // Prefix "P1","P2" and suffix "X2","S1","S2" should be KEEP
        // X1 -> delete, Y -> insert
        val types = result.map { it.type }
        val values = result.map { it.value }
        assertEquals(
            listOf(
                DiffOperation.Type.KEEP,
                DiffOperation.Type.KEEP,
                DiffOperation.Type.DELETE,
                DiffOperation.Type.INSERT,
                DiffOperation.Type.KEEP,
                DiffOperation.Type.KEEP,
                DiffOperation.Type.KEEP
            ),
            types
        )
        assertEquals(
            listOf("P1", "P2", "X1", "Y",  "X2", "S1", "S2"),
            values
        )
    }

    @Test
    fun testRepeatedElements() {
        val source = listOf("A", "B", "A", "B", "C")
        val target = listOf("A", "A", "B", "C", "C")
        val ops = algorithm.diff(source, target).operations

        // We expect 2 deletes + 2 inserts (total 4 edits).
        val edits = ops.count { it.type != DiffOperation.Type.KEEP }
        assertEquals(2, edits, "Should perform exactly 4 deletions+insertions")

        // Reconstruct the edited sequence by applying KEEP/INSERT, skipping DELETE
        val reconstructed = buildList<String> {
            for (op in ops) {
                when (op.type) {
                    DiffOperation.Type.KEEP,
                    DiffOperation.Type.INSERT -> add(op.value)
                    DiffOperation.Type.DELETE -> { /* skip */ }
                }
            }
        }
        assertEquals(target, reconstructed, "Applying the diff should yield the target sequence")
    }
}
