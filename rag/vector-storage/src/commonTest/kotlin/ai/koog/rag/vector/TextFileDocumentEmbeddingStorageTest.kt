package ai.koog.rag.vector

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.rag.vector.mocks.MockDocument
import ai.koog.rag.vector.mocks.MockDocumentProvider
import ai.koog.rag.vector.mocks.MockFileSystem
import ai.koog.rag.vector.mocks.MockFileSystemProvicer
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
            return 1.0 - 2.0 * intersectionsSize / totalSize;
        }
    }

    private suspend fun createTestStorage(): TextFileDocumentEmbeddingStorage<MockDocument, String> {
        val mockFileSystem = MockFileSystem()
        val mockDocumentProvider = MockDocumentProvider(mockFileSystem)
        val mockFileSystemProvicer = MockFileSystemProvicer(mockFileSystem)
        val mockEmbedder = MockEmbedder()

        return TextFileDocumentEmbeddingStorage(mockEmbedder, mockDocumentProvider, mockFileSystemProvicer, "test-root")
    }

    @Test
    fun testStoreAndRead() = runTest {
        val storage = createTestStorage()
        val document = MockDocument("test document")

        // Store document
        val documentId = storage.store(document, Unit)
        assertNotNull(documentId)

        // Read document back
        val retrievedDocument = storage.read(documentId)
        assertEquals(document, retrievedDocument)
    }

    @Test
    fun testDelete() = runTest {
        val storage = createTestStorage()
        val document = MockDocument("test document")

        // Store document
        val documentId = storage.store(document, Unit)

        // Verify it exists
        assertNotNull(storage.read(documentId))

        // Delete it
        val deleted = storage.delete(documentId)
        assertTrue(deleted)

        // Verify it's gone
        assertEquals(null, storage.read(documentId))
    }

    @Test
    fun testAllDocuments() = runTest {
        val storage = createTestStorage()
        val documents = listOf(MockDocument("doc1"), MockDocument("doc2"), MockDocument("doc3"))
        val documentIds = mutableListOf<String>()


        // Store multiple documents
        documents.forEach { doc ->
            val id = storage.store(doc, Unit)
            documentIds.add(id)
        }

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
        documents.forEach { doc ->
            val id = storage.store(doc, Unit)
            documentIds.add(id)
        }

        // Rank documents by similarity to "hello"
        val rankedDocs = storage.rankDocuments("hello").toList()
        assertEquals(documents.size, rankedDocs.size)

        // All documents should have a similarity score
        rankedDocs.forEach { rankedDoc ->
            assertNotNull(rankedDoc.document)
            assertTrue(rankedDoc.similarity >= 0.0)
        }

        // Documents containing "hello" should be more similar (lower distance)
        val helloDocuments = rankedDocs.filter { it.document.content.contains("hello") }
        val nonHelloDocuments = rankedDocs.filter { !it.document.content.contains("hello") }

        if (helloDocuments.isNotEmpty() && nonHelloDocuments.isNotEmpty()) {
            val avgHelloSimilarity = helloDocuments.map { it.similarity }.average()
            val avgNonHelloSimilarity = nonHelloDocuments.map { it.similarity }.average()
            assertTrue(avgHelloSimilarity > avgNonHelloSimilarity, "Documents with 'hello' should be more similar")
        }
    }

    @Test
    fun testRankDocumentsEmptyStorage() = runTest {
        val storage = createTestStorage()

        // Rank documents in empty storage
        val rankedDocs = storage.rankDocuments("test query").toList()
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
        documents.forEach { doc ->
            val id = storage.store(doc)
            documentIds.add(id)
        }

        // Verify all documents can be retrieved
        documentIds.zip(documents).forEach { (id, expectedDoc) ->
            val retrievedDoc = storage.read(id)
            assertEquals(expectedDoc, retrievedDoc)
        }
    }

    @Test
    fun testTextEmbeddingFunctionality() = runTest {
        val storage = createTestStorage()
        val documents = listOf(MockDocument("apple"), MockDocument("banana"), MockDocument("cherry"))
        val documentIds = mutableListOf<String>()

        // Store documents
        documents.forEach { doc ->
            val id = storage.store(doc, Unit)
            documentIds.add(id)
        }

        // Test ranking with different queries
        val appleRanked = storage.rankDocuments("apple").toList()
        val bananaRanked = storage.rankDocuments("banana").toList()

        assertEquals(documents.size, appleRanked.size)
        assertEquals(documents.size, bananaRanked.size)

        // The exact document should have the best similarity (lowest distance)
        val bestAppleMatch = appleRanked.maxBy { it.similarity }
        val bestBananaMatch = bananaRanked.maxBy { it.similarity }

        assertEquals("apple", bestAppleMatch.document.content)
        assertEquals("banana", bestBananaMatch.document.content)
    }
}
