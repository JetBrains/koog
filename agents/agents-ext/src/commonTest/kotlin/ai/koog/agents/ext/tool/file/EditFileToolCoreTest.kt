package ai.koog.agents.ext.tool.file

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.rag.base.files.readText
import ai.koog.rag.base.files.writeText
import ai.koog.test.utils.InMemoryFS
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(InternalAgentToolsApi::class)
class EditFileToolCoreTest {

    @Test
    fun test_simple_edit_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)

        val path = "/project/Example.txt"
        mockedFS.writeText(path, "Hello World\n")

        // When
        val args = EditFileTool.Args(
            path = path,
            original = "World",
            replacement = "Koog"
        )
        tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertEquals("Hello Koog\n", updated)
    }

    @Test
    fun test_report_should_be_understandable_for_LLM() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)

        val path = "/project/Example.txt"
        mockedFS.writeText(path, "Hello World\n")

        // When
        val args = EditFileTool.Args(
            path = path,
            original = "World",
            replacement = "Koog"
        )
        val result = tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val markdownReport = result.toMarkdown()
        assertContains(markdownReport, "Success")
        assertContains(markdownReport, "edit")
    }

    @Test
    fun test_case_mismatching_original_is_applied() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/CaseNotes.txt"
        mockedFS.writeText(
            path,
            """
            |Line A
            |Hello World
            |Line C
            """.trimMargin() + "\n"
        )

        // When
        val args = EditFileTool.Args(
            path = path,
            original = "world", // different case than in file
            replacement = "Koog"
        )
        tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertEquals(
            """
            |Line A
            |Hello Koog
            |Line C
            """.trimMargin() + "\n",
            updated
        )
    }

    @Test
    fun test_original_with_extra_spaces_is_applied() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/SpaceNotes.txt"
        mockedFS.writeText(
            path,
            """
            |Line A
            |Hello World
            |Line C
            """.trimMargin() + "\n"
        )

        // When
        val args = EditFileTool.Args(
            path = path,
            original = "Hello    World", // more spaces than in file
            replacement = "Hello Koog"
        )
        tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertEquals(
            """
            |Line A
            |Hello Koog
            |Line C
            """.trimMargin() + "\n",
            updated
        )
    }

    @Test
    fun test_original_with_less_spaces_is_applied() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/SpaceNotes.txt"
        mockedFS.writeText(
            path,
            """
            |Line A
            |Hello    World
            |Line C
            """.trimMargin() + "\n"
        )

        // When
        val args = EditFileTool.Args(
            path = path,
            original = "Hello World", // less spaces than in file
            replacement = "Hello Koog"
        )
        tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertEquals(
            """
            |Line A
            |Hello Koog
            |Line C
            """.trimMargin() + "\n",
            updated
        )
    }

    @Test
    fun test_delete_operation_removes_line() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/DeleteNotes.txt"
        mockedFS.writeText(
            path,
            """
            |Line A
            |Hello World
            |Line C
            """.trimMargin() + "\n"
        )

        // When
        val args = EditFileTool.Args(
            path = path,
            original = "Hello World\n",
            replacement = ""
        )
        tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertEquals(
            """
            |Line A
            |Line C
            """.trimMargin(),
            updated.trim()
        )
    }

    @Test
    fun test_create_new_file_with_empty_original_and_nonexistent_path() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/new/Created.txt"
        assertEquals(false, mockedFS.exists(path))

        // When
        val newContent = "Hello World"
        val args = EditFileTool.Args(
            path = path,
            original = "",
            replacement = newContent
        )
        tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        assertEquals(true, mockedFS.exists(path))
        val updated = mockedFS.readText(path)
        assertEquals(newContent, updated)
    }

    @Test
    fun test_rewrite_existing_file_with_empty_original() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/Rewrite.txt"
        val originalContent = """
        |Old A
        |Old B
        |Old C
        """.trimMargin()
        mockedFS.writeText(path, originalContent)

        // When
        val replacementContent = """
        |New 1
        |New 2
        |New 3
        """.trimMargin()
        val args = EditFileTool.Args(
            path = path,
            original = "",
            replacement = replacementContent
        )
        tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertEquals(replacementContent, updated)
    }

    @Test
    fun test_original_not_found_reports_failure_and_noop_diff() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/NoMatch.txt"
        mockedFS.writeText(
            path,
            """
            |Alpha
            |Beta
            |Gamma
            """.trimMargin() + "\n"
        )

        val originalContentBefore = mockedFS.readText(path)

        // When
        val args = EditFileTool.Args(
            path = path,
            original = "Delta", // does not exist in file
            replacement = "Omega"
        )
        val result = tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val markdownReport = result.toMarkdown()
        assertFalse(markdownReport.contains("Successfully"), "Markdown should not indicate a successful edit")

        assertEquals(false, result.applied, "Patch should not be applied when original is not found")

        assertContains(
            markdownReport,
            "re-read",
            ignoreCase = true,
            message = "Markdown should contain a remark about re-reading the original file"
        )

        val contentAfter = mockedFS.readText(path)
        assertEquals(originalContentBefore, contentAfter)
    }
}
