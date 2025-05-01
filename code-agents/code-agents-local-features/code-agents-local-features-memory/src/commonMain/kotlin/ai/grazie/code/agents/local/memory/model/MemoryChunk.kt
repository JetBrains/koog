package ai.grazie.code.agents.local.memory.model

import kotlinx.serialization.Serializable

/**
 * Represents an atomic unit of information in the agent's memory system.
 * Memory chunks are designed to store discrete pieces of information that can be
 * efficiently retrieved and processed by the agent. They serve as the foundation
 * for building more complex memory structures and enable effective information retrieval.
 *
 * Use cases:
 * - Storing conversation segments for context retention
 * - Caching processed information for quick access
 * - Organizing related pieces of information with tags
 * - Building searchable knowledge bases
 *
 * @property id Unique identifier for the memory chunk
 * @property content The actual information stored in the chunk
 * @property tags List of labels used for categorization and efficient retrieval
 */
interface MemoryChunk {
    val id: String
    val content: String
    val tags: List<String>
}

/**
 * Standard implementation of a memory chunk that provides basic storage capabilities.
 * This implementation is serializable and can be easily persisted to storage.
 *
 * Example usage:
 * ```
 * val chunk = BasicMemoryChunk(
 *     id = "env-info-2024",
 *     content = "Operating System: macOS 13.0, Java Version: 17.0.1",
 *     tags = listOf("environment", "system-info", "runtime")
 * )
 * ```
 *
 * @property id Unique identifier for the chunk, often incorporating timestamp or context
 * @property content The information stored in this chunk, typically in a format suitable for LLM processing
 * @property tags Labels that help in organizing and retrieving related chunks efficiently
 */
@Serializable
data class BasicMemoryChunk(
    override val id: String,
    override val content: String,
    override val tags: List<String>
) : MemoryChunk
