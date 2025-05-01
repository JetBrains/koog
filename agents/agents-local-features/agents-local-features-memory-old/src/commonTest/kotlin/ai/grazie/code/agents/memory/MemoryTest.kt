package ai.grazie.code.agents.memory

import ai.grazie.code.agents.memory.chunk.BasicMemoryChunk
import ai.grazie.code.agents.memory.storage.MemoryStorage
import ai.grazie.code.agents.memory.storage.MemoryViaMapStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoryTest {
    @Test
    fun testSessionStorage() = runTest {
        val storage = MemoryViaMapStorage.new()
        testStorage(storage)
    }

    private suspend fun testStorage(storage: MemoryStorage) {
        // Test store and retrieve
        val chunk = BasicMemoryChunk(
            id = "test-id",
            content = "Test content",
            tags = listOf("test", "memory")
        )
        val storedChunk = storage.store(chunk)
        assertEquals(chunk, storedChunk)

        val retrievedChunk = storage.retrieve("test-id")
        assertNotNull(retrievedChunk)
        assertEquals(chunk, retrievedChunk)

        // Test retrieveAll
        val chunks = storage.all()
        assertTrue(chunks.contains(chunk))

        // Test retrieveByTags
        val taggedChunks = storage.retrieve(listOf("test"))
        assertTrue(taggedChunks.contains(chunk))

        val matchAllChunks = storage.retrieve(listOf("test", "memory"), true)
        assertTrue(matchAllChunks.contains(chunk))

        val nonMatchingChunks = storage.retrieve(listOf("nonexistent"))
        assertTrue(nonMatchingChunks.isEmpty())

        // Test delete
        val deleted = storage.delete("test-id")
        assertTrue(deleted)

        val deletedChunk = storage.retrieve("test-id")
        assertEquals(null, deletedChunk)

        // Test clear
        val newChunk = BasicMemoryChunk(
            id = "test-id-2",
            content = "Test content 2",
            tags = listOf("test", "memory")
        )
        storage.store(newChunk)
        storage.clear()
        val clearedChunks = storage.all()
        assertTrue(clearedChunks.isEmpty())
    }
}