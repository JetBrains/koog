package ai.grazie.code.agents.memory.chunk

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Interface representing a chunk of memory that can be stored and retrieved.
 * A memory chunk can be output as text or queried for specific data.
 */
@Serializable
sealed interface MemoryChunk {
    /**
     * Unique identifier for the memory chunk.
     */
    val id: String

    /**
     * Content of the memory chunk.
     */
    val content: String

    /**
     * Tags associated with the memory chunk for tag-based retrieval.
     */
    val tags: List<String>

    /**
     * Timestamp when the memory chunk was created.
     */
    val timestamp: Instant

    /**
     * Outputs the memory chunk as text.
     *
     * @return String representation of the memory chunk
     */
    fun asText(): String

    /**
     * Retrieves specific data from the memory chunk based on a query.
     *
     * @param query The query to extract specific data
     * @return The extracted data as a string, or null if no matching data is found
     */
    fun search(query: String): String?

    /**
     * Creates a copy of this memory chunk with additional tags.
     *
     * @param additionalTags Tags to add to the memory chunk
     * @return A new memory chunk with the combined tags
     */
    fun tags(additionalTags: List<String>): MemoryChunk
}

