package ai.koog.agents.ext.tool.file

import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.ext.tool.file.render.norm
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

@OptIn(InternalAgentToolsApi::class)
class ReadFileToolJvmTest {

    private val fs = JVMFileSystemProvider.ReadOnly
    private val tool = ReadFileTool(fs)

    @TempDir
    lateinit var tempDir: Path

    private fun createTestFile(name: String, content: String = ""): Path =
        tempDir.resolve(name).createFile().apply { writeText(content) }

    private suspend fun readFile(path: Path, startLine: Int = 0, endLine: Int = -1): ReadFileTool.Result =
        tool.execute(ReadFileTool.Args(path.toString(), startLine, endLine))

    @Test
    fun `Args uses correct defaults`() {
        val args = ReadFileTool.Args("/tmp/test.txt")
        assertEquals("/tmp/test.txt", args.path)
        assertEquals(0, args.startLine)
        assertEquals(-1, args.endLine)
    }

    @Test
    fun `descriptor is configured correctly`() {
        val descriptor = tool.descriptor
        assertEquals("__read_file__", descriptor.name)
        assertTrue(descriptor.description.isNotEmpty())
        assertEquals(listOf("path"), descriptor.requiredParameters.map { it.name })
        assertEquals(setOf("startLine", "endLine"), descriptor.optionalParameters.map { it.name }.toSet())
    }

