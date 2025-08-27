package ai.koog.agents.file.tools

import ai.koog.agents.file.tools.model.FileSize
import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadFileUtilJvmTest {

    private val fs = JVMFileSystemProvider.ReadOnly

    @TempDir
    lateinit var tempDir: Path

    private fun createTestFile(name: String, content: String = ""): Path =
        tempDir.resolve(name).createFile().apply { writeText(content) }

    @Test
    fun `extractExtension extracts standard extensions`() {
        assertEquals("txt", extractExtension("file.txt"))
        assertEquals("java", extractExtension("Main.java"))
        assertEquals("kt", extractExtension("MyClass.kt"))
        assertEquals("json", extractExtension("config.json"))
    }

    @Test
    fun `extractExtension returns null for files without extension`() {
        assertNull(extractExtension("README"))
        assertNull(extractExtension("Makefile"))
        assertNull(extractExtension("file"))
        assertNull(extractExtension("file."))
    }

    @Test
    fun `extractExtension handles hidden files`() {
        assertNull(extractExtension(".hidden"))
        assertNull(extractExtension(".bashrc"))
        assertEquals("json", extractExtension(".config.json"))
        assertEquals("txt", extractExtension(".hidden.txt"))
    }

    @Test
    fun `extractExtension handles multiple dots by taking last extension`() {
        assertEquals("gz", extractExtension("archive.tar.gz"))
        assertEquals("js", extractExtension("script.min.js"))
        assertEquals("backup", extractExtension("file.txt.backup"))
    }

    @Test
    fun `buildContent returns Text for complete file`() {
        val content = "line1\nline2\nline3"

        assertIs<FileSystemEntry.File.Content.Text>(buildContent(content, 0, -1))
        assertIs<FileSystemEntry.File.Content.Text>(buildContent(content, 0, 3))
        assertIs<FileSystemEntry.File.Content.Text>(buildContent(content, 0, 100))
    }

    @Test
    fun `buildContent returns Excerpt for partial range`() {
        val content = "line0\nline1\nline2\nline3"

        val result = buildContent(content, 1, 3) as FileSystemEntry.File.Content.Excerpt

        assertEquals(1, result.snippets.size)
        assertEquals("line1\nline2\n", result.snippets[0].text)
        assertEquals(DocumentProvider.Position(1, 0), result.snippets[0].range.start)
        assertEquals(DocumentProvider.Position(3, 0), result.snippets[0].range.end)
    }

    @Test
    fun `buildContent handles startLine beyond file length`() {
        val content = "line1\nline2"

        val result = buildContent(content, 10, -1) as FileSystemEntry.File.Content.Excerpt

        assertEquals("", result.snippets[0].text)
        assertEquals(DocumentProvider.Position(2, 0), result.snippets[0].range.start)
        assertEquals(DocumentProvider.Position(2, 0), result.snippets[0].range.end)
    }

    @Test
    fun `buildContent validates arguments`() {
        assertFailsWith<IllegalArgumentException>("negative startLine") {
            buildContent("content", -1, -1)
        }

        assertFailsWith<IllegalArgumentException>("invalid endLine") {
            buildContent("content", 0, -5)
        }

        assertFailsWith<IllegalArgumentException>("endLine <= startLine") {
            buildContent("content", 2, 1)
        }

        assertFailsWith<IllegalArgumentException>("endLine == startLine") {
            buildContent("content", 1, 1)
        }
    }

    @Test
    fun `buildFileSize handles empty file`() = runTest {
        val empty = createTestFile("empty.txt", "")
        val sizes = buildFileSize(fs, empty)

        assertEquals(1, sizes.size, "Empty file should only return Bytes (detected as binary)")
        val bytesSize = sizes.filterIsInstance<FileSize.Bytes>().first()
        assertEquals(0L, bytesSize.bytes)
    }

    @Test
    fun `buildFileSize counts single line correctly`() = runTest {
        val withoutNewline = createTestFile("single.txt", "single line")
        assertEquals(1, buildFileSize(fs, withoutNewline).filterIsInstance<FileSize.Lines>().first().lines)

        val withNewline = createTestFile("single-nl.txt", "single line\n")
        assertEquals(1, buildFileSize(fs, withNewline).filterIsInstance<FileSize.Lines>().first().lines)
    }

    @Test
    fun `buildFileSize counts multiple lines correctly`() = runTest {
        val multiLine = createTestFile("multi.txt", "line1\nline2\nline3")
        assertEquals(3, buildFileSize(fs, multiLine).filterIsInstance<FileSize.Lines>().first().lines)

        val withTrailing = createTestFile("trailing.txt", "line1\nline2\nline3\n")
        assertEquals(3, buildFileSize(fs, withTrailing).filterIsInstance<FileSize.Lines>().first().lines)
    }

    @Test
    fun `buildFileSize handles only newlines`() = runTest {
        val onlyNewlines = createTestFile("newlines.txt", "\n\n\n")
        assertEquals(3, buildFileSize(fs, onlyNewlines).filterIsInstance<FileSize.Lines>().first().lines)
    }

    @Test
    fun `buildFileSize respects 1 MiB threshold`() = runTest {
        val atBoundary = createTestFile("exact.txt", "x".repeat(FileSize.MIB.toInt()))
        val atSizes = buildFileSize(fs, atBoundary)
        assertEquals(2, atSizes.size, "At exactly 1 MiB should return both Bytes and Lines")
        assertTrue(atSizes.any { it is FileSize.Lines })

        val overBoundary = createTestFile("over.txt", "x".repeat(FileSize.MIB.toInt() + 1))
        val overSizes = buildFileSize(fs, overBoundary)
        assertEquals(1, overSizes.size, "Over 1 MiB should only return Bytes")
        assertIs<FileSize.Bytes>(overSizes[0])
    }

    @Test
    fun `buildFileSize returns only bytes for binary files`() = runTest {
        val binaryFile = tempDir.resolve("binary.dat").apply {
            createFile()
            writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        }

        val sizes = buildFileSize(fs, binaryFile)

        assertEquals(1, sizes.size)
        assertIs<FileSize.Bytes>(sizes[0])
    }

    @Test
    fun `buildFileEntry works with full file`() = runTest {
        val file = createTestFile("test.txt", "content")
        val metadata = fs.metadata(file)!!

        val entry = buildFileEntry(fs, file, metadata, 0, -1)

        assertIs<FileSystemEntry.File.Content.Text>(entry.content)
        assertEquals("content", entry.content.text)
    }

    @Test
    fun `buildFileEntry works with excerpt`() = runTest {
        val file = createTestFile("test.txt", "line0\nline1\nline2")
        val metadata = fs.metadata(file)!!

        val entry = buildFileEntry(fs, file, metadata, 1, 2)

        assertIs<FileSystemEntry.File.Content.Excerpt>(entry.content)
        assertEquals("line1\n", entry.content.snippets[0].text)
    }
}
