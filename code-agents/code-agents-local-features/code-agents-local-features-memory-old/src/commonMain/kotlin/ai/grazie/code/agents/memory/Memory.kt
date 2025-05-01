package ai.grazie.code.agents.memory

import ai.grazie.code.agents.memory.chunk.MemoryChunk

/**
 * Interface to retrieve data that has been stored in memory.
 * Provides methods for semantic search, tag-based retrieval, and more.
 */
interface Memory {
    /**
     * Retrieves memory chunks based on a semantic search query.
     *
     * @param query The semantic search query
     * @param limit Maximum number of chunks to retrieve (default: 5)
     * @return List of memory chunks matching the query
     */
    suspend fun search(query: String, limit: Int = 5): List<MemoryChunk>

    /**
     * Retrieves memory chunks based on tags.
     *
     * @param tags List of tags to search for
     * @param matchAll If true, only return chunks that have all the specified tags (default: false)
     * @param limit Maximum number of chunks to retrieve (default: 5)
     * @return List of memory chunks matching the tags
     */
    suspend fun tags(tags: List<String>, matchAll: Boolean = false, limit: Int = 5): List<MemoryChunk>

    suspend fun store(content: String): List<MemoryChunk>

    /**
     * Stores a memory chunk.
     *
     * @param chunk The memory chunk to store
     * @return The stored memory chunk with any additional metadata
     */
    suspend fun store(chunk: MemoryChunk): MemoryChunk

    /**
     * Clears all memory chunks.
     */
    suspend fun clear()
}