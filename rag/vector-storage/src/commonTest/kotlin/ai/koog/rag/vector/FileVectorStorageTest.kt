package ai.koog.rag.vector

import ai.koog.embeddings.base.Vector
import ai.koog.rag.vector.mocks.MockDocument
import ai.koog.rag.vector.mocks.MockDocumentProvider
import ai.koog.rag.vector.mocks.MockFileSystem
import ai.koog.rag.vector.mocks.MockFileSystemProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileVectorStorageTest {
    private fun createTestStorage(): FileVectorStorage<MockDocument, String> {
        val mockFileSystem = MockFileSystem()
        val mockDocumentProvider = MockDocumentProvider(mockFileSystem)
        val mockFileSystemProvider = MockFileSystemProvider(mockFileSystem)

        val storage = FileVectorStorage(mockDocumentProvider, mockFileSystemProvider, "test-root")
        return storage
    }

    @Test
    fun testStoreAndRead() = runTest {
        val storage = createTestStorage()
        val document = MockDocument("Text of the test document")
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        // Store document
        val documentId = storage.store(document, vector)
        assertNotNull(documentId)

        // Read document back
        val retrievedDocument = storage.read(documentId)
        assertEquals(document, retrievedDocument)
    }

    @Test
    fun testStoreAndGetPayload() = runTest {
        val storage = createTestStorage()
        val document = MockDocument("Text of the test document")
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        // Store document
        val documentId = storage.store(document, vector)

        // Get payload back
        val retrievedVector = storage.getPayload(documentId)
        assertEquals(vector, retrievedVector)
    }

    @Test
    fun testReadWithPayload() = runTest {
        val storage = createTestStorage()
        val document = MockDocument("Text of the test document")
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        // Store document
        val documentId = storage.store(document, vector)

        // Read with payload
        val result = storage.readWithPayload(documentId)
        assertNotNull(result)
        assertEquals(document, result.document)
        assertEquals(vector, result.payload)
    }

    @Test
    fun testDelete() = runTest {
        val storage = createTestStorage()
        val document = MockDocument("Text of the test document")
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        // Store document
        val documentId = storage.store(document, vector)

        // Verify it exists
        assertNotNull(storage.read(documentId))

        // Delete it
        val deleted = storage.delete(documentId)
        assertTrue(deleted)

        // Verify it's gone
        assertNull(storage.read(documentId))
        assertNull(storage.getPayload(documentId))
    }

    @Test
    fun testAllDocuments() = runTest {
        val storage = createTestStorage()
        val documents = listOf(
            MockDocument("Text of the test document 1"),
            MockDocument("Text of the test document 2"),
            MockDocument("Text of the test document 3")
        )
        val vectors = listOf(
            Vector(listOf(1.0, 2.0)),
            Vector(listOf(3.0, 4.0)),
            Vector(listOf(5.0, 6.0))
        )

        // Store multiple documents and keep track of document IDs
        val documentIds = mutableListOf<String>()
        documents.zip(vectors).forEach { (doc, vec) ->
            val documentId = storage.store(doc, vec)
            documentIds.add(documentId)
        }

        // Retrieve all documents
        val allDocs = storage.allDocuments().toList()
        assertEquals(documents.size, allDocs.size)
        assertTrue(allDocs.containsAll(documents))

        assertTrue(storage.read(documentIds[0]) in allDocs, "first document should be present in storage")
        assertTrue(storage.read(documentIds[1]) in allDocs, "second document should be present in storage")
        assertTrue(storage.read(documentIds[2]) in allDocs, "third document should be present in storage")
    }

    @Test
    fun testAllDocumentsWithPayload() = runTest {
        val storage = createTestStorage()
        val documents = listOf(
            MockDocument("Text of the test document 1"),
            MockDocument("Text of the test document 2"),
        )
        val vectors = listOf(
            Vector(listOf(1.0, 2.0)),
            Vector(listOf(3.0, 4.0))
        )

        // Store documents and keep track of document IDs
        val documentIds = mutableListOf<String>()
        documents.zip(vectors).forEach { (doc, vec) ->
            val documentId = storage.store(doc, vec)
            documentIds.add(documentId)
        }

        // Retrieve all documents with payload
        val allDocsWithPayload = storage.allDocumentsWithPayload().toList()
        assertEquals(2, allDocsWithPayload.size)

        val retrievedDocs = allDocsWithPayload.map { it.document }
        val retrievedVectors = allDocsWithPayload.map { it.payload }

        assertTrue(retrievedDocs.containsAll(documents))
        assertTrue(retrievedVectors.containsAll(vectors))
    }

    @Test
    fun testReadNonExistentDocument() = runTest {
        val storage = createTestStorage()

        val result = storage.read("non-existent-id")
        assertNull(result)
    }

    @Test
    fun testGetPayloadNonExistentDocument() = runTest {
        val storage = createTestStorage()

        val result = storage.getPayload("non-existent-id")
        assertNull(result)
    }

    @Test
    fun testDeleteNonExistentDocument() = runTest {
        val storage = createTestStorage()

        val result = storage.delete("non-existent-id")
        assertEquals(false, result)
    }
}
