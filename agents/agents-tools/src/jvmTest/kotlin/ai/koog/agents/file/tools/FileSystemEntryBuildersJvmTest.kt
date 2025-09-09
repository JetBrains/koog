package ai.koog.agents.file.tools

import ai.koog.agents.file.tools.model.FileSize
import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.agents.file.tools.model.buildFileEntry
import ai.koog.agents.file.tools.model.buildFileSize
import ai.koog.agents.file.tools.model.buildFileSystemEntry
import ai.koog.agents.file.tools.model.buildFolderEntry
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FileSystemEntryBuildersJvmTest {

    private val fs = JVMFileSystemProvider.ReadOnly

    @TempDir
    lateinit var tempDir: Path

    private fun createTestFile(name: String, content: String = ""): Path =
        tempDir.resolve(name).createFile().apply { writeText(content) }

    private fun createTestDir(name: String): Path =
        tempDir.resolve(name).createDirectories()

    @Test
    fun `buildFileEntry - creates text file entry without content`() = runTest {
        val path = createTestFile("note.txt", "a\nb\nc")
        val metadata = assertNotNull(fs.metadata(path))

        val entry = buildFileEntry(fs, path, metadata)

        assertIs<FileSystemEntry.File>(entry)
        assertEquals("note.txt", entry.name)
        assertEquals("txt", entry.extension)
        assertEquals(false, entry.hidden)
        assertEquals(FileMetadata.FileContentType.Text, entry.contentType)
        assertIs<FileSystemEntry.File.Content.None>(entry.content)
        assertEquals(listOf(FileSize.Bytes(5), FileSize.Lines(3)), entry.size)
    }

    @Test
    fun `buildFolderEntry uses fs name and absolute path and preserves null entries`() = runTest {
        val dir = createTestDir("bin")
        val meta = assertNotNull(fs.metadata(dir))

        val entry = buildFolderEntry(fs, dir, meta, null)

        assertIs<FileSystemEntry.Folder>(entry)
        assertEquals(fs.name(dir), entry.name)
        assertEquals(fs.toAbsolutePathString(dir), entry.path)
        assertNull(entry.entries)
    }

    @Test
    fun `buildFolderEntry preserves provided children and their order`() = runTest {
        // src/
        //   A.txt
        //   pkg/
        val parent = createTestDir("src")
        val a = createTestFile("src/A.txt", "a")
        val pkg = createTestDir("src/pkg")

        val fileEntry = buildFileSystemEntry(fs, a, assertNotNull(fs.metadata(a)))
        val dirEntry = buildFileSystemEntry(fs, pkg, assertNotNull(fs.metadata(pkg)))
        val children = listOf(fileEntry, dirEntry)

        val folder = buildFolderEntry(fs, parent, assertNotNull(fs.metadata(parent)), children)

        assertNotNull(folder.entries)
        assertEquals(children, folder.entries)
    }

    @Test
    fun `buildFileSystemEntry - returns file for file metadata with none content`() = runTest {
        val path = createTestFile("hello.md", "# Title\ncontent")
        val metadata = assertNotNull(fs.metadata(path))

        val entry = buildFileSystemEntry(fs, path, metadata)

        assertIs<FileSystemEntry.File>(entry).also {
            assertIs<FileSystemEntry.File.Content.None>(it.content)
        }
    }

    @Test
    fun `buildFileSystemEntry - returns folder for directory metadata with null entries`() = runTest {
        val path = createTestDir("bin")
        val metadata = assertNotNull(fs.metadata(path))

        val entry = buildFileSystemEntry(fs, path, metadata)

        assertIs<FileSystemEntry.Folder>(entry).also {
            assertEquals(null, it.entries)
        }
    }

    @Test
    fun `buildFileSize handles empty file`() = runTest {
        val empty = createTestFile("empty.txt", "")
        val sizes = buildFileSize(fs, empty)

        assertEquals(1, sizes.size, "Empty file should only return Bytes (detected as binary)")
        assertEquals(0L, sizes.filterIsInstance<FileSize.Bytes>().first().bytes)
    }

    @Test
    fun `buildFileSize - single line with and without trailing newline`() = runTest {
        val noNl = createTestFile("single.txt", "a")
        val sizes1 = buildFileSize(fs, noNl)
        assertEquals(1L, sizes1.filterIsInstance<FileSize.Bytes>().single().bytes)
        assertEquals(1, sizes1.filterIsInstance<FileSize.Lines>().single().lines)

        val withNl = createTestFile("single-nl.txt", "a\n")
        val sizes2 = buildFileSize(fs, withNl)
        assertEquals(2L, sizes2.filterIsInstance<FileSize.Bytes>().single().bytes)
        assertEquals(2, sizes2.filterIsInstance<FileSize.Lines>().single().lines)
    }

    @Test
    fun `buildFileSize - multiple lines with and without trailing newline`() = runTest {
        val multi = createTestFile("multi.txt", "a\nb\nc")
        val sizes1 = buildFileSize(fs, multi)
        assertEquals(5L, sizes1.filterIsInstance<FileSize.Bytes>().single().bytes)
        assertEquals(3, sizes1.filterIsInstance<FileSize.Lines>().single().lines)

        val trailing = createTestFile("trailing.txt", "a\nb\nc\n")
        val sizes2 = buildFileSize(fs, trailing)
        assertEquals(6L, sizes2.filterIsInstance<FileSize.Bytes>().single().bytes)
        assertEquals(4, sizes2.filterIsInstance<FileSize.Lines>().single().lines)
    }

    @Test
    fun `buildFileSize - only newline characters are counted as empty lines`() = runTest {
        val onlyNl = createTestFile("newlines.txt", "\n\n\n")
        val sizes = buildFileSize(fs, onlyNl)

        assertEquals(2, sizes.size)
        assertEquals(3L, sizes.filterIsInstance<FileSize.Bytes>().single().bytes)
        assertEquals(4, sizes.filterIsInstance<FileSize.Lines>().single().lines)
    }

    @Test
    fun `buildFileSize - exactly 1 MiB returns Bytes and Lines, over 1 MiB only Bytes`() = runTest {
        val atBoundary = createTestFile("exact.txt", "x".repeat(FileSize.MIB.toInt()))
        val atSizes = buildFileSize(fs, atBoundary)
        assertEquals(2, atSizes.size)
        assertEquals(FileSize.MIB, atSizes.filterIsInstance<FileSize.Bytes>().single().bytes)
        assertEquals(1, atSizes.filterIsInstance<FileSize.Lines>().single().lines)

        val overBoundary = createTestFile("over.txt", "x".repeat(FileSize.MIB.toInt() + 1))
        val overSizes = buildFileSize(fs, overBoundary)
        assertEquals(1, overSizes.size)
        assertEquals(FileSize.MIB + 1, overSizes.filterIsInstance<FileSize.Bytes>().single().bytes)
    }

    @Test
    fun `buildFileSize - binary files return only Bytes`() = runTest {
        val binary = tempDir.resolve("binary.dat").apply {
            createFile()
            writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        }
        val sizes = buildFileSize(fs, binary)

        assertEquals(1, sizes.size)
        assertEquals(2L, sizes.filterIsInstance<FileSize.Bytes>().single().bytes)
    }

    @Test
    fun `buildFileSize - explicit Binary contentType returns only Bytes on small binary file`() = runTest {
        val binary = tempDir.resolve("binary.dat").apply {
            createFile()
            writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        }
        val sizes = buildFileSize(fs, binary, FileMetadata.FileContentType.Binary)

        assertEquals(1, sizes.size)
        assertEquals(2L, sizes.filterIsInstance<FileSize.Bytes>().single().bytes)
    }

    @Test
    fun `buildFileSize - explicit Text contentType returns Bytes and Lines`() = runTest {
        val textFile = createTestFile("text.txt", "a\nb\nc")
        val sizes = buildFileSize(fs, textFile, FileMetadata.FileContentType.Text)

        assertEquals(2, sizes.size)
        assertEquals(5L, sizes.filterIsInstance<FileSize.Bytes>().single().bytes)
        assertEquals(3, sizes.filterIsInstance<FileSize.Lines>().single().lines)
    }
}
