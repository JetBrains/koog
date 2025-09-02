package ai.koog.agents.file.tools

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
        tool.execute(ReadFileTool.Args(path.toString(), startLine, endLine), enabler)

    @Test
    fun `tool reads complete file successfully`() = runBlocking {
        val file = createTestFile("test.txt", "Hello, World!")
        val result = readFile(file)

        val text = assertIs<FileSystemEntry.File.Content.Text>(result.file.content)
        assertEquals("Hello, World!", text.text)
    }

    @Test
    fun `tool reads file excerpt with line range`() = runBlocking {
        val file = createTestFile("lines.txt", "line0\nline1\nline2\nline3")
        val result = readFile(file, startLine = 1, endLine = 3)

        val excerpt = assertIs<FileSystemEntry.File.Content.Excerpt>(result.file.content)
        assertEquals("line1\nline2\n", excerpt.snippets[0].text)
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
    fun `throws ValidationFailure for binary files`() {
        val binaryFile = tempDir.resolve("binary.dat").apply {
            createFile()
            writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        }
        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(binaryFile) }
        }
    }

    @Test
    fun `buildContent validates arguments`() {
        val file = createTestFile("valid.txt", "line1\nline2\nline3\nline4")

        assertThrows<ToolException.ValidationFailure>("startLine must be < the whole file lines count") {
            runBlocking { readFile(file, 10, -1) }
        }

        assertThrows<ToolException.ValidationFailure>("startLine must be >= 0") {
            runBlocking { readFile(file, -5, 2) }
        }

        assertThrows<ToolException.ValidationFailure>("endLine must be >= -1") {
            runBlocking { readFile(file, 0, -5) }
        }

        assertThrows<ToolException.ValidationFailure>("endLine must be > startLine") {
            runBlocking { readFile(file, 1, 1) }
        }
    }

    @Test
    fun `Args uses correct defaults`() {
        val args = ReadFileTool.Args("/test/path")
        assertEquals("/test/path", args.path)
        assertEquals(0, args.startLine)
        assertEquals(-1, args.endLine)
    }

    @Test
    fun `descriptor is configured correctly`() {
        val descriptor = ReadFileTool.descriptor

        assertEquals("__read_file__", descriptor.name)
        assertTrue(descriptor.description.isNotEmpty())

        assertEquals(1, descriptor.requiredParameters.size)
        assertEquals("path", descriptor.requiredParameters[0].name)

        assertEquals(2, descriptor.optionalParameters.size)
        val optionalNames = descriptor.optionalParameters.map { it.name }.toSet()
        assertEquals(setOf("startLine", "endLine"), optionalNames)
    }

    @Test
    fun `Result serializer is available`() = runBlocking {
        val file = createTestFile("test.txt", "content")
        val result = readFile(file)
        assertEquals(ReadFileTool.Result.serializer(), result.getSerializer())
    }

    @Test
    fun `Result toStringDefault formats output correctly`() = runBlocking {
        val file = createTestFile("format.txt", "test content")
        val result = readFile(file)

        val output = result.toStringDefault()
        assertTrue(output.contains(file.toAbsolutePath().toString()))
        assertTrue(output.contains("test content"))
        assertTrue(output.contains("1 line"))
    }
}
