package ai.koog.agents.ext.tool.edit

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.ext.tool.edit.testutil.InMemoryFS
import ai.koog.rag.base.files.readText
import ai.koog.rag.base.files.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

@OptIn(InternalAgentToolsApi::class)
class EditFileToolSpecialContentTest {

    @Test
    fun test_unicode_characters_handled_correctly_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/i18n/Unicode.txt"
        mockedFS.writeText(
            path,
            (
                """
                |function test() {
                |    // Unicode symbols: 🚀 💻 🔧
                |    const greeting = "Hello, 世界!";
                |    return "こんにちは";
                |}
                |""".trimMargin()
            )
        )

        // When
        tool.execute(
            EditFileTool.Args(path = path, original = "Hello, 世界!", replacement = "Bonjour, 世界!"),
            object : DirectToolCallsEnabler {}
        )

        tool.execute(
            EditFileTool.Args(path = path, original = "こんにちは", replacement = "さようなら"),
            object : DirectToolCallsEnabler {}
        )

        // Then
        val updated = mockedFS.readText(path)
        assertEquals("""
                |function test() {
                |    // Unicode symbols: 🚀 💻 🔧
                |    const greeting = "Bonjour, 世界!";
                |    return "さようなら";
                |}
                |""".trimMargin(),
            updated
        )
    }

    @Test
    fun test_escape_sequences_handled_correctly_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/escapes/Escapes.js"
        mockedFS.writeText(path, "const str = \"Hello\\n\\t\\\"World\\\"\"")

        // When
        tool.execute(
            EditFileTool.Args(path = path, original = "\\n\\t", replacement = "\\n"),
            object : DirectToolCallsEnabler {}
        )

        tool.execute(
            EditFileTool.Args(path = path, original = "World", replacement = "Universe"),
            object : DirectToolCallsEnabler {}
        )

        // Then
        val updated = mockedFS.readText(path)
        assertEquals("const str = \"Hello\\n\\\"Universe\\\"\"", updated)
    }

    @Test
    fun test_regex_special_characters_handled_correctly_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/regex/Regex.js"
        mockedFS.writeText(path, "function test() { return /^\\\\d+$/; }")

        // When
        tool.execute(
            EditFileTool.Args(path = path, original = "/^\\\\d+$/", replacement = "/^[0-9]+$/"),
            object : DirectToolCallsEnabler {}
        )

        // Then
        val updated = mockedFS.readText(path)
        assertEquals("function test() { return /^[0-9]+$/; }", updated)
    }

    @Test
    fun test_escaped_special_characters_handled_correctly_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/regex/Escaped.js"
        mockedFS.writeText(path, "const pattern = \"\\\\d+\";")

        // When
        tool.execute(
            EditFileTool.Args(path = path, original = "\\\\d+", replacement = "[0-9]+"),
            object : DirectToolCallsEnabler {}
        )

        // Then
        val updated = mockedFS.readText(path)
        assertEquals("const pattern = \"[0-9]+\";", updated)
    }

    @Test
    fun test_comments_with_patch_like_text_handled_correctly_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/comments/PatchLike.js"
        mockedFS.writeText(
            path,
            (
                """
                |// TODO: Replace "return 42" with "return 43"
                |function test() {
                |    return 42;  // Will be replaced
                |}
                |""".trimMargin()
            )
        )

        // When
        tool.execute(
            EditFileTool.Args(path = path, original = "return 42", replacement = "return 43"),
            object : DirectToolCallsEnabler {}
        )

        // Then
        val updated = mockedFS.readText(path)
        assertTrue(updated.contains("// TODO: Replace \"return 43\" with \"return 43\""))
        assertTrue(updated.contains("return 43"))
    }
}
