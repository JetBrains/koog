package ai.koog.agents.ext.tool.file.render

import ai.koog.agents.ext.tool.file.model.FileSize
import ai.koog.agents.ext.tool.file.model.FileSystemEntry
import ai.koog.prompt.text.text
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileMetadata
import kotlin.test.Test
import kotlin.test.assertEquals

class TextTest {
    @Test
    fun `file - renders with full path when no parent`() {
        val file = FileSystemEntry.File(
            name = "test.kt",
            extension = "kt",
            path = "/project/src/test.kt",
            hidden = false,
            size = listOf(FileSize.Bytes(1024)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.None
        )

        val actual = text { file(file) }

        assertEquals("/project/src/test.kt (1.0 KiB)", actual)
    }

    @Test
    fun `file - renders with relative path when parent provided`() {
        val parent = FileSystemEntry.Folder(
            name = "src",
            path = "/project/src",
            hidden = false,
            entries = null
        )
        val file = FileSystemEntry.File(
            name = "test.kt",
            extension = "kt",
            path = "/project/src/utils/test.kt",
            hidden = false,
            size = listOf(FileSize.Bytes(512)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.None
        )

        val actual = text { file(file, parent) }

        assertEquals("utils/test.kt (0.5 KiB)", actual)
    }

    @Test
    fun `file - shows name when path equals parent path`() {
        val parent = FileSystemEntry.Folder(
            name = "src",
            path = "/project/src",
            hidden = false,
            entries = null
        )
        val file = FileSystemEntry.File(
            name = "main.kt",
            extension = "kt",
            path = "/project/src",
            hidden = false,
            size = listOf(FileSize.Bytes(256)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.None
        )

        val actual = text { file(file, parent) }

        assertEquals("main.kt (0.2 KiB)", actual)
    }

    @Test
    fun `file - renders binary type with metadata`() {
        val file = FileSystemEntry.File(
            name = "image.png",
            extension = "png",
            path = "/repo/assets/image.png",
            hidden = false,
            size = listOf(FileSize.Bytes(1_572_864)),
            contentType = FileMetadata.FileContentType.Binary,
            content = FileSystemEntry.File.Content.None
        )

        val actual = text { file(file) }

        assertEquals("/repo/assets/image.png (binary, 1.5 MiB)", actual)
    }

    @Test
    fun `file - renders hidden file with all metadata`() {
        val file = FileSystemEntry.File(
            name = "notes.md",
            extension = "md",
            path = "/repo/docs/notes.md",
            hidden = true,
            size = listOf(FileSize.Bytes(2048), FileSize.Lines(12)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.None
        )

        val actual = text { file(file) }

        assertEquals("/repo/docs/notes.md (2.0 KiB, 12 lines, hidden)", actual)
    }

    @Test
    fun `file - renders with text content and trimming`() {
        val file = FileSystemEntry.File(
            name = "notes.md",
            extension = "md",
            path = "/repo/docs/notes.md",
            hidden = true,
            size = listOf(FileSize.Bytes(2048), FileSize.Lines(12)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Text("  Hello world!  \n\n")
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/repo/docs/notes.md (2.0 KiB, 12 lines, hidden)",
            "Content:",
            "```markdown",
            "Hello world!",
            "```"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - renders with empty text content`() {
        val file = FileSystemEntry.File(
            name = "empty.txt",
            extension = "txt",
            path = "/empty.txt",
            hidden = false,
            size = listOf(FileSize.Bytes(0), FileSize.Lines(0)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Text("")
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/empty.txt (0 bytes, 0 lines)",
            "Content:",
            ""
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - renders with single excerpt snippet`() {
        val snippet = FileSystemEntry.File.Content.Excerpt.Snippet(
            text = "  fun a() = 1  \n",
            range = DocumentProvider.DocumentRange(
                DocumentProvider.Position(3, 0),
                DocumentProvider.Position(5, 0)
            )
        )
        val file = FileSystemEntry.File(
            name = "code.kt",
            extension = "kt",
            path = "/repo/src/code.kt",
            hidden = false,
            size = listOf(FileSize.Bytes(512), FileSize.Lines(200)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Excerpt(listOf(snippet))
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/repo/src/code.kt (0.5 KiB, 200 lines)",
            "Excerpt:",
            "Lines 3-5:",
            "```kotlin",
            "fun a() = 1",
            "```"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - renders with multiple excerpt snippets`() {
        val s1 = FileSystemEntry.File.Content.Excerpt.Snippet(
            text = "  fun a() = 1  \n",
            range = DocumentProvider.DocumentRange(
                DocumentProvider.Position(3, 0),
                DocumentProvider.Position(5, 0)
            )
        )
        val s2 = FileSystemEntry.File.Content.Excerpt.Snippet(
            text = "class C\n{\n}\n",
            range = DocumentProvider.DocumentRange(
                DocumentProvider.Position(10, 0),
                DocumentProvider.Position(13, 0)
            )
        )
        val file = FileSystemEntry.File(
            name = "code.kt",
            extension = "kt",
            path = "/repo/src/code.kt",
            hidden = false,
            size = listOf(FileSize.Bytes(512), FileSize.Lines(200)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Excerpt(listOf(s1, s2))
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/repo/src/code.kt (0.5 KiB, 200 lines)",
            "Excerpt:",
            "Lines 3-5:",
            "```kotlin",
            "fun a() = 1",
            "```",
            "Lines 10-13:",
            "```kotlin",
            "class C\n{\n}",
            "```"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - renders empty excerpt with no excerpt message`() {
        val file = FileSystemEntry.File(
            name = "empty_excerpt.txt",
            extension = "txt",
            path = "/empty_excerpt.txt",
            hidden = false,
            size = listOf(FileSize.Bytes(100), FileSize.Lines(5)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Excerpt(emptyList())
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/empty_excerpt.txt (<0.1 KiB, 5 lines)",
            "(No excerpt)"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - renders with no content type None`() {
        val file = FileSystemEntry.File(
            name = "binary.exe",
            extension = "exe",
            path = "/bin/binary.exe",
            hidden = false,
            size = listOf(FileSize.Bytes(5_242_880)),
            contentType = FileMetadata.FileContentType.Binary,
            content = FileSystemEntry.File.Content.None
        )

        val actual = text { file(file) }

        assertEquals("/bin/binary.exe (binary, 5.0 MiB)", actual)
    }

    @Test
    fun `file - handles whitespace-only text content with proper trimming`() {
        val file = FileSystemEntry.File(
            name = "whitespace.txt",
            extension = "txt",
            path = "/whitespace.txt",
            hidden = false,
            size = listOf(FileSize.Bytes(10), FileSize.Lines(3)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Text("   \n\n   ")
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/whitespace.txt (<0.1 KiB, 3 lines)",
            "Content:",
            ""
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - handles excerpt with whitespace snippet and trimming`() {
        val snippet = FileSystemEntry.File.Content.Excerpt.Snippet(
            text = "   \n   \n",
            range = DocumentProvider.DocumentRange(
                DocumentProvider.Position(1, 0),
                DocumentProvider.Position(3, 0)
            )
        )
        val file = FileSystemEntry.File(
            name = "whitespace_excerpt.txt",
            extension = "txt",
            path = "/whitespace_excerpt.txt",
            hidden = false,
            size = listOf(FileSize.Bytes(20), FileSize.Lines(5)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Excerpt(listOf(snippet))
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/whitespace_excerpt.txt (<0.1 KiB, 5 lines)",
            "Excerpt:",
            "Lines 1-3:",
            ""
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - handles complex path separators in relative path calculation`() {
        val parent = FileSystemEntry.Folder(
            name = "root",
            path = "/root",
            hidden = false,
            entries = null
        )
        val file = FileSystemEntry.File(
            name = "test.txt",
            extension = "txt",
            path = "/root\\path/with\\mixed/separators/test.txt",
            hidden = false,
            size = listOf(FileSize.Bytes(100)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.None
        )

        val actual = text { file(file, parent) }

        assertEquals("path/with/mixed/separators/test.txt (<0.1 KiB)", actual)
    }

    @Test
    fun `folder - handles complex path separators in relative path calculation`() {
        val parent = FileSystemEntry.Folder(
            name = "root",
            path = "/root",
            hidden = false,
            entries = null
        )
        val folder = FileSystemEntry.Folder(
            name = "sub",
            path = "/root\\path/with\\mixed/separators",
            hidden = false,
            entries = emptyList()
        )

        val actual = text { folder(folder, parent) }

        assertEquals("path/with/mixed/separators/", actual)
    }

    @Test
    fun `file - renders code block for uppercase extension and maps language id`() {
        val file = FileSystemEntry.File(
            name = "README.MD",
            extension = "MD",
            path = "/README.MD",
            hidden = false,
            size = listOf(FileSize.Bytes(42)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Text("Content in markdown")
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/README.MD (<0.1 KiB)",
            "Content:",
            "```markdown",
            "Content in markdown",
            "```"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - renders empty code block when content is whitespace and extension is code`() {
        val file = FileSystemEntry.File(
            name = "Empty.kt",
            extension = "KT",
            path = "/Empty.kt",
            hidden = false,
            size = listOf(FileSize.Bytes(0)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Text("   \n\n   ")
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/Empty.kt (0 bytes)",
            "Content:",
            "```kotlin",
            "```"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - renders JavaScript with correct language ID`() {
        val file = FileSystemEntry.File(
            name = "app.js",
            extension = "js",
            path = "/app.js",
            hidden = false,
            size = listOf(FileSize.Bytes(100)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Text("console.log('hello')")
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/app.js (<0.1 KiB)",
            "Content:",
            "```javascript",
            "console.log('hello')",
            "```"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - renders TypeScript with correct language ID`() {
        val file = FileSystemEntry.File(
            name = "app.ts",
            extension = "ts",
            path = "/app.ts",
            hidden = false,
            size = listOf(FileSize.Bytes(100)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Text("const x: string = 'hello'")
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/app.ts (<0.1 KiB)",
            "Content:",
            "```typescript",
            "const x: string = 'hello'",
            "```"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - renders Python with correct language ID`() {
        val file = FileSystemEntry.File(
            name = "main.py",
            extension = "py",
            path = "/main.py",
            hidden = false,
            size = listOf(FileSize.Bytes(100)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Text("print('hello')")
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/main.py (<0.1 KiB)",
            "Content:",
            "```python",
            "print('hello')",
            "```"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - renders C++ files with correct language ID`() {
        val extensions = listOf("cpp", "cxx", "cc", "hpp")

        extensions.forEach { ext ->
            val file = FileSystemEntry.File(
                name = "test.$ext",
                extension = ext,
                path = "/test.$ext",
                hidden = false,
                size = listOf(FileSize.Bytes(100)),
                contentType = FileMetadata.FileContentType.Text,
                content = FileSystemEntry.File.Content.Text("#include <iostream>")
            )

            val actual = text { file(file) }

            val expected = listOf(
                "/test.$ext (<0.1 KiB)",
                "Content:",
                "```cpp",
                "#include <iostream>",
                "```"
            ).joinToString("\n")

            assertEquals(expected, actual, "Failed for extension: $ext")
        }
    }

    @Test
    fun `file - renders unknown code extension with lowercase`() {
        val file = FileSystemEntry.File(
            name = "config.toml",
            extension = "toml",
            path = "/config.toml",
            hidden = false,
            size = listOf(FileSize.Bytes(100)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Text("[package]\nname = 'test'")
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/config.toml (<0.1 KiB)",
            "Content:",
            "```toml",
            "[package]\nname = 'test'",
            "```"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - renders non-code file without code block`() {
        val file = FileSystemEntry.File(
            name = "notes.unknown",
            extension = "unknown",
            path = "/notes.unknown",
            hidden = false,
            size = listOf(FileSize.Bytes(100)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Text("Just some text")
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/notes.unknown (<0.1 KiB)",
            "Content:",
            "Just some text"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - renders with null extension as plain text`() {
        val file = FileSystemEntry.File(
            name = "noext",
            extension = null,
            path = "/noext",
            hidden = false,
            size = listOf(FileSize.Bytes(100)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Text("Plain text content")
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/noext (<0.1 KiB)",
            "Content:",
            "Plain text content"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `file - preserves newlines in text content`() {
        val file = FileSystemEntry.File(
            name = "multiline.txt",
            extension = "txt",
            path = "/multiline.txt",
            hidden = false,
            size = listOf(FileSize.Bytes(100)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.Text("line1\nline2\nline3")
        )

        val actual = text { file(file) }

        val expected = listOf(
            "/multiline.txt (<0.1 KiB)",
            "Content:",
            "line1\nline2\nline3"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `folder - renders with full path when no parent`() {
        val folder = FileSystemEntry.Folder(
            name = "project",
            path = "/home/user/project",
            hidden = false,
            entries = emptyList()
        )

        val actual = text { folder(folder) }

        assertEquals("/home/user/project/", actual)
    }

    @Test
    fun `folder - renders with relative path when parent provided`() {
        val parent = FileSystemEntry.Folder(
            name = "user",
            path = "/home/user",
            hidden = false,
            entries = null
        )
        val folder = FileSystemEntry.Folder(
            name = "project",
            path = "/home/user/documents/project",
            hidden = false,
            entries = emptyList()
        )

        val actual = text { folder(folder, parent) }

        assertEquals("documents/project/", actual)
    }

    @Test
    fun `folder - shows name when path equals parent path`() {
        val parent = FileSystemEntry.Folder("proj", "/a/b/proj", false, emptyList())
        val actual = text { folder(parent, parent) }
        assertEquals("proj/", actual)
    }

    @Test
    fun `folder - renders hidden folder with metadata`() {
        val folder = FileSystemEntry.Folder(
            name = ".git",
            path = "/project/.git",
            hidden = true,
            entries = emptyList()
        )

        val actual = text { folder(folder) }

        assertEquals("/project/.git/ (hidden)", actual)
    }

    @Test
    fun `folder - renders with null entries`() {
        val folder = FileSystemEntry.Folder(
            name = "unknown",
            path = "/unknown",
            hidden = false,
            entries = null
        )

        val actual = text { folder(folder) }

        assertEquals("/unknown/", actual)
    }

    @Test
    fun `folder - collapses single file entry`() {
        val only = FileSystemEntry.File(
            name = "only.txt",
            extension = "txt",
            path = "/root/only.txt",
            hidden = false,
            size = listOf(FileSize.Bytes(0), FileSize.Lines(0)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.None
        )
        val folder = FileSystemEntry.Folder(
            name = "root",
            path = "/root",
            hidden = false,
            entries = listOf(only)
        )

        val actual = text { folder(folder) }

        assertEquals("/root/only.txt (0 bytes, 0 lines)", actual)
    }

    @Test
    fun `folder - collapses single folder entry`() {
        val subfolder = FileSystemEntry.Folder(
            name = "sub",
            path = "/parent/sub",
            hidden = false,
            entries = emptyList()
        )
        val parent = FileSystemEntry.Folder(
            name = "parent",
            path = "/parent",
            hidden = false,
            entries = listOf(subfolder)
        )

        val actual = text { folder(parent) }

        assertEquals("/parent/sub/", actual)
    }

    @Test
    fun `folder - renders multiple entries with proper indentation`() {
        val child1 = FileSystemEntry.File(
            name = "readme.txt",
            extension = "txt",
            path = "/repo/project/readme.txt",
            hidden = false,
            size = listOf(FileSize.Bytes(100), FileSize.Lines(5)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.None
        )
        val subFolder = FileSystemEntry.Folder(
            name = "src",
            path = "/repo/project/src",
            hidden = true,
            entries = emptyList()
        )
        val root = FileSystemEntry.Folder(
            name = "project",
            path = "/repo/project",
            hidden = false,
            entries = listOf(child1, subFolder)
        )

        val actual = text { folder(root) }

        val expected = listOf(
            "/repo/project/",
            "  readme.txt (<0.1 KiB, 5 lines)",
            "  src/ (hidden)"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `folder - collapses nested structure with single entries`() {
        val deepFile = FileSystemEntry.File(
            name = "deep.txt",
            extension = "txt",
            path = "/root/level1/level2/deep.txt",
            hidden = false,
            size = listOf(FileSize.Bytes(64)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.None
        )
        val level2 = FileSystemEntry.Folder(
            name = "level2",
            path = "/root/level1/level2",
            hidden = false,
            entries = listOf(deepFile)
        )
        val level1 = FileSystemEntry.Folder(
            name = "level1",
            path = "/root/level1",
            hidden = false,
            entries = listOf(level2)
        )
        val root = FileSystemEntry.Folder(
            name = "root",
            path = "/root",
            hidden = false,
            entries = listOf(level1)
        )

        val actual = text { folder(root) }

        assertEquals("/root/level1/level2/deep.txt (<0.1 KiB)", actual)
    }

    @Test
    fun `folder - renders nested structure with multiple entries and proper indentation`() {
        val deepFile1 = FileSystemEntry.File(
            name = "deep1.txt",
            extension = "txt",
            path = "/root/level1/level2/deep1.txt",
            hidden = false,
            size = listOf(FileSize.Bytes(64)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.None
        )
        val deepFile2 = FileSystemEntry.File(
            name = "deep2.txt",
            extension = "txt",
            path = "/root/level1/level2/deep2.txt",
            hidden = false,
            size = listOf(FileSize.Bytes(128)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.None
        )
        val level2 = FileSystemEntry.Folder(
            name = "level2",
            path = "/root/level1/level2",
            hidden = false,
            entries = listOf(deepFile1, deepFile2)
        )
        val level1File = FileSystemEntry.File(
            name = "config.txt",
            extension = "txt",
            path = "/root/level1/config.txt",
            hidden = false,
            size = listOf(FileSize.Bytes(32)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.None
        )
        val level1 = FileSystemEntry.Folder(
            name = "level1",
            path = "/root/level1",
            hidden = false,
            entries = listOf(level1File, level2)
        )
        val root = FileSystemEntry.Folder(
            name = "root",
            path = "/root",
            hidden = false,
            entries = listOf(level1)
        )

        val actual = text { folder(root) }

        val expected = listOf(
            "/root/level1/",
            "  config.txt (<0.1 KiB)",
            "  level2/",
            "    deep1.txt (<0.1 KiB)",
            "    deep2.txt (0.1 KiB)"
        ).joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `entry - delegates to file for file entries`() {
        val file = FileSystemEntry.File(
            name = "test.kt",
            extension = "kt",
            path = "/test.kt",
            hidden = false,
            size = listOf(FileSize.Bytes(100)),
            contentType = FileMetadata.FileContentType.Text,
            content = FileSystemEntry.File.Content.None
        )

        val actual = text { entry(file) }

        assertEquals("/test.kt (<0.1 KiB)", actual)
    }

    @Test
    fun `entry - delegates to folder for folder entries`() {
        val folder = FileSystemEntry.Folder(
            name = "src",
            path = "/src",
            hidden = false,
            entries = emptyList()
        )

        val actual = text { entry(folder) }

        assertEquals("/src/", actual)
    }
}
