package ai.koog.prompt.cache.memory

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.absoluteValue

/**
 * In-memory implementation of [PromptCache].
 * This implementation stores cache entries in memory.
 */
public class InMemoryPromptCache(private val maxEntries: Int?) : PromptCache {
    public companion object : PromptCache.Factory.Named("memory") {
        override fun create(config: String): PromptCache {
            val parts = elements(config)
            require(parts[0] == "memory") { "Invalid cache type: ${parts[0]}. Expected 'memory'." }
            val limit = when {
                parts.size == 1 || parts[1].isEmpty() -> null
                parts[1].equals("unlimited", ignoreCase = true) -> null
                else -> parts[1].toIntOrNull() ?: error("Invalid memory cache size limit: ${parts[1]}. Expected a number or 'unlimited'.")
            }
            return InMemoryPromptCache(limit)
        }
    }

    private val cache = mutableMapOf<String, CacheEntry>()

    private data class CacheEntry(
        val response: List<Message.Response>,
        var accessed: Instant = Clock.System.now()
    )

    /**
     * Generate a cache key for a prompt with tools.
     */
    private fun cacheKey(prompt: Prompt, tools: List<ToolDescriptor>): String {
        val toolsString = tools.joinToString { it.name }
        return (prompt.toString() + toolsString).hashCode().absoluteValue.toString(36)
    }

    override suspend fun get(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response>? {
        val key = cacheKey(prompt, tools)
        val entry = cache[key] ?: return null

        // Update last accessed time
        entry.accessed = Clock.System.now()

        return entry.response
    }

    override suspend fun put(prompt: Prompt, tools: List<ToolDescriptor>, response: List<Message.Response>) {
        val key = cacheKey(prompt, tools)

        // Enforce size limit if specified
        if (maxEntries != null && cache.size >= maxEntries && !cache.containsKey(key)) {
            // Remove least recently used entry
            cache.entries
                .minByOrNull { it.value.accessed }
                ?.key
                ?.let { cache.remove(it) }
        }

        cache[key] = CacheEntry(response)
    }
}
