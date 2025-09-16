package ai.koog.agents.ext.tool.file

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.ext.utils.InMemoryFS
import ai.koog.rag.base.files.readText
import ai.koog.rag.base.files.writeText
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalAgentToolsApi::class)
class EditFileToolFormattingEdgeCasesTest {

    @Test
    fun test_tabs_vs_spaces_equivalent_original_is_applied() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/indent/Sample.js"
        mockedFS.writeText(
            path,
            """
            function test() {
            	if (true) {  // Tab indentation
            	    return 42;  // Mixed tab and spaces
            	}
            }
            """.trimIndent()
        )

        // When
        val args = EditFileTool.Args(
            path = path,
            original = "        return 42",
            replacement = "\t    return 43"
        )
        tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertEquals(
            """
            function test() {
            	if (true) {  // Tab indentation
            	    return 43;  // Mixed tab and spaces
            	}
            }
            """.trimIndent(),
            updated
        )
    }

    @Test
    fun test_crlf_vs_lf_equivalent_original_is_applied() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/project/eol/Mixed.txt"
        val content = "line1\nline2\r\nline3\rline4"
        mockedFS.writeText(path, content)

        // When
        val args = EditFileTool.Args(
            path = path,
            original = "line2\nline3",
            replacement = "newline\nline3"
        )
        tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertEquals("line1\nnewline\nline3\rline4", updated)
    }
}
