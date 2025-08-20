package ai.koog.agents.file.tools

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(InternalAgentToolsApi::class)
class ReadFileToolJvmTest {

    private val fs = JVMFileSystemProvider.ReadOnly
    private val enabler = object : DirectToolCallsEnabler {}
    private val tool = ReadFileTool(fs)

    @TempDir
    lateinit var tempDir: Path

    private fun createTestFile(name: String, content: String = ""): Path =
        tempDir.resolve(name).createFile().apply { writeText(content) }

    private suspend fun readFile(path: Path, startLine: Int = 0, endLine: Int = -1): ReadFileTool.Result =
        tool.executeUnsafe(ReadFileTool.Args(path.toString(), startLine, endLine), enabler)

    @Test
    fun `reads entire file content successfully`() = runBlocking {
        val file = createTestFile("simple.txt", "Hello, World!")

        val result = readFile(file)

        assertTrue(result.file.content is FileSystemEntry.File.Content.Text)
        assertEquals("Hello, World!", result.file.content.text)
        assertEquals("simple.txt", result.file.name)
        assertEquals("txt", result.file.extension)
    }

    @Test
    fun `reads empty file successfully`() = runBlocking {
        val file = createTestFile("empty.txt", "")

        val result = readFile(file)

        assertTrue(result.file.content is FileSystemEntry.File.Content.Text)
        assertEquals("", result.file.content.text)
        assertEquals("empty.txt", result.file.name)
    }

    @Test
    fun `reads file without extension`() = runBlocking {
        val file = createTestFile("next", "content without extension")

        val result = readFile(file)

        assertEquals("next", result.file.name)
        assertEquals(null, result.file.extension)
        assertEquals("content without extension", (result.file.content as FileSystemEntry.File.Content.Text).text)
    }

    @Test
    fun `identifies hidden files correctly`() = runBlocking {
        val file = createTestFile(".hidden", "secret content")

        val result = readFile(file)

        assertTrue(result.file.hidden)
        assertEquals(".hidden", result.file.name)
        assertEquals("secret content", (result.file.content as FileSystemEntry.File.Content.Text).text)
    }

    @Test
    fun `reads specific line range correctly`() = runBlocking {
        val content = "line0\nline1\nline2\nline3\nline4"
        val file = createTestFile("lines.txt", content)

        val result = readFile(file, startLine = 1, endLine = 4)

        assertTrue(result.file.content is FileSystemEntry.File.Content.Excerpt)
        assertEquals("line1\nline2\nline3\n", result.file.content.snippets[0].text)
    }

    @Test
    fun `reads from start line to end when endLine is -1`() = runBlocking {
        val file = createTestFile("partial.txt", "start\nmiddle\nend")

        val result = readFile(file, startLine = 0, endLine = -1)

        assertTrue(result.file.content is FileSystemEntry.File.Content.Text)
        assertEquals("start\nmiddle\nend", result.file.content.text)
    }

    @Test
    fun `reads single line correctly`() = runBlocking {
        val file = createTestFile("multiline.txt", "first\nsecond\nthird")

        val result = readFile(file, startLine = 1, endLine = 2)

        assertTrue(result.file.content is FileSystemEntry.File.Content.Excerpt)
        assertEquals("second\n", result.file.content.snippets[0].text)
    }

    @Test
    fun `returns full content when endLine exceeds file length`() = runBlocking {
        val file = createTestFile("short.txt", "line1\nline2\nline3")

        val result = readFile(file, startLine = 0, endLine = 100)

        assertTrue(result.file.content is FileSystemEntry.File.Content.Text)
        assertEquals("line1\nline2\nline3", result.file.content.text)
    }

    @Test
    fun `returns empty content when startLine exceeds file length`() = runBlocking {
        val file = createTestFile("short.txt", "line1\nline2")

        val result = readFile(file, startLine = 10, endLine = -1)

        assertTrue(result.file.content is FileSystemEntry.File.Content.Excerpt)
        assertEquals("", result.file.content.snippets[0].text)
    }

