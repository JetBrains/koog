package ai.koog.agents.file.tools.model

import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileMetadata
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSystemEntryTest {
    private fun createFile(
        name: String = "test.txt",
        path: String = "/path/test.txt",
        extension: String? = "txt",
        hidden: Boolean = false,
        contentType: FileMetadata.FileContentType = FileMetadata.FileContentType.Text,
        size: List<FileSize> = listOf(FileSize.Bytes(100)),
        content: FileSystemEntry.File.Content = FileSystemEntry.File.Content.None,
    ) = FileSystemEntry.File(
        name = name,
        extension = extension,
        path = path,
        hidden = hidden,
        size = size,
        contentType = contentType,
        content = content,
    )

    private fun createFolder(
        name: String = "folder",
        path: String = "/path/folder",
        hidden: Boolean = false,
        entries: List<FileSystemEntry>? = null,
    ) = FileSystemEntry.Folder(
        name = name,
        path = path,
        hidden = hidden,
        entries = entries,
    )

    // ============ FileSystemEntry.File tests ============

    @Test
    fun `file has correct properties`() {
        val file = createFile(
            name = "config.json",
            extension = "json",
            path = "/app/config.json",
            hidden = true,
            contentType = FileMetadata.FileContentType.Text,
            size = listOf(FileSize.Bytes(1024), FileSize.Lines(100))
        )

        assertEquals("config.json", file.name, "File name should match")
        assertEquals("json", file.extension, "Extension should match")
        assertEquals("/app/config.json", file.path, "Path should match")
        assertTrue(file.hidden, "File should be hidden")
        assertEquals(FileMetadata.FileContentType.Text, file.contentType, "Content type should be Text")
        assertEquals(2, file.size.size, "Should have two size representations")
    }

    @Test
    fun `file with no extension`() {
        val file = createFile(
            name = "README",
            extension = null
        )
        assertNull(file.extension, "File without extension should have null extension")
    }

    @Test
    fun `file visit ignores depth parameter`() = runTest {
        val file = createFile()
        val visitedDepth0 = mutableListOf<String>()
        val visitedDepthMax = mutableListOf<String>()

        file.visit(depth = 0) { entry ->
            visitedDepth0.add(entry.name)
        }

        file.visit(depth = Int.MAX_VALUE) { entry ->
            visitedDepthMax.add(entry.name)
        }

        assertEquals(visitedDepth0, visitedDepthMax, "File visit should ignore depth")
        assertEquals(1, visitedDepth0.size, "Should visit exactly once")
    }

    // ============ FileSystemEntry.File.Content tests ============

    @Test
    fun `content text has correct text`() {
        val content = FileSystemEntry.File.Content.Text("hello world\nline 2")

        assertEquals("hello world\nline 2", content.text, "Text content should match")
    }

    @Test
    fun `content text with special characters`() {
        val specialText = "Special chars: \"quotes\", \ttab, \nnewline, emoji 🎉"
        val content = FileSystemEntry.File.Content.Text(specialText)

        assertEquals(specialText, content.text, "Special characters should be preserved")
    }

    @Test
    fun `content excerpt single snippet`() {
        val range = DocumentProvider.DocumentRange(
            start = DocumentProvider.Position(0, 0),
            end = DocumentProvider.Position(1, 0),
        )
        val snippet = FileSystemEntry.File.Content.Excerpt.Snippet(
            text = "line 1",
            range = range,
        )
        val excerpt = FileSystemEntry.File.Content.Excerpt(snippet)

        assertEquals(1, excerpt.snippets.size, "Should have one snippet")
        assertEquals("line 1", excerpt.snippets[0].text, "Snippet text should match")
        assertEquals(range, excerpt.snippets[0].range, "Snippet range should match")
    }

    @Test
    fun `content excerpt multiple snippets`() {
        val snippet1 = FileSystemEntry.File.Content.Excerpt.Snippet(
            text = "first snippet",
            range = DocumentProvider.DocumentRange(
                DocumentProvider.Position(0, 0),
                DocumentProvider.Position(1, 0),
            ),
        )
        val snippet2 = FileSystemEntry.File.Content.Excerpt.Snippet(
            text = "second snippet",
            range = DocumentProvider.DocumentRange(
                DocumentProvider.Position(5, 0),
                DocumentProvider.Position(6, 0),
            ),
        )
        val snippet3 = FileSystemEntry.File.Content.Excerpt.Snippet(
            text = "third snippet",
            range = DocumentProvider.DocumentRange(
                DocumentProvider.Position(10, 5),
                DocumentProvider.Position(11, 15),
            ),
        )

        val excerpt = FileSystemEntry.File.Content.Excerpt(snippet1, snippet2, snippet3)

        assertEquals(3, excerpt.snippets.size, "Should have three snippets")
        assertEquals("first snippet", excerpt.snippets[0].text)
        assertEquals("second snippet", excerpt.snippets[1].text)
        assertEquals("third snippet", excerpt.snippets[2].text)
    }

    // ============ FileSystemEntry.Folder tests ============

    @Test
    fun `folder has null extension`() {
        val folder = createFolder()
        assertNull(folder.extension, "Folders should not have extensions")
    }

    @Test
    fun `folder visit depth=0 visits only folder`() = runTest {
        val folder = createFolder(
            name = "root",
            entries = listOf(
                createFile(name = "file1.txt"),
                createFile(name = "file2.txt"),
            ),
        )
        val visited = mutableListOf<String>()

        folder.visit(depth = 0) { entry ->
            visited.add(entry.name)
        }

        assertEquals(
            expected = listOf("root"),
            actual = visited,
            message = "Depth 0 should only visit the folder itself"
        )
    }

    @Test
    fun `folder visit depth=1 visits direct children only`() = runTest {
        val folder = createFolder(
            name = "root",
            entries = listOf(
                createFile(name = "file1.txt"),
                createFolder(
                    name = "subfolder",
                    entries = listOf(createFile(name = "nested.txt")),
                ),
                createFile(name = "file2.txt"),
            ),
        )
        val visited = mutableListOf<String>()

        folder.visit(depth = 1) { entry ->
            visited.add(entry.name)
        }

        assertEquals(
            expected = listOf("root", "file1.txt", "subfolder", "file2.txt"),
            actual = visited,
            message = "Depth 1 should visit folder and immediate children only, not nested content"
        )
    }

    /**
     * Tests that visit() traverses the tree in pre-order (parent before children).
     * Structure:
     * ```
     * root
     * ├── left
     * │   ├── l1.txt
     * │   └── left2
     * │       └── l2.txt
     * └── right
     *     └── r1.txt
     * ```
     */
    @Test
    fun `folder visit is pre-order traversal`() = runTest {
        val left = createFolder(
            name = "left",
            entries = listOf(
                createFile(name = "l1.txt"),
                createFolder(name = "left2", entries = listOf(createFile(name = "l2.txt"))),
            ),
        )
        val right = createFolder(
            name = "right",
            entries = listOf(createFile(name = "r1.txt")),
        )
        val root = createFolder(name = "root", entries = listOf(left, right))

        val visited = mutableListOf<String>()
        root.visit(depth = Int.MAX_VALUE) { visited.add(it.name) }

        assertEquals(
            expected = listOf("root", "left", "l1.txt", "left2", "l2.txt", "right", "r1.txt"),
            actual = visited,
            message = "Should traverse in pre-order: parent before children, left to right"
        )
    }

    @Test
    fun `folder visit with null entries visits only folder`() = runTest {
        val folder = createFolder(name = "empty", entries = null)
        val visited = mutableListOf<String>()

        folder.visit(depth = 10) { entry ->
            visited.add(entry.name)
        }

        assertEquals(
            expected = listOf("empty"),
            actual = visited,
            message = "Folder with null entries should only visit itself"
        )
    }

    @Test
    fun `folder visit with empty entries visits only folder`() = runTest {
        val folder = createFolder(name = "empty", entries = emptyList())
        val visited = mutableListOf<String>()

        folder.visit(depth = 10) { entry ->
            visited.add(entry.name)
        }

        assertEquals(
            expected = listOf("empty"),
            actual = visited,
            message = "Folder with empty entries should only visit itself"
        )
    }

    @Test
    fun `folder visit with negative depth behaves like depth=0`() = runTest {
        val folder = createFolder(
            name = "root",
            entries = listOf(createFile(name = "file.txt"))
        )

        val visited = mutableListOf<String>()
        folder.visit(depth = -1) { entry ->
            visited.add(entry.name)
        }

        assertEquals(
            expected = listOf("root"),
            actual = visited,
            message = "Negative depth should behave like depth=0"
        )
    }

    @Test
    fun `folder visit with depth exceeding structure depth`() = runTest {
        val structure = createFolder(
            "root",
            entries = listOf(
                createFolder(
                    "child",
                    entries = listOf(
                        createFile("grandchild.txt")
                    )
                )
            )
        )

        val visited = mutableListOf<String>()
        structure.visit(depth = 100) { entry ->
            visited.add(entry.name)
        }

        assertEquals(
            listOf("root", "child", "grandchild.txt"),
            visited,
            "Should visit entire structure even when depth exceeds structure depth"
        )
    }
}
