package ai.koog.rag.vector

import ai.koog.embeddings.base.Vector
import ai.koog.rag.vector.mocks.MockDocument
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class InMemoryVectorStorageTest {

    @Test
    fun testStoreAndRead() = runTest {
        // Arrange
        val storage = InMemoryVectorStorage<MockDocument>()
        val document = MockDocument("Test document")
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        // Act
        val id = storage.store(document, vector)
        val retrievedDocument = storage.read(id)

        // Assert
        assertNotNull(id)
        assertEquals(document, retrievedDocument)
    }

    @Test
    fun testGetPayload() = runTest {
        // Arrange
        val storage = InMemoryVectorStorage<MockDocument>()
        val document = MockDocument("Test document")
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        // Act
        val id = storage.store(document, vector)
        val retrievedVector = storage.getPayload(id)

        // Assert
        assertNotNull(retrievedVector)
        assertEquals(vector, retrievedVector)
    }

    @Test
    fun testReadWithPayload() = runTest {
        // Arrange
        val storage = InMemoryVectorStorage<MockDocument>()
        val document = MockDocument("Test document")
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        // Act
        val id = storage.store(document, vector)
        val docWithPayload = storage.readWithPayload(id)

        // Assert
        assertNotNull(docWithPayload)
        assertEquals(document, docWithPayload.document)
        assertEquals(vector, docWithPayload.payload)
    }

    @Test
    fun testDelete() = runTest {
        // Arrange
        val storage = InMemoryVectorStorage<MockDocument>()
        val document = MockDocument("Test document")
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        // Act
        val id = storage.store(document, vector)
        val deleteResult = storage.delete(id)
        val retrievedDocument = storage.read(id)

        // Assert
        assertTrue(deleteResult)
        assertNull(retrievedDocument)
    }

    @Test
    fun testDeleteNonExistent() = runTest {
        // Arrange
        val storage = InMemoryVectorStorage<MockDocument>()

        // Act
        val deleteResult = storage.delete("non-existent-id")

        // Assert
        assertFalse(deleteResult)
    }

    @Test
    fun testAllDocuments() = runTest {
        // Arrange
        val storage = InMemoryVectorStorage<MockDocument>()
        val documents = listOf(MockDocument("Document 1"), MockDocument("Document 2"), MockDocument("Document 3"))
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        // Act
        documents.forEach { storage.store(it, vector) }
        val allDocs = storage.allDocuments().toList()

        // Assert
        assertEquals(documents.size, allDocs.size)
        documents.forEach { document ->
            assertTrue(allDocs.contains(document))
        }
    }

    @Test
    fun testAllDocumentsWithPayload() = runTest {
        // Arrange
        val storage = InMemoryVectorStorage<MockDocument>()
        val documents = listOf(MockDocument("Document 1"), MockDocument("Document 2"), MockDocument("Document 3"))
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        // Act
        documents.forEach { storage.store(it, vector) }
        val allDocsWithPayload = storage.allDocumentsWithPayload().toList()

        // Assert
        assertEquals(documents.size, allDocsWithPayload.size)
        documents.forEach { document ->
            assertTrue(allDocsWithPayload.any { it.document == document && it.payload == vector })
        }
    }
}
