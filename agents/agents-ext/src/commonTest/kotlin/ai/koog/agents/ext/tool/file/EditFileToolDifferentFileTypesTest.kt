package ai.koog.agents.ext.tool.file

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.rag.base.files.readText
import ai.koog.rag.base.files.writeText
import ai.koog.test.utils.InMemoryFS
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalAgentToolsApi::class)
class EditFileToolDifferentFileTypesTest {

    @Test
    fun test_python_edit_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/repo/module/test_sample.py"
        mockedFS.writeText(
            path,
            """
            |def test():
            |    print("debug")
            |    x = 42
            |    return x
            """.trimMargin()
        )

        // When
        val args = EditFileTool.Args(
            path = path,
            original = "print(\"debug\")\n    ",
            replacement = ""
        )
        tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertEquals(
            """
            def test():
                x = 42
                return x
            """.trimIndent(),
            updated.trim()
        )
    }

    @Test
    fun test_json_edit_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/repo/config/settings.json"
        mockedFS.writeText(path, """{"key": "value", "number": 42}""")

        // When
        val args = EditFileTool.Args(
            path = path,
            original = "\"value\"",
            replacement = "\"newValue\""
        )
        tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertEquals("""{"key": "newValue", "number": 42}""", updated)
    }

    @Test
    fun test_xml_edit_via_tool() = runTest {
        // Given
        val mockedFS = InMemoryFS()
        val tool = EditFileTool(mockedFS)
        val path = "/repo/resources/sample.xml"
        mockedFS.writeText(path, "<root><item>value</item></root>")

        // When
        val args = EditFileTool.Args(
            path = path,
            original = "<item>value</item>",
            replacement = "<item>newValue</item>"
        )
        tool.execute(args, object : DirectToolCallsEnabler {})

        // Then
        val updated = mockedFS.readText(path)
        assertEquals("<root><item>newValue</item></root>", updated)
    }
}
