package ai.koog.rag.vector

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JVMTextDocumentEmbedderTest {

    // Simple mock implementation of Embedder for testing
    private class MockEmbedder : Embedder {
        override suspend fun embed(text: String): Vector {
            // Simple embedding: convert each character to its ASCII value and use as vector values
            return Vector(text.map { it.code.toDouble() })
        }

        override fun diff(embedding1: Vector, embedding2: Vector): Double {
            // Simple Euclidean distance
            return embedding1.values.zip(embedding2.values) { a, b -> (a - b) * (a - b) }.sum()
        }
    }

    private fun createTestEmbedder(): JVMTextDocumentEmbedder {
        val mockEmbedder = MockEmbedder()
        return JVMTextDocumentEmbedder(mockEmbedder)
    }

    private fun createTestFile(content: String): Path {
        val tempFile = Files.createTempFile("test-doc", ".txt")
        Files.write(tempFile, content.toByteArray())
        return tempFile
    }

    @Test
    fun testEmbedDocument() = runTest {
        val embedder = createTestEmbedder()
        val testFile = createTestFile("hello world")

        try {
            // Embed document
            val vector = embedder.embed(testFile)

            // Verify vector is created
            assertEquals("hello world".length, vector.values.size)

            // Verify vector values correspond to character codes
            val expectedValues = "hello world".map { it.code.toDouble() }
            assertEquals(expectedValues, vector.values)
        } finally {
            Files.deleteIfExists(testFile)
        }
    }

    @Test
    fun testEmbedText() = runTest {
        val embedder = createTestEmbedder()
        val text = "test text"

        // Embed text
        val vector = embedder.embed(text)

        // Verify vector is created
        assertEquals(text.length, vector.values.size)

        // Verify vector values correspond to character codes
        val expectedValues = text.map { it.code.toDouble() }
        assertEquals(expectedValues, vector.values)
    }

    @Test
    fun testDiff() = runTest {
        val embedder = createTestEmbedder()
        val vector1 = Vector(listOf(1.0, 2.0, 3.0))
        val vector2 = Vector(listOf(1.0, 2.0, 3.0))
        val vector3 = Vector(listOf(4.0, 5.0, 6.0))

        // Test identical vectors
        val diff1 = embedder.diff(vector1, vector2)
        assertEquals(0.0, diff1)

        // Test different vectors
        val diff2 = embedder.diff(vector1, vector3)
        assertTrue(diff2 > 0.0)

        // Test expected difference calculation
        val expectedDiff = (1.0 - 4.0) * (1.0 - 4.0) + (2.0 - 5.0) * (2.0 - 5.0) + (3.0 - 6.0) * (3.0 - 6.0)
        assertEquals(expectedDiff, diff2)
    }

    @Test
    fun testEmbedEmptyDocument() = runTest {
        val embedder = createTestEmbedder()
        val emptyFile = createTestFile("")

        try {
            // Embed empty document
            val vector = embedder.embed(emptyFile)

            // Verify empty vector is created
            assertEquals(0, vector.values.size)
        } finally {
            Files.deleteIfExists(emptyFile)
        }
    }

    @Test
    fun testEmbedEmptyText() = runTest {
        val embedder = createTestEmbedder()
        val emptyText = ""

        // Embed empty text
        val vector = embedder.embed(emptyText)

        // Verify empty vector is created
        assertEquals(0, vector.values.size)
    }

    @Test
    fun testEmbedLargeDocument() = runTest {
        val embedder = createTestEmbedder()
        val largeContent = "This is a large document with many words. ".repeat(100)
        val largeFile = createTestFile(largeContent)

        try {
            // Embed large document
            val vector = embedder.embed(largeFile)

            // Verify vector size matches content length
            assertEquals(largeContent.length, vector.values.size)

            // Verify first few values
            val expectedFirstValues = largeContent.take(10).map { it.code.toDouble() }
            assertEquals(expectedFirstValues, vector.values.take(10))
        } finally {
            Files.deleteIfExists(largeFile)
        }
    }

    @Test
    fun testConsistentEmbedding() = runTest {
        val embedder = createTestEmbedder()
        val content = "consistent test content"
        val testFile = createTestFile(content)

        try {
            // Embed the same document multiple times
            val vector1 = embedder.embed(testFile)
            val vector2 = embedder.embed(testFile)

            // Verify embeddings are identical
            assertEquals(vector1, vector2)

            // Verify difference is zero
            val diff = embedder.diff(vector1, vector2)
            assertEquals(0.0, diff)
        } finally {
            Files.deleteIfExists(testFile)
        }
    }

    @Test
    fun testDocumentVsTextEmbedding() = runTest {
        val embedder = createTestEmbedder()
        val content = "test content for comparison"
        val testFile = createTestFile(content)

        try {
            // Embed document and text separately
            val documentVector = embedder.embed(testFile)
            val textVector = embedder.embed(content)

            // Verify they produce the same result
            assertEquals(documentVector, textVector)

            // Verify difference is zero
            val diff = embedder.diff(documentVector, textVector)
            assertEquals(0.0, diff)
        } finally {
            Files.deleteIfExists(testFile)
        }
    }

    @Test
    fun testSpecialCharacters() = runTest {
        val embedder = createTestEmbedder()
        val specialContent = "Hello! @#$%^&*()_+ 123 αβγ"
        val testFile = createTestFile(specialContent)

        try {
            // Embed document with special characters
            val vector = embedder.embed(testFile)

            // Verify vector size
            assertEquals(specialContent.length, vector.values.size)

            // Verify some specific character codes
            assertEquals('H'.code.toDouble(), vector.values[0])
            assertEquals('!'.code.toDouble(), vector.values[5])
            assertEquals('@'.code.toDouble(), vector.values[7])
        } finally {
            Files.deleteIfExists(testFile)
        }
    }
}
