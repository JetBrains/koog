package ai.koog.rag.vector.storage

import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import ai.koog.rag.vector.embedder.DocumentEmbedder
import ai.koog.rag.vector.mocks.MockDocument
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryDocumentEmbeddingStorageTest {

    // Simple mock implementation of DocumentEmbedder for testing
    private class MockDocumentEmbedder : DocumentEmbedder<MockDocument> {
        override suspend fun embed(document: MockDocument): Vector {
            // Simple embedding: convert each character to its ASCII value and use as vector values
            return embed(document.content)
        }

        override suspend fun embed(text: String): Vector {
            return Vector(text.map { it.code.toDouble() })
        }

        override fun diff(embedding1: Vector, embedding2: Vector): Double {
            // For the test case, we need to check if the query is contained in the document
            // If the query vector is a subset of the document vector, return 0.0 (high similarity)
            // Otherwise, return 1.0 (low similarity)
            val queryString = embedding1.values.map { it.toInt().toChar() }.joinToString("")
            val docString = embedding2.values.map { it.toInt().toChar() }.joinToString("")

            return if (docString.contains(queryString)) 0.0 else 1.0
        }
    }

    @Test
    fun testStoreAndRead() = runTest {
        // Arrange
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)
        val document = MockDocument("Test document")

        // Act
        val id = storage.add(listOf(document)).first()
        val retrievedDocument = storage.get(listOf(id)).firstOrNull()

        // Assert
        assertNotNull(id)
        assertEquals(document, retrievedDocument)
    }

    @Test
    fun testDelete() = runTest {
        // Arrange
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)
        val document = MockDocument("Test document")

        // Act
        val id = storage.add(listOf(document)).first()
        val deletedIds = storage.delete(listOf(id))
        val retrievedDocument = storage.get(listOf(id)).firstOrNull()

        // Assert
        assertEquals(listOf(id), deletedIds)
        assertNull(retrievedDocument)
    }

    @Test
    fun testAllDocuments() = runTest {
        // Arrange
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)
        val documents = listOf(MockDocument("Document 1"), MockDocument("Document 2"), MockDocument("Document 3"))

        // Act
        storage.add(documents)
        val allDocs = storage.allDocuments().toList()

        // Assert
        assertEquals(documents.size, allDocs.size)
        documents.forEach { document ->
            assertTrue(allDocs.contains(document))
        }
    }

    @Test
    fun testUpdatePreservesIds() = runTest {
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)
        val document = MockDocument("original content")

        val id = storage.add(listOf(document)).first()
        val updatedDocument = MockDocument("updated content")

        val updatedIds = storage.update(mapOf(id to updatedDocument))

        assertEquals(listOf(id), updatedIds)
        val retrieved = storage.get(listOf(id)).firstOrNull()
        assertEquals(updatedDocument, retrieved)
    }

    @Test
    fun testUpdateMultipleDocumentsPreservesIds() = runTest {
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)
        val doc1 = MockDocument("first")
        val doc2 = MockDocument("second")

        val id1 = storage.add(listOf(doc1)).first()
        val id2 = storage.add(listOf(doc2)).first()

        val updatedDoc1 = MockDocument("first updated")
        val updatedDoc2 = MockDocument("second updated")

        val updatedIds = storage.update(mapOf(id1 to updatedDoc1, id2 to updatedDoc2))

        assertEquals(setOf(id1, id2), updatedIds.toSet())
        assertEquals(updatedDoc1, storage.get(listOf(id1)).firstOrNull())
        assertEquals(updatedDoc2, storage.get(listOf(id2)).firstOrNull())
    }

    @Test
    fun testUpdateDoesNotChangeDocumentCount() = runTest {
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)
        val documents = listOf(MockDocument("doc1"), MockDocument("doc2"), MockDocument("doc3"))

        val ids = storage.add(documents)
        val allDocsBefore = storage.allDocuments().toList()
        assertEquals(3, allDocsBefore.size)

        storage.update(mapOf(ids[0] to MockDocument("doc1 updated")))

        val allDocsAfter = storage.allDocuments().toList()
        assertEquals(3, allDocsAfter.size)
    }

    @Test
    fun testRankDocuments() = runTest {
        // Arrange
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)
        val documents = listOf(
            MockDocument("apple banana"),
            MockDocument("banana cherry"),
            MockDocument("cherry date")
        )

        // Store all documents
        storage.add(documents)

        // Act
        val query = "banana"
        val rankedDocs = storage.search(SimilaritySearchRequest(queryText = query, limit = Int.MAX_VALUE))

        // Assert
        assertEquals(documents.size, rankedDocs.size)

        // The document containing "banana" should have higher similarity (lower diff)
        val bananaDocRanks = rankedDocs.filter { it.document.content.contains("banana") }
            .map { it.score.value }

        val otherDocRanks = rankedDocs.filter { !it.document.content.contains("banana") }
            .map { it.score.value }

        // In our mock implementation, documents containing the query should have similarity 1.0 (exact match)
        // and others should have similarity 0.0 (different)
        bananaDocRanks.forEach { score ->
            assertEquals(1.0, score)
        }

        otherDocRanks.forEach { score ->
            assertEquals(0.0, score)
        }
    }

    @Test
    fun testUpdateMissingIdDoesNotChangeDocumentCount() = runTest {
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)
        val documents = listOf(MockDocument("doc1"), MockDocument("doc2"))

        storage.add(documents)
        val countBefore = storage.allDocuments().toList().size

        val updatedIds = storage.update(mapOf("non-existent-id" to MockDocument("new doc")))

        assertTrue(updatedIds.isEmpty())
        val countAfter = storage.allDocuments().toList().size
        assertEquals(countBefore, countAfter)
    }

    @Test
    fun testNamespaceRejectedInAdd() = runTest {
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)

        assertFailsWith<IllegalArgumentException> {
            storage.add(listOf(MockDocument("doc")), namespace = "tenant-1")
        }
    }

    @Test
    fun testNamespaceRejectedInSearch() = runTest {
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)

        assertFailsWith<IllegalArgumentException> {
            storage.search(SimilaritySearchRequest(queryText = "q", limit = 10), namespace = "tenant-1")
        }
    }

    @Test
    fun testNamespaceRejectedInUpdate() = runTest {
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)

        assertFailsWith<IllegalArgumentException> {
            storage.update(mapOf("id" to MockDocument("doc")), namespace = "tenant-1")
        }
    }

    @Test
    fun testNamespaceRejectedInDelete() = runTest {
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)

        assertFailsWith<IllegalArgumentException> {
            storage.delete(listOf("id"), namespace = "tenant-1")
        }
    }

    @Test
    fun testNamespaceRejectedInGet() = runTest {
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)

        assertFailsWith<IllegalArgumentException> {
            storage.get(listOf("id"), namespace = "tenant-1")
        }
    }

    @Test
    fun testFilterExpressionRejectedInSearch() = runTest {
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)

        assertFailsWith<IllegalArgumentException> {
            storage.search(
                SimilaritySearchRequest(queryText = "q", limit = 10, filterExpression = "category = 'news'")
            )
        }
    }
}
