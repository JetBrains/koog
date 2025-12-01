package ai.koog.agents.ext.tool.file

import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.ext.tool.file.render.norm
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalAgentToolsApi::class)
class WriteFileToolJvmTest {

    private val fs = JVMFileSystemProvider.ReadWrite
    private val tool = WriteFileTool(fs)

    @TempDir
    lateinit var tempDir: Path

    private suspend fun write(path: Path, content: String): WriteFileTool.Result =
        tool.execute(WriteFileTool.Args(path.toString(), content))

    @Test
    fun `descriptor is configured correctly`() {
        val descriptor = tool.descriptor
        assertEquals("__write_file__", descriptor.name)
        assertTrue(descriptor.description.isNotEmpty())
        assertEquals(listOf("path", "content"), descriptor.requiredParameters.map { it.name })
        assertTrue(descriptor.optionalParameters.isEmpty())
    }

    @Test
    fun `Args are passed correctly`() {
        val args = WriteFileTool.Args("/tmp/test.txt", "hello")
        assertEquals("/tmp/test.txt", args.path)
        assertEquals("hello", args.content)
    }

    @Test
    fun `throws ValidationFailure when content is empty`() {
        val p = tempDir.resolve("a/empty.txt")
        assertThrows<ToolException.ValidationFailure> {
            runBlocking { write(p, "") }
        }
    }

    @Test
    fun `writes new file creating parent directories and returns metadata`() = runBlocking {
        val p = tempDir.resolve("nested/dir/notes.md")

        val result = write(p, "hello world")

        assertTrue(p.exists())
        assertEquals("hello world", p.readText())
        // Expected:
        // Written
        // /tempDir/nested/dir/notes.md (<0.1 KiB, 1 line)
        val expected = listOf(
            "Written",
            "${p.toAbsolutePath().toString().norm()} (<0.1 KiB, 1 line)"
        ).joinToString("\n")
        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    fun `overwrites existing file content`() = runBlocking {
        val p = tempDir.resolve("file.txt")
        p.parent.createDirectories()
        write(p, "old content")

        val result = write(p, "new content")

        // Expected:
        // Written
        // /tempDir/file.txt (<0.1 KiB, 1 line)
        val expected = listOf(
            "Written",
            "${p.toAbsolutePath().toString().norm()} (<0.1 KiB, 1 line)"
        ).joinToString("\n")
        assertEquals(expected, tool.encodeResultToString(result))
        assertEquals("new content", p.readText())
    }

    @Test
    fun `writes multi-line content and renders absolute path`() = runBlocking {
        val p = tempDir.resolve("multi/lines.txt")
        val content = "first line\nsecond line with spaces  \nthird line"

        val result = write(p, content)

        // Expected:
        // Written
        // /tempDir/multi/lines.txt (<0.1 KiB, 3 lines)
        val expected = listOf(
            "Written",
            "${p.toAbsolutePath().toString().norm()} (<0.1 KiB, 3 lines)"
        ).joinToString("\n")
        assertEquals(expected, tool.encodeResultToString(result))
        assertTrue(p.exists())
        assertEquals(content, p.readText())
    }
}
