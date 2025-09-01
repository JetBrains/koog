package ai.koog.agents.file.tools.model

import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class FileSystemEntryJvmTest {

    private val fs = JVMFileSystemProvider.ReadOnly

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `File_of - creation from existing file path`() = runBlocking {
        val testFile = tempDir.resolve("test.txt").apply {
            createFile()
            writeText("test content")
        }

        val fileEntry = FileSystemEntry.File.of(testFile, fs = fs)

        assertNotNull(fileEntry)
        assertEquals("test.txt", fileEntry!!.name)
        assertEquals("txt", fileEntry.extension)
        assertEquals(testFile.toString(), fileEntry.path)
        assertFalse(fileEntry.hidden)
        assertEquals(FileMetadata.FileContentType.Text, fileEntry.contentType)
        assertTrue(fileEntry.size.isNotEmpty())
        assertEquals(FileSystemEntry.File.Content.None, fileEntry.content)
    }

    @Test
    fun `File_of - creation from non-existing path`() = runBlocking {
        val nonExistentPath = tempDir.resolve("nonexistent.txt")

        val fileEntry = FileSystemEntry.File.of(nonExistentPath, fs = fs)

        assertNull(fileEntry)
    }

    @Test
    fun `File_of - creation from directory path`() = runBlocking {
        val directory = tempDir.resolve("testdir").apply { createDirectories() }

        val fileEntry = FileSystemEntry.File.of(directory, fs = fs)

        assertNull(fileEntry)
    }

    @Test
    fun `File_of - creation with explicit content`() = runBlocking {
        val testFile = tempDir.resolve("test.txt").apply {
            createFile()
            writeText("test content")
        }
        val content = FileSystemEntry.File.Content.Text("custom content")

        val fileEntry = FileSystemEntry.File.of(testFile, content, fs)

        assertNotNull(fileEntry)
        assertEquals(content, fileEntry!!.content)
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `File_of - creation with hidden leading-dot file`() = runBlocking {
        val hiddenFile = tempDir.resolve(".hidden").apply {
            createFile()
            writeText("hidden content")
        }

        val fileEntry = FileSystemEntry.File.of(hiddenFile, fs = fs)

        assertNotNull(fileEntry)
        assertEquals(".hidden", fileEntry!!.name)
        assertEquals("hidden", fileEntry.extension)
        assertTrue(fileEntry.hidden)
    }

    @Test
    fun `File_of - no extension yields null`() = runBlocking {
        val fileNoExt = tempDir.resolve("LICENSE").apply {
            createFile()
            writeText("license text")
        }
        val fileEntry = FileSystemEntry.File.of(fileNoExt, fs = fs)
        assertNotNull(fileEntry)
        assertEquals("LICENSE", fileEntry!!.name)
        assertNull(fileEntry.extension)
    }

    @Test
    fun `File_of - hidden multi-dot filename uses last token as extension`() = runBlocking {
        val hiddenMultiDot = tempDir.resolve(".env.local").apply {
            createFile()
            writeText("FOO=bar")
        }
        val fileEntry = FileSystemEntry.File.of(hiddenMultiDot, fs = fs)
        assertNotNull(fileEntry)
        assertEquals(".env.local", fileEntry!!.name)
        assertEquals("local", fileEntry.extension)
    }

    @Test
    fun `File_visit - calls visitor once`() = runBlocking {
        val file = FileSystemEntry.File(
            name = "test.txt",
            extension = "txt",
            path = "/test.txt",
            hidden = false,
            size = emptyList(),
            contentType = FileMetadata.FileContentType.Text
        )

        var visitCount = 0
        var visitedEntry: FileSystemEntry? = null

        file.visit(0) { entry ->
            visitCount++
            visitedEntry = entry
        }

        assertEquals(1, visitCount)
        assertEquals(file, visitedEntry)
    }

    @Test
    fun `File_visit - ignores depth parameter`() = runBlocking {
        val file = FileSystemEntry.File(
            name = "test.txt",
            extension = "txt",
            path = "/test.txt",
            hidden = false,
            size = emptyList(),
            contentType = FileMetadata.FileContentType.Text
        )

        var visitCount = 0

        file.visit(5) { _ ->
            visitCount++
        }

        assertEquals(1, visitCount)
    }

    @Test
    fun `Folder_of - creation from existing directory path`() = runBlocking {
        val testDir = tempDir.resolve("test-folder").apply { createDirectories() }

        val folderEntry = FileSystemEntry.Folder.of(testDir, fs = fs)

        assertNotNull(folderEntry)
        assertEquals("test-folder", folderEntry!!.name)
        assertNull(folderEntry.extension)
        assertEquals(testDir.toString(), folderEntry.path)
        assertFalse(folderEntry.hidden)
        assertNull(folderEntry.entries)
    }

    @Test
    fun `Folder_of - creation from non-existing path`() = runBlocking {
        val nonExistentPath = tempDir.resolve("nonexistent")

        val folderEntry = FileSystemEntry.Folder.of(nonExistentPath, fs = fs)

        assertNull(folderEntry)
    }

    @Test
    fun `Folder_of - creation from file path`() = runBlocking {
        val file = tempDir.resolve("test.txt").apply {
            createFile()
            writeText("test content")
        }

        val folderEntry = FileSystemEntry.Folder.of(file, fs = fs)

        assertNull(folderEntry)
    }

    @Test
    fun `Folder_of - creation with explicit entries`() = runBlocking {
        val testDir = tempDir.resolve("test-folder").apply { createDirectories() }
        val entries = listOf<FileSystemEntry>()

        val folderEntry = FileSystemEntry.Folder.of(testDir, entries, fs)

        assertNotNull(folderEntry)
        assertEquals(entries, folderEntry!!.entries)
    }

    @Test
    fun `Folder_of - creation with hidden directory`() = runBlocking {
        val hiddenDir = tempDir.resolve(".hidden-dir").apply { createDirectories() }

        val folderEntry = FileSystemEntry.Folder.of(hiddenDir, fs = fs)

        assertNotNull(folderEntry)
        assertEquals(".hidden-dir", folderEntry!!.name)
    }

    @Test
    fun `Folder_property - extension is always null`() {
        val folder = FileSystemEntry.Folder(
            name = "test-folder",
            path = "/path/to/test-folder",
            hidden = false,
            entries = null
        )

        assertNull(folder.extension)
    }

    @Test
    fun `Folder_visit - depth zero visits only folder`() = runBlocking {
        val folder = FileSystemEntry.Folder(
            name = "test-folder",
            path = "/test-folder",
            hidden = false,
            entries = listOf()
        )

        var visitCount = 0
        var visitedEntry: FileSystemEntry? = null

        folder.visit(0) { entry ->
            visitCount++
            visitedEntry = entry
        }

        assertEquals(1, visitCount)
        assertEquals(folder, visitedEntry)
    }

    @Test
    fun `Folder_visit - null entries`() = runBlocking {
        val folder = FileSystemEntry.Folder(
            name = "test-folder",
            path = "/test-folder",
            hidden = false,
            entries = null
        )

        var visitCount = 0

        folder.visit(5) { _ ->
            visitCount++
        }

        assertEquals(1, visitCount)
    }

    @Test
    fun `Folder_visit - empty entries`() = runBlocking {
        val folder = FileSystemEntry.Folder(
            name = "test-folder",
            path = "/test-folder",
            hidden = false,
            entries = emptyList()
        )

        var visitCount = 0

        folder.visit(5) { _ ->
            visitCount++
        }

        assertEquals(1, visitCount)
    }

    @Test
    fun `Folder_visit - with entries and depth`() = runBlocking {
        val childFile = FileSystemEntry.File(
            name = "child.txt",
            extension = "txt",
            path = "/test-folder/child.txt",
            hidden = false,
            size = emptyList(),
            contentType = FileMetadata.FileContentType.Text
        )

        val nestedFolder = FileSystemEntry.Folder(
            name = "nested",
            path = "/test-folder/nested",
            hidden = false,
            entries = listOf(childFile)
        )

        val folder = FileSystemEntry.Folder(
            name = "test-folder",
            path = "/test-folder",
            hidden = false,
            entries = listOf(nestedFolder)
        )

        var visitCount = 0
        val visitedEntries = mutableListOf<FileSystemEntry>()

        folder.visit(2) { entry ->
            visitCount++
            visitedEntries.add(entry)
        }

        assertEquals(3, visitCount)
        assertEquals(folder, visitedEntries[0])
        assertEquals(nestedFolder, visitedEntries[1])
        assertEquals(childFile, visitedEntries[2])
    }

    @Test
    fun `Folder_visit - respects depth limit`() = runBlocking {
        val deepFile = FileSystemEntry.File(
            name = "deep.txt",
            extension = "txt",
            path = "/test-folder/nested/deep.txt",
            hidden = false,
            size = emptyList(),
            contentType = FileMetadata.FileContentType.Text
        )

        val nestedFolder = FileSystemEntry.Folder(
            name = "nested",
            path = "/test-folder/nested",
            hidden = false,
            entries = listOf(deepFile)
        )

        val folder = FileSystemEntry.Folder(
            name = "test-folder",
            path = "/test-folder",
            hidden = false,
            entries = listOf(nestedFolder)
        )

        var visitCount = 0

        folder.visit(1) { _ ->
            visitCount++
        }

        assertEquals(2, visitCount) // folder + nestedFolder, but not deepFile
    }

    @Test
    fun `Folder_visit - mixed entries preorder`() = runBlocking {
        val file1 = FileSystemEntry.File(
            name = "file1.txt",
            extension = "txt",
            path = "/test/file1.txt",
            hidden = false,
            size = emptyList(),
            contentType = FileMetadata.FileContentType.Text
        )

        val file2 = FileSystemEntry.File(
            name = "file2.txt",
            extension = "txt",
            path = "/test/file2.txt",
            hidden = false,
            size = emptyList(),
            contentType = FileMetadata.FileContentType.Text
        )

        val nestedFolder = FileSystemEntry.Folder(
            name = "nested",
            path = "/test/nested",
            hidden = false,
            entries = listOf(file2)
        )

        val folder = FileSystemEntry.Folder(
            name = "test",
            path = "/test",
            hidden = false,
            entries = listOf(file1, nestedFolder)
        )

        val visitedEntries = mutableListOf<FileSystemEntry>()

        folder.visit(2) { entry ->
            visitedEntries.add(entry)
        }

        assertEquals(4, visitedEntries.size)
        assertEquals(folder, visitedEntries[0])
        assertEquals(file1, visitedEntries[1])
        assertEquals(nestedFolder, visitedEntries[2])
        assertEquals(file2, visitedEntries[3])
    }

    @Test
    fun `FileSystemEntry_of - with file path`() = runBlocking {
        val testFile = tempDir.resolve("test.txt").apply {
            createFile()
            writeText("test content")
        }

        val entry = FileSystemEntry.of(testFile, fs)

        assertNotNull(entry)
        assertTrue(entry is FileSystemEntry.File)
        assertEquals("test.txt", entry!!.name)
        assertEquals("txt", entry.extension)
    }

    @Test
    fun `FileSystemEntry_of - with directory path`() = runBlocking {
        val testDir = tempDir.resolve("test-folder").apply { createDirectories() }

        val entry = FileSystemEntry.of(testDir, fs)

        assertNotNull(entry)
        assertTrue(entry is FileSystemEntry.Folder)
        assertEquals("test-folder", entry!!.name)
        assertNull(entry.extension)
    }

    @Test
    fun `FileSystemEntry_of - with non-existing path`() = runBlocking {
        val nonExistentPath = tempDir.resolve("nonexistent")

        val entry = FileSystemEntry.of(nonExistentPath, fs)

        assertNull(entry)
    }
}
