package ai.koog.rag.vector

import ai.koog.embeddings.base.Vector
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class JVMFileVectorStorageTest {

    private fun createTestStorage(): JVMFileVectorStorage {
        val tempDir = Files.createTempDirectory("jvm-vector-storage-test")
        return JVMFileVectorStorage(tempDir)
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
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        try {
            // Store document
            val documentId = storage.store(testFile, vector)
            assertNotNull(documentId)

            // Read document back
            val retrievedDocument = storage.read(documentId)
            assertNotNull(retrievedDocument)
            // Don't compare paths directly, as the storage returns the internal storage path
            // Instead, verify the content is the same
            assertEquals(
                Files.readString(testFile),
                Files.readString(retrievedDocument)
            )
        } finally {
            Files.deleteIfExists(testFile)
        }
    }

    @Test
    fun testStoreAndGetPayload() = runTest {
        val storage = createTestStorage()
        val testFile = createTestFile("test document content")
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        try {
            // Store document
            val documentId = storage.store(testFile, vector)

            // Get payload back
            val retrievedVector = storage.getPayload(documentId)
            assertEquals(vector, retrievedVector)
        } finally {
            Files.deleteIfExists(testFile)
        }
    }

    @Test
    fun testReadWithPayload() = runTest {
        val storage = createTestStorage()
        val testFile = createTestFile("test document content")
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        try {
            // Store document
            val documentId = storage.store(testFile, vector)

            // Read with payload
            val result = storage.readWithPayload(documentId)
            assertNotNull(result)
            // Compare file contents instead of paths
            assertEquals(
                Files.readString(testFile),
                Files.readString(result.document)
            )
            assertEquals(vector, result.payload)
        } finally {
            Files.deleteIfExists(testFile)
        }
    }

    @Test
    fun testDelete() = runTest {
        val storage = createTestStorage()
        val testFile = createTestFile("test document content")
        val vector = Vector(listOf(1.0, 2.0, 3.0))

        try {
            // Store document
            val documentId = storage.store(testFile, vector)

            // Verify it exists
            assertNotNull(storage.read(documentId))

            // Delete it
            val deleted = storage.delete(documentId)
            assertTrue(deleted)

            // Verify it's gone
            assertNull(storage.read(documentId))
            assertNull(storage.getPayload(documentId))
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
        val vectors = listOf(
            Vector(listOf(1.0, 2.0)),
            Vector(listOf(3.0, 4.0)),
            Vector(listOf(5.0, 6.0))
        )

        try {
            // Store multiple documents
            testFiles.zip(vectors).forEach { (file, vec) ->
                storage.store(file, vec)
            }

            // Retrieve all documents
            val allDocs = storage.allDocuments().toList()
            assertEquals(testFiles.size, allDocs.size)

            // Compare contents instead of paths
            val testContents = testFiles.map { Files.readString(it) }.toSet()
            val retrievedContents = allDocs.map { Files.readString(it) }.toSet()
            assertEquals(testContents, retrievedContents)
        } finally {
            testFiles.forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun testAllDocumentsWithPayload() = runTest {
        val storage = createTestStorage()
        val testFiles = listOf(
            createTestFile("document 1"),
            createTestFile("document 2")
        )
        val vectors = listOf(
            Vector(listOf(1.0, 2.0)),
            Vector(listOf(3.0, 4.0))
        )

        try {
            // Store documents
            testFiles.zip(vectors).forEach { (file, vec) ->
                storage.store(file, vec)
            }

            // Retrieve all documents with payload
            val allDocsWithPayload = storage.allDocumentsWithPayload().toList()
            assertEquals(2, allDocsWithPayload.size)

            val retrievedDocs = allDocsWithPayload.map { it.document }
            val retrievedVectors = allDocsWithPayload.map { it.payload }

            // Compare contents instead of paths
            val testContents = testFiles.map { Files.readString(it) }.toSet()
            val retrievedContents = retrievedDocs.map { Files.readString(it) }.toSet()
            assertEquals(testContents, retrievedContents)

            // Compare vectors
            val vectorSet = vectors.toSet()
            val retrievedVectorSet = retrievedVectors.toSet()
            assertEquals(vectorSet, retrievedVectorSet)
        } finally {
            testFiles.forEach { Files.deleteIfExists(it) }
        }
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