    @Test
    fun `handles unicode content correctly`() = runBlocking {
        val file = createTestFile("unicode.txt", "café ñoño 中文 🚀")

        val result = readFile(file)

        assertEquals("café ñoño 中文 🚀", (result.file.content as FileSystemEntry.File.Content.Text).text)
    }

    @Test
    fun `handles mixed line endings`() = runBlocking {
        val file = createTestFile("mixed.txt", "line1\r\nline2\nline3\r")

        val result = readFile(file, startLine = 1, endLine = 2)

        assertTrue(result.file.content is FileSystemEntry.File.Content.Excerpt)
        assertEquals("line2\n", result.file.content.snippets[0].text)
    }

    @Test
    fun `handles whitespace-only content`() = runBlocking {
        val file = createTestFile("whitespace.txt", "   \n  \n\t\n")

        val result = readFile(file, startLine = 1, endLine = 3)

        assertTrue(result.file.content is FileSystemEntry.File.Content.Excerpt)
        assertEquals("  \n\t\n", result.file.content.snippets[0].text)
    }

    @Test
    fun `handles file with only newlines`() = runBlocking {
        val file = createTestFile("newlines.txt", "\n\n\n")

        val result = readFile(file)

        assertEquals("\n\n\n", (result.file.content as FileSystemEntry.File.Content.Text).text)
    }

    @Test
    fun `handles single character file`() = runBlocking {
        val file = createTestFile("single.txt", "x")

        val result = readFile(file)

        assertEquals("x", (result.file.content as FileSystemEntry.File.Content.Text).text)
    }

    @Test
    fun `handles file with special characters in name`() = runBlocking {
        val file = createTestFile("special@#$%.txt", "special content")

        val result = readFile(file)

        assertEquals("special@#$%.txt", result.file.name)
        assertEquals("special content", (result.file.content as FileSystemEntry.File.Content.Text).text)
    }

    @Test
    fun `handles tabs and indentation`() = runBlocking {
        val file = createTestFile("tabs.txt", "\t\tindented\n    spaces")

        val result = readFile(file, startLine = 0, endLine = 1)

        assertTrue(result.file.content is FileSystemEntry.File.Content.Excerpt)
        assertEquals("\t\tindented\n", result.file.content.snippets[0].text)
    }

