package ai.koog.agents.file.tools.model

import ai.koog.rag.base.files.DocumentProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileSystemEntryTest {

    @Test
    fun `Content - Text stores full text`() {
        val text = FileSystemEntry.File.Content.Text("test content")
        assertEquals("test content", text.text)
    }

    @Test
    fun `Content - Excerpt with single snippet`() {
        val snippet =
            FileSystemEntry.File.Content.Excerpt.Snippet(
                "snippet text",
                DocumentProvider.DocumentRange(
                    DocumentProvider.Position(0, 0),
                    DocumentProvider.Position(1, 0),
                ),
            )
        val excerpt = FileSystemEntry.File.Content.Excerpt(listOf(snippet))

        assertEquals(1, excerpt.snippets.size)
        assertEquals(snippet, excerpt.snippets[0])
    }

    @Test
    fun `Content - Excerpt vararg constructor`() {
        val snippet1 =
            FileSystemEntry.File.Content.Excerpt.Snippet(
                "snippet1",
                DocumentProvider.DocumentRange(
                    DocumentProvider.Position(0, 0),
                    DocumentProvider.Position(1, 0),
                ),
            )
        val snippet2 =
            FileSystemEntry.File.Content.Excerpt.Snippet(
                "snippet2",
                DocumentProvider.DocumentRange(
                    DocumentProvider.Position(2, 0),
                    DocumentProvider.Position(3, 0),
                ),
            )

        val excerpt = FileSystemEntry.File.Content.Excerpt(snippet1, snippet2)

        assertEquals(2, excerpt.snippets.size)
        assertEquals(snippet1, excerpt.snippets[0])
        assertEquals(snippet2, excerpt.snippets[1])
    }

    @Test
    fun `Content of Text when selecting full range -1`() {
        val content = "first\nsecond\nthird"
        val result = FileSystemEntry.File.Content.of(content, startLine = 0, endLine = -1)

        assertTrue(result is FileSystemEntry.File.Content.Text)
        assertEquals(content, result.text)
    }

    @Test
    fun `Content_of - Text when range covers all lines exactly`() {
        val content = "alpha\nbeta\ngamma"
        val lineCount = content.lines().size

        val result = FileSystemEntry.File.Content.of(content, startLine = 0, endLine = lineCount)

        assertTrue(result is FileSystemEntry.File.Content.Text)
        assertEquals(content, result.text)
    }

    @Test
    fun `Content_of - Excerpt for partial line range`() {
        val content = "line0\nline1\nline2\nline3"

        val result = FileSystemEntry.File.Content.of(content, startLine = 1, endLine = 3)

        assertTrue(result is FileSystemEntry.File.Content.Excerpt)
        assertEquals(1, result.snippets.size)

        val snippet = result.snippets[0]
        assertEquals("line1\nline2\n", snippet.text)
        assertEquals(1, snippet.range.start.line)
        assertEquals(0, snippet.range.start.column)
        assertEquals(3, snippet.range.end.line)
        assertEquals(0, snippet.range.end.column)
    }

    @Test
    fun `Content_of - Excerpt for single line selection`() {
        val content = "first\nsecond\nthird"

        val result = FileSystemEntry.File.Content.of(content, startLine = 1, endLine = 2)

        assertTrue(result is FileSystemEntry.File.Content.Excerpt)
        assertEquals("second\n", result.snippets[0].text)
    }

    @Test
    fun `Content_of - Excerpt middle to end`() {
        val content = "start\nmiddle\nend"

        val result = FileSystemEntry.File.Content.of(content, startLine = 1, endLine = -1)

        assertTrue(result is FileSystemEntry.File.Content.Excerpt)
        assertEquals("middle\nend", result.snippets[0].text)
    }

    @Test
    fun `Content_of handles empty content correctly`() {
        val result = FileSystemEntry.File.Content.of("", startLine = 0, endLine = -1)

        assertTrue(result is FileSystemEntry.File.Content.Text)
        assertEquals("", result.text)
    }

    @Test
    fun `Content_of handles single line without newline`() {
        val content = "one_line"
        val result = FileSystemEntry.File.Content.of(content, startLine = 0, endLine = -1)

        assertTrue(result is FileSystemEntry.File.Content.Text)
        assertEquals(content, result.text)
    }

    @Test
    fun `Content_of clamps range when endLine exceeds content bounds`() {
        val content = "a\nb"
        val result = FileSystemEntry.File.Content.of(content, startLine = 0, endLine = 100)

        assertTrue(result is FileSystemEntry.File.Content.Text)
        assertEquals(content, result.text)
    }

    @Test
    fun `Content_of throws IllegalArgumentException when startLine is negative`() {
        val content = "test\ncontent"

        val exception =
            assertFailsWith<IllegalArgumentException> {
                FileSystemEntry.File.Content.of(content, startLine = -1, endLine = -1)
            }
        assertTrue(exception.message!!.contains("startLine must be >= 0"))
    }

    @Test
    fun `Content_of throws IllegalArgumentException when endLine is less than minus one`() {
        val content = "test\ncontent"

        val exception =
            assertFailsWith<IllegalArgumentException> {
                FileSystemEntry.File.Content.of(content, startLine = 0, endLine = -5)
            }
        assertTrue(exception.message!!.contains("endLine must be >= -1"))
    }

    @Test
    fun `Content_of throws IllegalArgumentException when endLine not greater than startLine`() {
        val content = "test\ncontent"

        val exception =
            assertFailsWith<IllegalArgumentException> {
                FileSystemEntry.File.Content.of(content, startLine = 2, endLine = 1)
            }
        assertTrue(exception.message!!.contains("endLine must be > startLine"))
    }

    @Test
    fun `Content_of allows endLine minus one with any valid startLine`() {
        val content = "line1\nline2\nline3"

        // Should not throw
        FileSystemEntry.File.Content.of(content, startLine = 0, endLine = -1)
        FileSystemEntry.File.Content.of(content, startLine = 2, endLine = -1)
        assertTrue(true)
    }

    @Test
    fun `Content_of creates DocumentRange with correct positions`() {
        val content = "zero\none\ntwo\nthree"
        val result = FileSystemEntry.File.Content.of(content, startLine = 1, endLine = 3)

        assertTrue(result is FileSystemEntry.File.Content.Excerpt)
        val snippet = result.snippets[0]

        assertEquals(1, snippet.range.start.line)
        assertEquals(0, snippet.range.start.column)
        assertEquals(3, snippet.range.end.line)
        assertEquals(0, snippet.range.end.column)

        assertEquals(snippet.text, snippet.range.substring(content))
    }

    @Test
    fun `Content_of handles content ending without newline correctly`() {
        val content = "line1\nline2\nline3"
        val result = FileSystemEntry.File.Content.of(content, startLine = 2, endLine = -1)

        assertTrue(result is FileSystemEntry.File.Content.Excerpt)
        assertEquals("line3", result.snippets[0].text)
    }

    @Test
    fun `Content_of handles whitespace only lines`() {
        val content = "  \n\t\n   "
        val result = FileSystemEntry.File.Content.of(content, startLine = 1, endLine = 2)

        assertTrue(result is FileSystemEntry.File.Content.Excerpt)
        assertEquals("\t\n", result.snippets[0].text)
    }

    @Test
    fun `Content - Text without trailing newline`() {
        val content = "Hello world!"
        val text = FileSystemEntry.File.Content.Text(content)
        assertEquals(content, text.text)
    }

    @Test
    fun `Content - Excerpt with multiple snippets using list`() {
        val snippet1 =
            FileSystemEntry.File.Content.Excerpt.Snippet(
                text = "part1\n",
                range =
                DocumentProvider.DocumentRange(
                    DocumentProvider.Position(0, 0),
                    DocumentProvider.Position(1, 0),
                ),
            )
        val snippet2 =
            FileSystemEntry.File.Content.Excerpt.Snippet(
                text = "part2\n",
                range =
                DocumentProvider.DocumentRange(
                    DocumentProvider.Position(2, 0),
                    DocumentProvider.Position(3, 0),
                ),
            )

        val excerpt = FileSystemEntry.File.Content.Excerpt(listOf(snippet1, snippet2))

        assertEquals(2, excerpt.snippets.size)
        assertEquals(snippet1, excerpt.snippets[0])
        assertEquals(snippet2, excerpt.snippets[1])
    }
}
