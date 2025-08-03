package ai.koog.agents.core.tools.cache

import ai.koog.agents.core.tools.ToolResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * In-memory implementation of ToolCache with TTL support.
 *
 * Thread-safe and suitable for single-instance deployments.
 * For production distributed systems, use RedisToolCache or similar.
 *
 * @param maxSize Maximum number of entries to store
 * @param clock Clock for timestamp operations
 */
public class InMemoryToolCache(
    private val maxSize: Int = 1000,
    private val clock: Clock = Clock.System
) : ToolCache {

    private data class CacheEntry(
        val value: ToolResult,
        val expiresAt: Instant
    )

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()

    override suspend fun get(key: String): ToolResult? = mutex.withLock {
        val entry = cache[key]

        if (entry == null) {
            return null
        }

        val now = clock.now()
        if (now >= entry.expiresAt) {
            // Expired - remove
            cache.remove(key)
            return null
        }

        // Valid entry
        return entry.value
    }

    override suspend fun put(key: String, value: ToolResult, ttl: Duration): Unit = mutex.withLock {
        // Evict if at capacity (simple FIFO)
        if (cache.size >= maxSize && !cache.containsKey(key)) {
            val keyToEvict = cache.keys.first()
            cache.remove(keyToEvict)
        }

        val expiresAt = clock.now() + ttl
        cache[key] = CacheEntry(value, expiresAt)
    }

    override suspend fun invalidate(pattern: String): Unit = mutex.withLock {
        val regex = pattern.replace("*", ".*").toRegex()
        val keysToRemove = cache.keys.filter { it.matches(regex) }
        keysToRemove.forEach {
            cache.remove(it)
        }
    }

    override suspend fun clear(): Unit = mutex.withLock {
        cache.clear()
    }

    /**
     * Get detailed information about cache entries (for debugging).
     */
    public suspend fun entries(): Map<String, Pair<ToolResult, Instant>> = mutex.withLock {
        cache.mapValues { (_, entry) -> entry.value to entry.expiresAt }
    }
}
