package ai.grazie.code.agents.memory.storage

import ai.grazie.code.agents.memory.chunk.MemoryChunk

/**
 * Interface for memory storage implementations.
 * Provides methods for storing, retrieving, and managing memory chunks.
 */
interface MemoryStorage {
    /**
     * Stores a memory chunk.
     *
     * @param chunk The memory chunk to store
     * @return The stored memory chunk with any additional metadata
     */
    suspend fun store(chunk: MemoryChunk): MemoryChunk

    /**
     * Retrieves a memory chunk by its ID.
     *
     * @param id The ID of the memory chunk to retrieve
     * @return The memory chunk, or null if not found
     */
    suspend fun retrieve(id: String): MemoryChunk?

    /**
     * Retrieves all memory chunks.
     *
     * @return List of all memory chunks
     */
    suspend fun all(): List<MemoryChunk>

    /**
     * Retrieves memory chunks based on tags.
     *
     * @param tags List of tags to search for
     * @param matchAll If true, only return chunks that have all the specified tags
     * @return List of memory chunks matching the tags
     */
    suspend fun retrieve(tags: List<String>, matchAll: Boolean = false): List<MemoryChunk>

    /**
     * Deletes a memory chunk by its ID.
     *
     * @param id The ID of the memory chunk to delete
     * @return True if the chunk was deleted, false otherwise
     */
    suspend fun delete(id: String): Boolean

    /**
     * Clears all memory chunks.
     */
    suspend fun clear()
}

