package ai.koog.prompt.cache.memory

import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.message.Message
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * In-memory implementation of [PromptCache].
 * This implementation stores cache entries in memory.
 */
public class InMemoryPromptCache(private val maxEntries: Int?) : PromptCache {
    /**
     * A companion object implementation of the `PromptCache.Factory.Named` specialized for an in-memory prompt cache.
     * This factory is responsible for creating instances of `InMemoryPromptCache` based on a specific configuration string.
     *
     * The factory expects the configuration string to begin with "memory", followed by an optional size limit. The size
     * limit may either be omitted, set to "unlimited", or represented as a numeric value indicating the maximum
     * number of entries in the cache.
     */
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

    override suspend fun get(request: PromptCache.Request): List<Message.Response>? {
        val entry = cache[request.asCacheKey] ?: return null

        // Update last accessed time
        entry.accessed = Clock.System.now()

        return entry.response
    }

    override suspend fun put(request: PromptCache.Request, response: List<Message.Response>) {
        val key = request.asCacheKey

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
