package ai.koog.agents.core.tools.ratelimit

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.time.Duration

/**
 * Rate limiter interface with minimal abstractions.
 * Users can implement this interface for custom rate limiting strategies.
 */
public interface RateLimiter {
    /**
     * Check if a request is allowed under the rate limit.
     *
     * @param key Unique key for the rate limit (e.g., "tool:user:123")
     * @param limit Maximum number of requests allowed in the window
     * @param window Time window for the limit
     * @return true if request is allowed, false if rate limited
     */
    public suspend fun isAllowed(
        key: String,
        limit: Int,
        window: Duration
    ): Boolean

    /**
     * Reset rate limit for a specific key.
     *
     * @param key The key to reset
     */
    public suspend fun reset(key: String)

    /**
     * Reset all rate limits.
     */
    public suspend fun resetAll()
}

/**
 * An in-memory rate limiter with fixed window strategy.
 * This is a minimal implementation suitable for single-instance applications.
 * 
 * Includes automatic cleanup of expired entries to prevent memory leaks.
 *
 * For distributed systems or more advanced strategies, users should implement
 * their own RateLimiter.
 */
public class InMemoryRateLimiter(
    private val maxEntries: Int = 10000,
    private val cleanupThreshold: Int = 1000
) : RateLimiter {
    private data class WindowData(
        val count: Int,
        val windowStart: Long,
        val windowDuration: Long
    )

    private val limits = mutableMapOf<String, WindowData>()
    private val mutex = Mutex()

    override suspend fun isAllowed(
        key: String,
        limit: Int,
        window: Duration
    ): Boolean = mutex.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        val windowMillis = window.inWholeMilliseconds

        // Cleanup expired entries periodically to prevent memory leaks
        if (limits.size > cleanupThreshold) {
            cleanupExpiredEntries(now)
        }

        val current = limits[key]

        // If no existing data or window has expired, start new window
        if (current == null || now >= current.windowStart + current.windowDuration) {
            // Check if we're at capacity before adding new entries
            if (limits.size >= maxEntries && current == null) {
                // Remove the oldest entry to make space
                val oldestKey = limits.keys.first()
                limits.remove(oldestKey)
            }
            
            limits[key] = WindowData(1, now, windowMillis)
            return@withLock true
        }

        // Within current window
        if (current.count < limit) {
            limits[key] = current.copy(count = current.count + 1)
            return@withLock true
        }

        // Rate limited
        return@withLock false
    }

    /**
     * Removes expired entries from the rate limiter to prevent memory leaks.
     * Should be called periodically or when the cache gets too large.
     */
    private fun cleanupExpiredEntries(now: Long) {
        val iterator = limits.iterator()
        while (iterator.hasNext()) {
            val (_, data) = iterator.next()
            if (now >= data.windowStart + data.windowDuration) {
                iterator.remove()
            }
        }
    }

    /**
     * Force cleanup of expired entries.
     * Useful for testing or manual memory management.
     */
    public suspend fun cleanupExpired() {
        mutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            cleanupExpiredEntries(now)
        }
    }

    /**
     * Get current cache size (for monitoring/debugging).
     */
    public suspend fun size(): Int = mutex.withLock {
        limits.size
    }

    override suspend fun reset(key: String) {
        mutex.withLock {
            limits.remove(key)
        }
    }

    override suspend fun resetAll() {
        mutex.withLock {
            limits.clear()
        }
    }
}