    @Test
    fun `throws ValidationFailure for non-existent path`() {
        val missing = tempDir.resolve("missing.txt")
        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(missing) }
        }
    }

    @Test
    fun `throws ValidationFailure when path points to a directory`() {
        val dir = tempDir.resolve("dir").createDirectories()
        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(dir) }
        }
    }

    @Test
    fun `throws ValidationFailure when file is not a text file`() {
        val bin = tempDir.resolve("bin.dat").createFile().apply { writeBytes(byteArrayOf(0x00, 0xFF.toByte())) }
        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(bin) }
        }
    }

    @Test
    fun `full file render shows path size and line count and full text`() = runBlocking {
        val f = createTestFile("notes.md", "hello\nworld")

        val result = readFile(f)

        val expected = """
            ${"${f.toAbsolutePath().toString().norm()} (<0.1 KiB, 2 lines)"}
            Content:
            ```markdown
            hello
            world
            ```
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    fun `excerpt render shows header with lines and excerpt section`() = runBlocking {
        val f = createTestFile(
            "code.kt",
            """
            fun a() {}
            fun b() {}
            fun c() {}
            """.trimIndent()
        )

        val result = readFile(f, startLine = 1, endLine = 3)

        val expected = """
            ${"${f.toAbsolutePath().toString().norm()} (<0.1 KiB, 3 lines)"}
            Excerpt:
            Lines 1-3:
            ```kotlin
            fun b() {}
            fun c() {}
            ```
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    fun `whole-file explicit range renders full text not excerpt`() = runBlocking {
        val f = createTestFile("todo.txt", "a\nb\nc")

        val result = readFile(f, startLine = 0, endLine = 3)

        val expected = """
            ${"${f.toAbsolutePath().toString().norm()} (<0.1 KiB, 3 lines)"}
            Content:
            a
            b
            c
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    fun `language mapping renders yaml fence for yml`() = runBlocking {
        val f = createTestFile("config.yml", "a: 1\nb: 2")
        val result = readFile(f)
        val expected = """
            ${"${f.toAbsolutePath().toString().norm()} (<0.1 KiB, 2 lines)"}
            Content:
            ```yaml
            a: 1
            b: 2
            ```
        """.trimIndent()
        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    fun `language mapping handles uppercase extension for python`() = runBlocking {
        val f = createTestFile("SCRIPT.PY", "print('hi')")
        val result = readFile(f)
        val expected = """
            ${"${f.toAbsolutePath().toString().norm()} (<0.1 KiB, 1 line)"}
            Content:
            ```python
            print('hi')
            ```
        """.trimIndent()
        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    fun `language mapping maps ps1 to powershell`() = runBlocking {
        val f = createTestFile("run.PS1", "Write-Host 'hello'")
        val result = readFile(f)
        val expected = """
            ${"${f.toAbsolutePath().toString().norm()} (<0.1 KiB, 1 line)"}
            Content:
            ```powershell
            Write-Host 'hello'
            ```
        """.trimIndent()
        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    fun `language mapping maps gradle to groovy`() = runBlocking {
        val f = createTestFile("build.gradle", "task hello { }")
        val result = readFile(f)
        val expected = """
            ${"${f.toAbsolutePath().toString().norm()} (<0.1 KiB, 1 line)"}
            Content:
            ```groovy
            task hello { }
            ```
        """.trimIndent()
        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    fun `language mapping maps bat to batch`() = runBlocking {
        val f = createTestFile("RUN.BAT", "echo hello")
        val result = readFile(f)
        val expected = """
            ${"${f.toAbsolutePath().toString().norm()} (<0.1 KiB, 1 line)"}
            Content:
            ```batch
            echo hello
            ```
        """.trimIndent()
        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    fun `throws ValidationFailure when reading empty markdown file`() {
        val f = createTestFile("empty.md", "")
        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(f) }
        }
    }

    @Test
    fun `excerpt with endLine -1 clamps to EOF and is fenced`() = runBlocking {
        val f = createTestFile(
            "main.kt",
            """
            fun a() = 1
            fun b() = 2
            fun c() = 3
            """.trimIndent()
        )
        val result = readFile(f, startLine = 1, endLine = -1)
        val expected = """
            ${"${f.toAbsolutePath().toString().norm()} (<0.1 KiB, 3 lines)"}
            Excerpt:
            Lines 1-3:
            ```kotlin
            fun b() = 2
            fun c() = 3
            ```
        """.trimIndent()
        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    fun `file with only newline characters renders empty fenced block with correct line count`() = runBlocking {
        val f = createTestFile("blank.md", "\n\n\n")
        val result = readFile(f)
        val expected = """
            ${"${f.toAbsolutePath().toString().norm()} (<0.1 KiB, 4 lines)"}
            Content:
            ```markdown
            ```
        """.trimIndent()
        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    fun `throws ValidationFailure when reading empty kotlin file`() {
        val f = createTestFile("Empty.kt", "")
        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(f) }
        }
    }

    @Test
    fun `throws ValidationFailure for endLine less than startLine`() {
        val f = createTestFile("a.txt", "x\ny\nz")
        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(f, startLine = 2, endLine = 1) }
        }
    }

    @Test
    fun `throws ValidationFailure when startLine equals total lines`() {
        val f = createTestFile("lines.txt", "a\nb\nc")
        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(f, startLine = 3, endLine = -1) }
        }
    }

    @Test
    fun `throws ValidationFailure when endLine equals startLine`() {
        val f = createTestFile("same.txt", "a\nb\nc")
        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(f, startLine = 1, endLine = 1) }
        }
    }

    @Test
    fun `throws ValidationFailure when endLine equals startLine at zero`() {
        val f = createTestFile("zero.txt", "only one line")
        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(f, startLine = 0, endLine = 0) }
        }
    }

    @Test
    fun `throws ValidationFailure for startLine beyond file`() {
        val f = createTestFile("beyond.txt", "l1\nl2")
        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(f, startLine = 5, endLine = -1) }
        }
    }

    @Test
    fun `throws ValidationFailure for invalid endLine less than -1`() {
        val f = createTestFile("neg.txt", "content")
        assertThrows<ToolException.ValidationFailure> {
            runBlocking { readFile(f, startLine = 0, endLine = -2) }
        }
    }

    @Test
    fun `includes warning when endLine exceeds file length`() = runBlocking {
        val f = createTestFile("short.txt", "line1\nline2\nline3")

        val result = readFile(f, startLine = 0, endLine = 200)

        val expected = """
            Warning: endLine=200 exceeds file length (3 lines). Clamped to available lines 0-3.
            ${"${f.toAbsolutePath().toString().norm()} (<0.1 KiB, 3 lines)"}
            Content:
            line1
            line2
            line3
        """.trimIndent()
        assertEquals(expected, tool.encodeResultToString(result))
    }
}
