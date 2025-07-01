package ai.koog.rag.base.files

import ai.koog.rag.base.files.DocumentProvider.DocumentRange
import ai.koog.rag.base.files.DocumentProvider.Position
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals

class JVMDocumentProviderTest : KoogTestBase() {

    @Test
    fun `test document method returns the path`() {
        runBlocking {
            val result = JVMDocumentProvider.document(file1)
            assertEquals(file1, result)
        }
    }

    @Test
    fun `test text method returns the content of the file`() {
        runBlocking {
            val content = JVMDocumentProvider.text(file1)
            assertEquals(testCode, content.toString())
        }
    }

    @Test
    fun `test text method normalizes CRLF line endings`() {
        runBlocking {
            val testFile = tempDir.resolve("crlf.txt")
            testFile.writeText("Line1\r\nLine2\r\nLine3")

            val content = JVMDocumentProvider.text(testFile)
            assertEquals("Line1\nLine2\nLine3", content.toString())
        }
    }

    @Test
    fun `test text method normalizes CR line endings`() {
        runBlocking {
            val testFile = tempDir.resolve("cr.txt")
            testFile.writeText("Line1\rLine2\rLine3")

            val content = JVMDocumentProvider.text(testFile)
            assertEquals("Line1\nLine2\nLine3", content.toString())
        }
    }

    @Test
    fun `test setText method replaces entire file content`() {
        runBlocking {
            val testFile = tempDir.resolve("setText.txt")
            testFile.writeText("Original content")

            JVMDocumentProvider.Edit.setText(testFile, "New content", null)

            val newContent = testFile.readText()
            assertEquals("New content", newContent.normalizeForAssertion())
        }
    }

    @Test
    fun `test setText method replaces content in range`() {
        runBlocking {
            val testFile = tempDir.resolve("setTextRange.txt")
            testFile.writeText("Hello\nWorld")

            val range = DocumentRange(
                Position(0, 0),
                Position(0, 5)
            )

            JVMDocumentProvider.Edit.setText(testFile, "Goodbye", range)

            val newContent = testFile.readText()
            assertEquals("Goodbye\nWorld\n", newContent.normalizeForAssertion())
        }
    }

    @Test
    fun `test setText method with multiline range`() {
        runBlocking {
            val testFile = tempDir.resolve("setTextMultiline.txt")
            testFile.writeText("Hello\nWorld\nTest")

            val range = DocumentRange(
                Position(0, 0),
                Position(1, 5)
            )

            JVMDocumentProvider.Edit.setText(testFile, "Replaced", range)

            val newContent = testFile.readText()
            assertEquals("Replaced\nTest\n", newContent.normalizeForAssertion())
        }
    }

    @Test
    fun `test setText method with empty text`() {
        runBlocking {
            val testFile = tempDir.resolve("setTextEmpty.txt")
            testFile.writeText("Hello\nWorld")

            val range = DocumentRange(
                Position(0, 0),
                Position(0, 5)
            )

            JVMDocumentProvider.Edit.setText(testFile, "", range)

            val newContent = testFile.readText()
            assertEquals("\nWorld\n", newContent.normalizeForAssertion())
        }
    }

    @Test
    fun `test setText method with invalid range throws exception`() {
        runBlocking {
            val testFile = tempDir.resolve("setTextInvalid.txt")
            testFile.writeText("Hello\nWorld")

            val range = DocumentRange(
                Position(0, 10),
                Position(0, 5)
            )

            assertThrows<IllegalArgumentException> {
                JVMDocumentProvider.Edit.setText(testFile, "test", range)
            }
        }
    }

    @Test
    fun `test text method with empty file`() {
        runBlocking {
            val emptyFile = tempDir.resolve("empty.txt")
            emptyFile.writeText("")

            val content = JVMDocumentProvider.text(emptyFile)
            assertEquals("", content.toString())
        }
    }

    @Test
    fun `test setText method with empty file`() {
        runBlocking {
            val emptyFile = tempDir.resolve("emptySetText.txt")
            emptyFile.writeText("")

            JVMDocumentProvider.Edit.setText(emptyFile, "New content", null)

            val newContent = emptyFile.readText()
            assertEquals("New content", newContent.normalizeForAssertion())
        }
    }

    @Test
    fun `test setText method with file containing only newlines`() {
        runBlocking {
            val newlinesFile = tempDir.resolve("newlines.txt")
            newlinesFile.writeText("\n\n\n")

            val range = DocumentRange(
                Position(1, 0),
                Position(1, 0)
            )

            JVMDocumentProvider.Edit.setText(newlinesFile, "Inserted", range)

            val newContent = newlinesFile.readText()
            assertEquals("\nInserted\n\n", newContent.normalizeForAssertion())
        }
    }

    @Test
    fun `test setText method modifying last character of file`() {
        runBlocking {
            val testFile = tempDir.resolve("lastChar.txt")
            testFile.writeText("Hello")

            val range = DocumentRange(
                Position(0, 4),
                Position(0, 5)
            )

            JVMDocumentProvider.Edit.setText(testFile, "p", range)

            val newContent = testFile.readText()
            assertEquals("Hellp\n", newContent.normalizeForAssertion())
        }
    }
}