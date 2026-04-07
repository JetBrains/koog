package ai.koog.rag.vector.storage

import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import ai.koog.rag.vector.embedder.DocumentEmbedder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JVMFileDocumentEmbeddingStorageTest {

    // Simple mock implementation of DocumentEmbedder for testing
    private class MockDocumentEmbedder : DocumentEmbedder<Path> {
        override suspend fun embed(document: Path): Vector {
            // Simple embedding: read file content and convert each character to its ASCII value
            val content = Files.readString(document)
            return embed(content)
        }

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

    private fun createTestStorage(): JVMFileDocumentEmbeddingStorage {
        val tempDir = Files.createTempDirectory("jvm-doc-embedding-storage-test")
        val mockEmbedder = MockDocumentEmbedder()
        return JVMFileDocumentEmbeddingStorage(mockEmbedder, tempDir)
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
            assertEquals(Files.readString(testFile), Files.readString(retrievedDocument))
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
        val fileContents = testFiles.map { Files.readString(it) }

        try {
            // Store multiple documents
            storage.add(testFiles)

            // Retrieve all documents
            val allDocs = storage.allDocuments().toList()
            assertEquals(testFiles.size, allDocs.size)

            // Check that all contents are present
            val allContents = allDocs.map { Files.readString(it) }
            fileContents.forEach { content ->
                assertTrue(allContents.contains(content), "Content '$content' should be in retrieved documents")
            }
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

        // Rank documents in empty storage
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
        val fileContents = testFiles.map { Files.readString(it) }

        try {
            // Store multiple documents
            documentIds.addAll(storage.add(testFiles))

            // Verify all documents can be retrieved
            documentIds.zip(fileContents).forEach { (id, expectedContent) ->
                val retrievedFile = storage.get(listOf(id)).firstOrNull()
                assertNotNull(retrievedFile)
                assertEquals(expectedContent, Files.readString(retrievedFile))
            }
        } finally {
            testFiles.forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun testFileContentEmbedding() = runTest {
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
}
