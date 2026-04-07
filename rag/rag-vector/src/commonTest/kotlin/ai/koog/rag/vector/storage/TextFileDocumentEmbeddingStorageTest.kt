package ai.koog.rag.vector.storage

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import ai.koog.rag.vector.mocks.MockDocument
import ai.koog.rag.vector.mocks.MockDocumentProvider
import ai.koog.rag.vector.mocks.MockFileSystem
import ai.koog.rag.vector.mocks.MockFileSystemProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TextFileDocumentEmbeddingStorageTest {

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

    private fun createTestStorage(): TextFileDocumentEmbeddingStorage<MockDocument, String> {
        val mockFileSystem = MockFileSystem()
        val mockDocumentProvider = MockDocumentProvider(mockFileSystem)
        val mockFileSystemProvider = MockFileSystemProvider(mockFileSystem)
        val mockEmbedder = MockEmbedder()

        return TextFileDocumentEmbeddingStorage(mockEmbedder, mockDocumentProvider, mockFileSystemProvider, "test-root")
    }

    @Test
    fun testStoreAndRead() = runTest {
        val storage = createTestStorage()
        val document = MockDocument("test document")

        // Store document
        val documentId = storage.add(listOf(document)).first()
        assertNotNull(documentId)

        // Read document back
        val retrievedDocument = storage.get(listOf(documentId)).firstOrNull()
        assertEquals(document, retrievedDocument)
    }

    @Test
    fun testDelete() = runTest {
        val storage = createTestStorage()
        val document = MockDocument("test document")

        // Store document
        val documentId = storage.add(listOf(document)).first()

        // Verify it exists
        assertNotNull(storage.get(listOf(documentId)).firstOrNull())

        // Delete it
        val deletedIds = storage.delete(listOf(documentId))
        assertEquals(listOf(documentId), deletedIds)

        // Verify it's gone
        assertEquals(null, storage.get(listOf(documentId)).firstOrNull())
    }

    @Test
    fun testAllDocuments() = runTest {
        val storage = createTestStorage()
        val documents = listOf(MockDocument("doc1"), MockDocument("doc2"), MockDocument("doc3"))
        val documentIds = mutableListOf<String>()

        // Store multiple documents
        documentIds.addAll(storage.add(documents))

        // Retrieve all documents
        val allDocs = storage.allDocuments().toList()
        assertEquals(documents.size, allDocs.size)
        assertTrue(allDocs.containsAll(documents))
    }

    @Test
    fun testRankDocuments() = runTest {
        val storage = createTestStorage()
        val documents = listOf(MockDocument("hello world"), MockDocument("goodbye world"), MockDocument("hello universe"))
        val documentIds = mutableListOf<String>()

        // Store documents
        documentIds.addAll(storage.add(documents))

        // Rank documents by similarity to "hello"
        val rankedDocs = storage.search(SimilaritySearchRequest(queryText = "hello", limit = Int.MAX_VALUE))
        assertEquals(documents.size, rankedDocs.size)

        // All documents should have a similarity score
        rankedDocs.forEach { rankedDoc ->
            assertNotNull(rankedDoc.document)
            assertTrue(rankedDoc.score.value >= 0.0)
        }

        // Documents containing "hello" should be more similar (lower distance)
        val helloDocuments = rankedDocs.filter { it.document.content.contains("hello") }
        val nonHelloDocuments = rankedDocs.filter { !it.document.content.contains("hello") }

        if (helloDocuments.isNotEmpty() && nonHelloDocuments.isNotEmpty()) {
            val avgHelloSimilarity = helloDocuments.map { it.score.value }.average()
            val avgNonHelloSimilarity = nonHelloDocuments.map { it.score.value }.average()
            assertTrue(avgHelloSimilarity > avgNonHelloSimilarity, "Documents with 'hello' should be more similar")
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
        val documents = listOf(
            MockDocument("first document"),
            MockDocument("second document"),
            MockDocument("third document")
        )
        val documentIds = mutableListOf<String>()

        // Store multiple documents
        documentIds.addAll(storage.add(documents))

        // Verify all documents can be retrieved
        documentIds.zip(documents).forEach { (id, expectedDoc) ->
            val retrievedDoc = storage.get(listOf(id)).firstOrNull()
            assertEquals(expectedDoc, retrievedDoc)
        }
    }

    @Test
    fun testTextEmbeddingFunctionality() = runTest {
        val storage = createTestStorage()
        val documents = listOf(MockDocument("apple"), MockDocument("banana"), MockDocument("cherry"))
        val documentIds = mutableListOf<String>()

        // Store documents
        documentIds.addAll(storage.add(documents))

        // Test ranking with different queries
        val appleRanked = storage.search(SimilaritySearchRequest(queryText = "apple", limit = Int.MAX_VALUE))
        val bananaRanked = storage.search(SimilaritySearchRequest(queryText = "banana", limit = Int.MAX_VALUE))

        assertEquals(documents.size, appleRanked.size)
        assertEquals(documents.size, bananaRanked.size)

        // The exact document should have the best similarity (lowest distance)
        val bestAppleMatch = appleRanked.maxBy { it.score.value }
        val bestBananaMatch = bananaRanked.maxBy { it.score.value }

        assertEquals("apple", bestAppleMatch.document.content)
        assertEquals("banana", bestBananaMatch.document.content)
    }
}
