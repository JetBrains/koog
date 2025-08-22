package ai.koog.rag.vector

import ai.koog.embeddings.base.Vector
import ai.koog.rag.vector.mocks.MockDocument
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val id = storage.store(document)
        val retrievedDocument = storage.read(id)

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
        val id = storage.store(document)
        val deleteResult = storage.delete(id)
        val retrievedDocument = storage.read(id)

        // Assert
        assertTrue(deleteResult)
        assertNull(retrievedDocument)
    }

    @Test
    fun testAllDocuments() = runTest {
        // Arrange
        val embedder = MockDocumentEmbedder()
        val storage = InMemoryDocumentEmbeddingStorage(embedder)
        val documents = listOf(MockDocument("Document 1"), MockDocument("Document 2"), MockDocument("Document 3"))

        // Act
        documents.forEach { storage.store(it) }
        val allDocs = storage.allDocuments().toList()

        // Assert
        assertEquals(documents.size, allDocs.size)
        documents.forEach { document ->
            assertTrue(allDocs.contains(document))
        }
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
        documents.forEach { storage.store(it) }

        // Act
        val query = "banana"
        val rankedDocs = storage.rankDocuments(query).toList()

        // Assert
        assertEquals(documents.size, rankedDocs.size)

        // The document containing "banana" should have higher similarity (lower diff)
        val bananaDocRanks = rankedDocs.filter { it.document.content.contains("banana") }
            .map { it.similarity }

        val otherDocRanks = rankedDocs.filter { !it.document.content.contains("banana") }
            .map { it.similarity }

        // In our mock implementation, documents containing the query should have similarity 1.0 (exact match)
        // and others should have similarity 0.0 (different)
        bananaDocRanks.forEach { similarity ->
            assertEquals(1.0, similarity)
        }

        otherDocRanks.forEach { similarity ->
            assertEquals(0.0, similarity)
        }
    }
}