    @Test
    fun `throws ValidationFailure for non-existent file`() {
        val nonExistent = tempDir.resolve("missing.txt")

        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(nonExistent) }
        }
    }

    @Test
    fun `throws ValidationFailure for directory path`() {
        val dir = tempDir.resolve("directory").createDirectories()

        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(dir) }
        }
    }

    @Test
    fun `throws IllegalArgumentException for negative startLine`() {
        val file = createTestFile("valid.txt", "content")

        assertThrows<IllegalArgumentException> {
            runBlocking { readFile(file, startLine = -1) }
        }
    }

    @Test
    fun `throws IllegalArgumentException for invalid endLine`() {
        val file = createTestFile("valid.txt", "content")

        assertThrows<IllegalArgumentException> {
            runBlocking { readFile(file, startLine = 0, endLine = -5) }
        }
    }

    @Test
    fun `throws IllegalArgumentException when endLine less than startLine`() {
        val file = createTestFile("valid.txt", "line1\nline2\nline3")

        assertThrows<IllegalArgumentException> {
            runBlocking { readFile(file, startLine = 2, endLine = 1) }
        }
    }

    @Test
    fun `throws IllegalArgumentException when startLine equals endLine`() {
        val file = createTestFile("valid.txt", "line1\nline2\nline3")

        assertThrows<IllegalArgumentException> {
            runBlocking { readFile(file, startLine = 1, endLine = 1) }
        }
    }

    @Test
    fun `Args uses correct default values`() {
        val args = ReadFileTool.Args("/test/path")

        assertEquals("/test/path", args.path)
        assertEquals(0, args.startLine)
        assertEquals(-1, args.endLine)
    }

    @Test
    fun `Args accepts custom values`() {
        val args = ReadFileTool.Args("/test/path", startLine = 5, endLine = 10)

        assertEquals("/test/path", args.path)
        assertEquals(5, args.startLine)
        assertEquals(10, args.endLine)
    }

    @Test
    fun `Result provides correct serializer`() = runBlocking {
        val file = createTestFile("serialize.txt", "content")
        val result = readFile(file)

        assertEquals(ReadFileTool.Result.serializer(), result.getSerializer())
    }

    @Test
    fun `Result toStringDefault produces exact expected format`() = runBlocking {
        val file = createTestFile("format.txt", "test content")
        val result = readFile(file)

        val stringRepresentation = result.toStringDefault()
        val expectedString = "${file.toAbsolutePath()} (<0.1 KiB, 1 line)\nContent:\ntest content"
        assertEquals(expectedString, stringRepresentation)
    }

    @Test
    fun `descriptor has correct configuration`() {
        val descriptor = ReadFileTool.descriptor

        assertEquals("read-file", descriptor.name)
        assertTrue(descriptor.description.isNotEmpty())

        assertEquals(1, descriptor.requiredParameters.size)
        assertEquals("path", descriptor.requiredParameters[0].name)

        assertEquals(2, descriptor.optionalParameters.size)
        val optionalParamNames = descriptor.optionalParameters.map { it.name }
        assertTrue(optionalParamNames.contains("startLine"))
        assertTrue(optionalParamNames.contains("endLine"))
    }

    @Test
    fun `descriptor parameters have correct types and descriptions`() {
        val descriptor = ReadFileTool.descriptor

        val pathParam = descriptor.requiredParameters.find { it.name == "path" }
        assertNotNull(pathParam)
        assertTrue(pathParam.description.isNotEmpty())

        val startLineParam = descriptor.optionalParameters.find { it.name == "startLine" }
        assertNotNull(startLineParam)
        assertTrue(startLineParam.description.isNotEmpty())

        val endLineParam = descriptor.optionalParameters.find { it.name == "endLine" }
        assertNotNull(endLineParam)
        assertTrue(endLineParam.description.isNotEmpty())
    }

    @Test
    fun `handles extremely large line numbers`() = runBlocking {
        val file = createTestFile("small.txt", "only one line")

        val result = readFile(file, startLine = 1000, endLine = 2000)

        assertTrue(result.file.content is FileSystemEntry.File.Content.Excerpt)
        val excerpt = result.file.content
        assertEquals("", excerpt.snippets[0].text)
    }

    @Test
    fun `handles zero-byte file with line range`() = runBlocking {
        val file = createTestFile("zero.txt", "")

        val result = readFile(file, startLine = 0, endLine = 1)

        assertTrue(result.file.content is FileSystemEntry.File.Content.Text)
        assertEquals("", result.file.content.text)
    }

    @Test
    fun `handles file ending with multiple newlines`() = runBlocking {
        val file = createTestFile("trailing.txt", "content\n\n\n")

        val result = readFile(file, startLine = 1, endLine = -1)

        assertTrue(result.file.content is FileSystemEntry.File.Content.Excerpt)
        val excerpt = result.file.content
        assertEquals("\n\n", excerpt.snippets[0].text)
    }

    @Test
    fun `handles very long single line`() = runBlocking {
        val longLine = "x".repeat(1000)
        val file = createTestFile("long.txt", longLine)

        val result = readFile(file)

        assertEquals(longLine, (result.file.content as FileSystemEntry.File.Content.Text).text)
    }

    @Test
    fun `handles binary-like content as text`() = runBlocking {
        val file = createTestFile("binary.txt", "\u0000\u0001\u0002text")

        val result = readFile(file)

        assertEquals("\u0000\u0001\u0002text", (result.file.content as FileSystemEntry.File.Content.Text).text)
    }
}
