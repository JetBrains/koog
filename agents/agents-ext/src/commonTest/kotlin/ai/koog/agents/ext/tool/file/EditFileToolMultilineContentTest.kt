package ai.koog.agents.ext.tool.file

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.rag.base.files.readText
import ai.koog.rag.base.files.writeText
import ai.koog.test.utils.InMemoryFS
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalAgentToolsApi::class)
class EditFileToolMultilineContentTest {

    @Test
    fun test_multiline_block_replace_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/js/Block.js"
        mockedFS.writeText(
            path,
            """
            |function test() {
            |    if (condition) {
            |        doSomething();
            |    }
            |    return result;
            |}
            """.trimMargin()
        )

        // When
        val original = """
            |    if (condition) {
            |        doSomething();
            |    }
        """.trimMargin()
        val replacement = """
            |    if (condition) {
            |        doSomethingElse();
            |        doExtra();
            |    }
        """.trimMargin()
        tool.execute(
            EditFileTool.Args(path = path, original = original, replacement = replacement),
            object : DirectToolCallsEnabler {}
        )

        // Then
        val updated = mockedFS.readText(path)
        assertEquals(
            """
            |function test() {
            |    if (condition) {
            |        doSomethingElse();
            |        doExtra();
            |    }
            |    return result;
            |}
            """.trimMargin(),
            updated
        )
    }

    @Test
    fun test_large_content_middle_replacement_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/large/Large.txt"
        val sb = StringBuilder()
        repeat(1000) { sb.appendLine("// Line $it") }
        sb.appendLine("const target = 42;")
        repeat(1000) { sb.appendLine("// Line ${it + 1000}") }
        mockedFS.writeText(path, sb.toString().removeSuffix("\n"))

        // When
        val args = EditFileTool.Args(path = path, original = "const target = 42", replacement = "const target = 43")
        tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertTrue(updated.contains("const target = 43"))
    }

    @Test
    fun test_start_of_file_replacement_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/bounds/Start.txt"
        mockedFS.writeText(path, "const x = 42\nrest of code")

        // When
        tool.execute(EditFileTool.Args(path = path, original = "const x", replacement = "let x"), object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertEquals("let x = 42\nrest of code", updated)
    }

    @Test
    fun test_end_of_file_replacement_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/bounds/End.txt"
        mockedFS.writeText(path, "code;\nreturn 42")

        // When
        tool.execute(EditFileTool.Args(path = path, original = "return 42", replacement = "return 43"), object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertEquals("code;\nreturn 43", updated)
    }

    @Test
    fun test_consecutive_edits_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/seq/TwoEdits.js"
        mockedFS.writeText(path, "function test() { const x = 42; const y = 43; }")

        // When
        tool.execute(
            EditFileTool.Args(path = path, original = "const x = 42", replacement = "const x = 142"),
            object : DirectToolCallsEnabler {}
        )

        tool.execute(
            EditFileTool.Args(path = path, original = "const y = 43", replacement = "const y = 143"),
            object : DirectToolCallsEnabler {}
        )

        // Then
        val updated = mockedFS.readText(path)
        assertEquals("function test() { const x = 142; const y = 143; }", updated)
    }
}
