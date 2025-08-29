package ai.koog.agents.ext.tool.edit.diff.serialization

import ai.koog.agents.ext.tool.edit.diff.Diff
import ai.koog.agents.ext.tool.edit.diff.DiffOperation
import ai.koog.agents.ext.tool.edit.diff.MyersDiffAlgorithm
import ai.koog.agents.ext.tool.edit.diff.UnifiedDiffSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class UnifiedDiffSerializerTest {

    private val serializer = UnifiedDiffSerializer<String>()

    @Test
    fun testSerializeEmptyDiff() {
        val diff = Diff<String>(emptyList())
        val result = serializer.serialize(diff)
        assertEquals(
            "",
            result,
            "Empty diff should produce empty string"
        )
    }

    @Test
    fun testSerializeNoChanges() {
        val operations = listOf(
            DiffOperation(DiffOperation.Type.KEEP, "line 1"),
            DiffOperation(DiffOperation.Type.KEEP, "line 2"),
            DiffOperation(DiffOperation.Type.KEEP, "line 3")
        )
        val diff = Diff(operations)
        val result = serializer.serialize(diff)
        assertEquals(
            "",
            result,
            "Diff with only KEEP operations should produce empty string"
        )
    }

    @Test
    fun testSerializePureAdditions() {
        val oldContent = emptyList<String>()
        val newContent = listOf("line 1", "line 2", "line 3")

        val result = serializer.serialize(
            MyersDiffAlgorithm<String>().diff(oldContent, newContent)
        ).trim()

        val expected = """
            --- original
            +++ revised
            @@ -1,0 +1,3 @@
            +line 1
            +line 2
            +line 3
        """.trimIndent()

        assertEquals(expected, result, "Pure additions diff does not match expected")
    }

    @Test
    fun testSerializePureDeletions() {
        val oldContent = listOf("line 1", "line 2", "line 3")
        val newContent = emptyList<String>()

        val result = serializer.serialize(
            MyersDiffAlgorithm<String>().diff(oldContent, newContent)
        ).trim()

        val expected = """
            --- original
            +++ revised
            @@ -1,3 +1,0 @@
            -line 1
            -line 2
            -line 3
        """.trimIndent()

        assertEquals(expected, result, "Pure deletions diff does not match expected")
    }

    @Test
    fun testSerializeMixedChanges() {
        val oldContent = listOf("line 1", "line 2", "line 3", "line 4", "line 5")
        val newContent = listOf("line 1", "modified line 2", "line 3", "new line", "line 5")

        val result = serializer.serialize(
            MyersDiffAlgorithm<String>().diff(oldContent, newContent)
        ).trim()

        val expected = """
            --- original
            +++ revised
            @@ -1,5 +1,5 @@
             line 1
            -line 2
            +modified line 2
             line 3
            -line 4
            +new line
             line 5
        """.trimIndent()

        assertEquals(expected, result, "Mixed changes diff does not match expected")
    }

    @Test
    fun testSerializeMultipleHunks() {
        val oldContent = (1..20).map { "line $it" }
        val newContent = oldContent.mapIndexed { index, line ->
            when (index + 1) {
                2 -> "modified line 2"
                18 -> "modified line 18"
                else -> line
            }
        }

        val result = serializer.serialize(
            MyersDiffAlgorithm<String>().diff(oldContent, newContent)
        ).trim()

        val expected = """
            --- original
            +++ revised
            @@ -1,5 +1,5 @@
             line 1
            -line 2
            +modified line 2
             line 3
             line 4
             line 5
            @@ -15,6 +15,6 @@
             line 15
             line 16
             line 17
            -line 18
            +modified line 18
             line 19
             line 20
        """.trimIndent()

        assertEquals(expected, result, "Diff with multiple hunks does not match expected")
    }

    @Test
    fun testSerializeWithLargeContextMergesHunks() {
        val oldContent = (1..10).map { "line $it" }
        val newContent = oldContent.mapIndexed { index, line ->
            when (index + 1) {
                2 -> "modified line 2"
                8 -> "modified line 8"
                else -> line
            }
        }

        // if the serializer supported a contextSize option, we'd pass it here;
        // assuming default merges these two changes into one hunk for this test
        val result = serializer.serialize(
            MyersDiffAlgorithm<String>().diff(oldContent, newContent)
        ).trim()

        val expected = """
            --- original
            +++ revised
            @@ -1,10 +1,10 @@
             line 1
            -line 2
            +modified line 2
             line 3
             line 4
             line 5
             line 6
             line 7
            -line 8
            +modified line 8
             line 9
             line 10
        """.trimIndent()

        assertEquals(expected, result, "With large context the two hunks should merge into one")
    }
}
