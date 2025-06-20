package ai.koog.rag.vector

import ai.koog.embeddings.base.Vector
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
            return 1.0 - 2.0 * intersectionsSize / totalSize;
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
            val documentId = storage.store(testFile)
            assertNotNull(documentId)

            // Read document back
            val retrievedDocument = storage.read(documentId)
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
            val documentId = storage.store(testFile)

            // Verify it exists
            assertNotNull(storage.read(documentId))

            // Delete it
            val deleted = storage.delete(documentId)
            assertTrue(deleted)

            // Verify it's gone
            assertEquals(null, storage.read(documentId))
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
            testFiles.forEach { file ->
                storage.store(file)
            }

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
            testFiles.forEach { file ->
                storage.store(file)
            }

            // Rank documents by similarity to "hello"
            val rankedDocs = storage.rankDocuments("hello").toList()
            assertEquals(testFiles.size, rankedDocs.size)

            // All documents should have a similarity score
            rankedDocs.forEach { rankedDoc ->
                assertNotNull(rankedDoc.document)
                assertTrue(rankedDoc.similarity >= 0.0)
            }

            // Documents containing "hello" should be more similar (lower distance)
            val helloDocuments = rankedDocs.filter { 
                Files.readString(it.document).contains("hello") 
            }
            val nonHelloDocuments = rankedDocs.filter { 
                !Files.readString(it.document).contains("hello") 
            }

            if (helloDocuments.isNotEmpty() && nonHelloDocuments.isNotEmpty()) {
                val avgHelloSimilarity = helloDocuments.map { it.similarity }.average()
                val avgNonHelloSimilarity = nonHelloDocuments.map { it.similarity }.average()
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
        val rankedDocs = storage.rankDocuments("test query").toList()
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
            testFiles.forEach { file ->
                val id = storage.store(file)
                documentIds.add(id)
            }

            // Verify all documents can be retrieved
            documentIds.zip(fileContents).forEach { (id, expectedContent) ->
                val retrievedFile = storage.read(id)
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
            testFiles.forEach { file ->
                storage.store(file)
            }

            // Test ranking with different queries
            val appleRanked = storage.rankDocuments("apple").toList()
            val bananaRanked = storage.rankDocuments("banana").toList()

            assertEquals(testFiles.size, appleRanked.size)
            assertEquals(testFiles.size, bananaRanked.size)

            // The exact document should have the best similarity (lowest distance)
            val bestAppleMatch = appleRanked.maxBy { it.similarity }
            val bestBananaMatch = bananaRanked.maxBy { it.similarity }

            assertEquals("apple", Files.readString(bestAppleMatch.document))
            assertEquals("banana", Files.readString(bestBananaMatch.document))
        } finally {
            testFiles.forEach { Files.deleteIfExists(it) }
        }
    }
}