package ai.grazie.code.agents.memory.chunk

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Data class implementation of MemoryChunk for basic memory storage.
 */
@Serializable
data class BasicMemoryChunk(
    override val id: String,
    override val content: String,
    override val tags: List<String> = emptyList(),
    override val timestamp: Instant = Clock.System.now()
) : MemoryChunk {
    override fun asText(): String = content

    override fun search(query: String): String = content

    override fun tags(additionalTags: List<String>): MemoryChunk =
        copy(tags = tags + additionalTags)
}