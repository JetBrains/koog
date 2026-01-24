package ai.koog.agents.ext.tool.file

import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.rag.base.files.filter.GlobPattern
import ai.koog.rag.base.files.model.FileSystemEntry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListDirectoryUtilJvmTest {

    private val fs = JVMFileSystemProvider.ReadOnly

    @TempDir
    lateinit var tempDir: Path

    private fun createFile(name: String, content: String = ""): Path =
        tempDir.resolve(name).createFile().apply { writeText(content) }

    private fun createDir(name: String): Path =
        tempDir.resolve(name).createDirectories()

    @Test
    fun `buildDirectoryTree returns file entry when path is file`() = runTest {
        // Structure: a.txt
        val f = createFile("a.txt", "hello")
        val meta = assertNotNull(fs.metadata(f))

        val entry = buildDirectoryTree(fs, f, meta, maxDepth = 1)

        assertIs<FileSystemEntry.File>(entry)
        assertEquals(f.toAbsolutePath().toString(), entry.path)
        assertEquals("a.txt", entry.name)
        assertEquals("txt", entry.extension)
    }

    @Test
    fun `buildDirectoryTree returns folder with entries when depth 1 and multiple children`() = runTest {
        // Structure:
        // dir/
        // ├── f1.txt
        // └── f2.txt
        val d = createDir("dir")
        createFile("dir/f1.txt", "1")
        createFile("dir/f2.txt", "2")

        val meta = assertNotNull(fs.metadata(d))
        val entry = buildDirectoryTree(fs, d, meta, maxDepth = 1)

        assertIs<FileSystemEntry.Folder>(entry)
        assertEquals(d.toAbsolutePath().toString(), entry.path)
        val entries = assertNotNull(entry.entries)
        assertEquals(2, entries.size)
        val names = entries.map { (it as FileSystemEntry.File).name }.toSet()
        assertEquals(setOf("f1.txt", "f2.txt"), names)
    }

    @Test
    fun `single-entry directories are unwrapped and depth preserved`() = runTest {
        // Structure:
        // root/
        // └── single/
        //     └── leaf.txt
        val root = createDir("root")
        val single = root.resolve("single").createDirectories()
        val leaf = single.resolve("leaf.txt").createFile().apply { writeText("x") }

        val rootMeta = assertNotNull(fs.metadata(root))
        val entry = buildDirectoryTree(fs, root, rootMeta, maxDepth = 1) as FileSystemEntry.Folder

        val child = assertNotNull(entry.entries).single() as FileSystemEntry.Folder
        val leafEntry = assertNotNull(child.entries).single() as FileSystemEntry.File
        assertEquals(leaf.toAbsolutePath().toString(), leafEntry.path)
    }

    @Test
    fun `filter hides non matching names`() = runTest {
        // Structure:
        // d.txt/
        // ├── a.md
        // └── b.txt
        val d = createDir("d.txt")
        d.resolve("a.md").createFile().writeText("a")
        d.resolve("b.txt").createFile().writeText("b")

        val pattern = GlobPattern("*.txt", caseSensitive = false)
        val dMeta = assertNotNull(fs.metadata(d))
        val entry = buildDirectoryTree(fs, d, dMeta, maxDepth = 2, filter = pattern) as FileSystemEntry.Folder

        val names = assertNotNull(entry.entries).map {
            when (it) {
                is FileSystemEntry.File -> it.name
                is FileSystemEntry.Folder -> it.name
            }
        }.toSet()
        assertEquals(setOf("b.txt"), names)
    }

    @Test
    fun `filter returns null when root path doesn't match`() = runTest {
        // Structure: document.md
        val file = createFile("document.md", "content")
        val metadata = assertNotNull(fs.metadata(file))
        val pattern = GlobPattern("*.txt", caseSensitive = false)

        val entry = buildDirectoryTree(fs, file, metadata, maxDepth = 1, filter = pattern)

        assertNull(entry)
    }

    @Test
    fun `empty directory has empty entries list`() = runTest {
        // Structure: empty/ (no children)
        val emptyDir = createDir("empty")
        val metadata = assertNotNull(fs.metadata(emptyDir))

        val entry = buildDirectoryTree(fs, emptyDir, metadata, maxDepth = 1) as FileSystemEntry.Folder

        val entries = assertNotNull(entry.entries)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `throws IllegalArgumentException for invalid maxDepth`() = runTest {
        val dir = createDir("test")
        val metadata = assertNotNull(fs.metadata(dir))

        assertFailsWith<IllegalArgumentException> {
            buildDirectoryTree(fs, dir, metadata, maxDepth = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            buildDirectoryTree(fs, dir, metadata, maxDepth = -1)
        }
    }

    @Test
    fun `respects maxDepth limit with multiple files`() = runTest {
        // Structure:
        // level1/
        // └── level2/
        //     ├── file1.txt
        //     └── file2.txt
        val level1 = createDir("level1")
        val level2 = level1.resolve("level2").createDirectories()
        level2.resolve("file1.txt").createFile().writeText("content1")
        level2.resolve("file2.txt").createFile().writeText("content2")

        val metadata = assertNotNull(fs.metadata(level1))
        val entry = buildDirectoryTree(fs, level1, metadata, maxDepth = 1) as FileSystemEntry.Folder

        // level1 has 1 child (level2 directory), so it unwraps
        // level2 has multiple children - with fixed behavior, they are listed
        val level2Folder = assertNotNull(entry.entries).single() as FileSystemEntry.Folder
        val level2Entries = assertNotNull(level2Folder.entries)
        assertEquals(2, level2Entries.size)
        val names = level2Entries.map { (it as FileSystemEntry.File).name }.toSet()
        assertEquals(setOf("file1.txt", "file2.txt"), names)
    }

    @Test
    fun `respects maxDepth limit with multiple directories at root`() = runTest {
        // Structure:
        // level1/
        // ├── dir1/
        // │   └── file1.txt
        // └── dir2/
        //     └── file2.txt
        val level1 = createDir("level1")
        val dir1 = level1.resolve("dir1").createDirectories()
        val dir2 = level1.resolve("dir2").createDirectories()
        dir1.resolve("file1.txt").createFile().writeText("content1")
        dir2.resolve("file2.txt").createFile().writeText("content2")

        val metadata = assertNotNull(fs.metadata(level1))
        val entry = buildDirectoryTree(fs, level1, metadata, maxDepth = 1) as FileSystemEntry.Folder

        // level1 has multiple children - with fixed behavior, they are listed as collapsed directories
        val entries = assertNotNull(entry.entries)
        assertEquals(2, entries.size)
        val names = entries.map { (it as FileSystemEntry.Folder).name }.toSet()
        assertEquals(setOf("dir1", "dir2"), names)
        // Child directories should be collapsed (entries = null) since we're at the depth limit
        entries.forEach { dir ->
            assertNull((dir as FileSystemEntry.Folder).entries)
        }
    }

    @Test
    fun `single directory unwrapping with empty child directory`() = runTest {
        // Structure:
        // root/
        // └── single/ (empty directory)
        val root = createDir("root")
        root.resolve("single").createDirectories()

        val rootMeta = assertNotNull(fs.metadata(root))
        val entry = buildDirectoryTree(fs, root, rootMeta, maxDepth = 2) as FileSystemEntry.Folder

        assertEquals("root", entry.name)
        val singleChild = assertNotNull(entry.entries).single() as FileSystemEntry.Folder
        assertEquals("single", singleChild.name)
        val entries = assertNotNull(singleChild.entries)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `maxDepth exceeds actual directory depth`() = runTest {
        // Structure: shallow/ -> file.txt (only 2 levels deep)
        val shallow = createDir("shallow")
        createFile("shallow/file.txt", "content")

        val metadata = assertNotNull(fs.metadata(shallow))
        val entry = buildDirectoryTree(fs, shallow, metadata, maxDepth = 10) as FileSystemEntry.Folder

        val entries = assertNotNull(entry.entries)
        assertEquals(1, entries.size)
        val file = entries.single() as FileSystemEntry.File
        assertEquals("file.txt", file.name)
        assertEquals(FileSystemEntry.File.Content.None, file.content)
    }

    @Test
    fun `unwrapping stops when reaching files`() = runTest {
        // Structure:
        // root/
        // └── chain1/
        //     └── chain2/
        //         └── final.txt
        val root = createDir("root")
        val chain1 = root.resolve("chain1").createDirectories()
        val chain2 = chain1.resolve("chain2").createDirectories()
        chain2.resolve("final.txt").createFile().writeText("content")

        val rootMeta = assertNotNull(fs.metadata(root))
        val entry = buildDirectoryTree(fs, root, rootMeta, maxDepth = 1) as FileSystemEntry.Folder

        // Should unwrap through single directories until reaching the file
        val chain1Folder = assertNotNull(entry.entries).single() as FileSystemEntry.Folder
        val chain2Folder = assertNotNull(chain1Folder.entries).single() as FileSystemEntry.Folder
        val finalFile = assertNotNull(chain2Folder.entries).single() as FileSystemEntry.File
        assertEquals("final.txt", finalFile.name)
    }
}
