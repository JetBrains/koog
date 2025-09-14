package ai.koog.agents.ext.tool.file

import ai.koog.agents.ext.tool.file.model.FileSystemEntry
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ReadFileUtilJvmTest {

    private val fs = JVMFileSystemProvider.ReadOnly

    @TempDir
    lateinit var tempDir: Path

    private fun createTestFile(name: String = "test.txt", content: String): Path =
        tempDir.resolve(name).createFile().apply { writeText(content) }

    @Test
    fun `returns Text content when reading full file with -1`() = runTest {
        val file = createTestFile(content = "line1\nline2\nline3")
        val metadata = assertNotNull(fs.metadata(file))

        val entry = buildTextFileEntry(fs, file, metadata, 0, -1)

        assertIs<FileSystemEntry.File.Content.Text>(entry.content)
        assertEquals("line1\nline2\nline3", entry.content.text)
    }

    @Test
    fun `returns Text content when reading full file with exact line count`() = runTest {
        val file = createTestFile(content = "line1\nline2\nline3")
        val metadata = assertNotNull(fs.metadata(file))

        val entry = buildTextFileEntry(fs, file, metadata, 0, 3)

        assertIs<FileSystemEntry.File.Content.Text>(entry.content)
        assertEquals("line1\nline2\nline3", entry.content.text)
    }

    @Test
    fun `returns Excerpt content for partial range`() = runTest {
        val file = createTestFile(content = "line0\nline1\nline2\nline3")
        val metadata = assertNotNull(fs.metadata(file))

        val entry = buildTextFileEntry(fs, file, metadata, 1, 3)

        assertIs<FileSystemEntry.File.Content.Excerpt>(entry.content)
        val snippet = entry.content.snippets.single()
        assertEquals("line1\nline2", snippet.text.trim())
        assertEquals(1, snippet.range.start.line)
        assertEquals(3, snippet.range.end.line)
    }

    @Test
    fun `handles single line file with full content`() = runTest {
        val file = createTestFile(content = "single line")
        val metadata = assertNotNull(fs.metadata(file))

        val entry = buildTextFileEntry(fs, file, metadata, 0, -1)

        assertIs<FileSystemEntry.File.Content.Text>(entry.content)
        assertEquals("single line", entry.content.text)
    }

    @Test
    fun `sets file metadata correctly`() = runTest {
        val file = createTestFile(name = "document.md", content = "# Title")
        val metadata = assertNotNull(fs.metadata(file))

        val entry = buildTextFileEntry(fs, file, metadata, 0, -1)

        assertEquals("document.md", entry.name)
        assertEquals("md", entry.extension)
        assertEquals(file.toAbsolutePath().toString(), entry.path)
        assertEquals(metadata.hidden, entry.hidden)
    }

    @Test
    fun `throws when startLine is negative`() = runTest {
        val file = createTestFile(content = "content")
        val metadata = assertNotNull(fs.metadata(file))

        assertThrows<IllegalArgumentException> {
            buildTextFileEntry(fs, file, metadata, -1, 1)
        }
    }

    @Test
    fun `throws for invalid endLine less than -1`() = runTest {
        val file = createTestFile(content = "content")
        val metadata = assertNotNull(fs.metadata(file))

        assertThrows<IllegalArgumentException> {
            buildTextFileEntry(fs, file, metadata, 0, -2)
        }
    }

    @Test
    fun `throws when endLine equals startLine`() = runTest {
        val file = createTestFile(content = "line1\nline2")
        val metadata = assertNotNull(fs.metadata(file))

        assertThrows<IllegalArgumentException> {
            buildTextFileEntry(fs, file, metadata, 1, 1)
        }
    }

    @Test
    fun `throws when endLine is less than startLine`() = runTest {
        val file = createTestFile(content = "line1\nline2\nline3")
        val metadata = assertNotNull(fs.metadata(file))

        assertThrows<IllegalArgumentException> {
            buildTextFileEntry(fs, file, metadata, 2, 1)
        }
    }

    @Test
    fun `throws when startLine equals line count`() = runTest {
        val file = createTestFile(content = "a\nb\nc")
        val metadata = assertNotNull(fs.metadata(file))

        assertThrows<IllegalArgumentException> {
            buildTextFileEntry(fs, file, metadata, 3, -1)
        }
    }

    @Test
    fun `throws when startLine exceeds line count`() = runTest {
        val file = createTestFile(content = "line1\nline2")
        val metadata = assertNotNull(fs.metadata(file))

        assertThrows<IllegalArgumentException> {
            buildTextFileEntry(fs, file, metadata, 5, -1)
        }
    }

    @Test
    fun `throws when startLine is beyond single line file`() = runTest {
        val file = createTestFile(content = "single")
        val metadata = assertNotNull(fs.metadata(file))

        assertThrows<IllegalArgumentException> {
            buildTextFileEntry(fs, file, metadata, 1, 2)
        }
    }

    @Test
    fun `throws when endLine exceeds line`() = runTest {
        val file = createTestFile(content = "line1\nline2")
        val metadata = assertNotNull(fs.metadata(file))

        assertThrows<IllegalArgumentException> {
            buildTextFileEntry(fs, file, metadata, 0, 50)
        }
    }

    @Test
    fun `throws when file is empty`() = runTest {
        val file = createTestFile(content = "")
        val metadata = assertNotNull(fs.metadata(file))

        assertThrows<IllegalArgumentException> {
            buildTextFileEntry(fs, file, metadata, 0, 0)
        }
    }
}
