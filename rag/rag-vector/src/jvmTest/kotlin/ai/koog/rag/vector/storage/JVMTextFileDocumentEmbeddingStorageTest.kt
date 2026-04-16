package ai.koog.rag.vector.storage

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JVMTextFileDocumentEmbeddingStorageTest {

    // Simple mock implementation of Embedder for testing
    private class MockEmbedder : Embedder {
        override suspend fun embed(text: String): Vector {
            // Simple embedding: convert each word to it's hash code
            return Vector(text.split(" ").map { it.hashCode().toDouble() })
        }

        override fun diff(embedding1: Vector, embedding2: Vector): Double {
            // Number of intersecting elements (words) in 2 texts
            val intersectionsSize = embedding1.values.count { it in embedding2.values }
            val totalSize = embedding1.values.size + embedding2.values.size
            return 1.0 - 2.0 * intersectionsSize / totalSize
        }
    }

    private fun createTestStorage(): JVMFileEmbeddingStorage {
        val tempDir = Files.createTempDirectory("jvm-text-doc-embedding-storage-test")
        val mockEmbedder = MockEmbedder()
        return JVMFileEmbeddingStorage(mockEmbedder, tempDir)
    }

    private fun createTestFile(content: String): Path {
        val tempFile = Files.createTempFile("test-doc", ".txt")
        Files.write(tempFile, content.toByteArray())
        return tempFile
    }

    @Test
    fun testStoreAndRead() = runTest {
        val storage = createTestStorage()
        val testFile = createTestFile("test document content")

        try {
            // Store document
            val documentId = storage.add(listOf(testFile)).first()
            assertNotNull(documentId)

            // Read document back
            val retrievedDocument = storage.get(listOf(documentId)).firstOrNull()
            assertNotNull(retrievedDocument)

            // Compare content instead of file paths
            val originalContent = Files.readString(testFile)
            val retrievedContent = Files.readString(retrievedDocument)
            assertEquals(originalContent, retrievedContent)
        } finally {
            Files.deleteIfExists(testFile)
        }
    }

    @Test
    fun testDelete() = runTest {
        val storage = createTestStorage()
        val testFile = createTestFile("test document content")

        try {
            // Store document
            val documentId = storage.add(listOf(testFile)).first()

            // Verify it exists
            assertNotNull(storage.get(listOf(documentId)).firstOrNull())

            // Delete it
            val deletedIds = storage.delete(listOf(documentId))
            assertEquals(listOf(documentId), deletedIds)

            // Verify it's gone
            assertEquals(null, storage.get(listOf(documentId)).firstOrNull())
        } finally {
            Files.deleteIfExists(testFile)
        }
    }

    @Test
    fun testAllDocuments() = runTest {
        val storage = createTestStorage()
        val testFiles = listOf(
            createTestFile("document 1"),
            createTestFile("document 2"),
            createTestFile("document 3")
        )

        try {
            // Store multiple documents
            storage.add(testFiles)

            // Retrieve all documents
            val allDocs = storage.allDocuments().toList()
            assertEquals(testFiles.size, allDocs.size)

            // Compare contents instead of file paths
            val originalContents = testFiles.map { Files.readString(it) }.toSet()
            val retrievedContents = allDocs.map { Files.readString(it) }.toSet()
            assertEquals(originalContents, retrievedContents)
        } finally {
            testFiles.forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun testRankDocuments() = runTest {
        val storage = createTestStorage()
        val testFiles = listOf(
            createTestFile("hello world"),
            createTestFile("goodbye world"),
            createTestFile("hello universe")
        )

        try {
            // Store documents
            storage.add(testFiles)

            // Rank documents by similarity to "hello"
            val rankedDocs = storage.search(SimilaritySearchRequest(queryText = "hello", limit = Int.MAX_VALUE))
            assertEquals(testFiles.size, rankedDocs.size)

            // All documents should have a similarity score
            rankedDocs.forEach { rankedDoc ->
                assertNotNull(rankedDoc.document)
                assertTrue(rankedDoc.score.value >= 0.0)
            }

            // Documents containing "hello" should be more similar (lower distance)
            val helloDocuments = rankedDocs.filter {
                Files.readString(it.document).contains("hello")
            }
            val nonHelloDocuments = rankedDocs.filter {
                !Files.readString(it.document).contains("hello")
            }

            if (helloDocuments.isNotEmpty() && nonHelloDocuments.isNotEmpty()) {
                val avgHelloSimilarity = helloDocuments.map { it.score.value }.average()
                val avgNonHelloSimilarity = nonHelloDocuments.map { it.score.value }.average()
                assertTrue(avgHelloSimilarity > avgNonHelloSimilarity, "Documents with 'hello' should be more similar")
            }
        } finally {
            testFiles.forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun testRankDocumentsEmptyStorage() = runTest {
        val storage = createTestStorage()

        // Search documents in empty storage
        val rankedDocs = storage.search(SimilaritySearchRequest(queryText = "test query", limit = Int.MAX_VALUE))
        assertTrue(rankedDocs.isEmpty())
    }

    @Test
    fun testStoreMultipleDocuments() = runTest {
        val storage = createTestStorage()
        val testFiles = listOf(
            createTestFile("first document"),
            createTestFile("second document"),
            createTestFile("third document")
        )
        val documentIds = mutableListOf<String>()

        try {
            // Store multiple documents
            documentIds.addAll(storage.add(testFiles))

            // Verify all documents can be retrieved
            documentIds.zip(testFiles).forEach { (id, expectedFile) ->
                val retrievedFile = storage.get(listOf(id)).firstOrNull()
                assertNotNull(retrievedFile)

                // Compare content instead of file paths
                val originalContent = Files.readString(expectedFile)
                val retrievedContent = Files.readString(retrievedFile)
                assertEquals(originalContent, retrievedContent)
            }
        } finally {
            testFiles.forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun testTextEmbeddingFunctionality() = runTest {
        val storage = createTestStorage()
        val testFiles = listOf(
            createTestFile("apple"),
            createTestFile("banana"),
            createTestFile("cherry")
        )

        try {
            // Store documents
            storage.add(testFiles)

            // Test ranking with different queries
            val appleRanked = storage.search(SimilaritySearchRequest(queryText = "apple", limit = Int.MAX_VALUE))
            val bananaRanked = storage.search(SimilaritySearchRequest(queryText = "banana", limit = Int.MAX_VALUE))

            assertEquals(testFiles.size, appleRanked.size)
            assertEquals(testFiles.size, bananaRanked.size)

            // The exact document should have the best similarity (lowest distance)
            val bestAppleMatch = appleRanked.maxBy { it.score.value }
            val bestBananaMatch = bananaRanked.maxBy { it.score.value }

            assertEquals("apple", Files.readString(bestAppleMatch.document))
            assertEquals("banana", Files.readString(bestBananaMatch.document))
        } finally {
            testFiles.forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun testLargeTextDocuments() = runTest {
        val storage = createTestStorage()
        val largeText1 = "This is a large document with many words. ".repeat(100)
        val largeText2 = "Another large document with different content. ".repeat(100)
        val testFiles = listOf(
            createTestFile(largeText1),
            createTestFile(largeText2)
        )

        try {
            // Store documents
            storage.add(testFiles)

            // Test ranking
            val rankedDocs = storage.search(SimilaritySearchRequest(queryText = "large document", limit = Int.MAX_VALUE))
            assertEquals(testFiles.size, rankedDocs.size)

            // All documents should have similarity scores
            rankedDocs.forEach { rankedDoc ->
                assertNotNull(rankedDoc.document)
                assertTrue(rankedDoc.score.value >= 0.0)
            }
        } finally {
            testFiles.forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun testEmptyFileHandling() = runTest {
        val storage = createTestStorage()
        val emptyFile = createTestFile("")

        try {
            // Store empty document
            val documentId = storage.add(listOf(emptyFile)).first()
            assertNotNull(documentId)

            // Read document back
            val retrievedDocument = storage.get(listOf(documentId)).firstOrNull()
            assertNotNull(retrievedDocument)

            // Compare content instead of file paths
            val originalContent = Files.readString(emptyFile)
            val retrievedContent = Files.readString(retrievedDocument)
            assertEquals(originalContent, retrievedContent)

            // Test ranking with empty document
            val rankedDocs = storage.search(SimilaritySearchRequest(queryText = "test", limit = Int.MAX_VALUE))
            assertEquals(1, rankedDocs.size)
            assertNotNull(rankedDocs.first().document)
        } finally {
            Files.deleteIfExists(emptyFile)
        }
    }
}
