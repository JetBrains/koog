package ai.koog.agents.local.memory.model

/**
 * Provides platform-independent access to time-related functionality for the memory system.
 * This interface enables consistent timestamp generation across different platforms while
 * allowing platform-specific optimizations.
 *
 * Use cases:
 * - Generating timestamps for memory chunks and facts
 * - Implementing time-based memory retrieval and cleanup
 * - Supporting temporal reasoning in the memory system
 * - Enabling platform-specific time handling optimizations
 *
 * The interface is designed to be simple yet flexible enough to accommodate
 * different platform requirements and time precision needs.
 */
public interface TimeProvider {
    /**
     * Retrieves the current timestamp in milliseconds.
     * This method is used throughout the memory system to:
     * - Mark when facts were stored
     * - Track memory chunk creation time
     * - Support temporal queries and memory management
     *
     * @return Current time in milliseconds since the Unix epoch
     */
    public fun getCurrentTimestamp(): Long
}

/**
 * Platform-specific implementation of TimeProvider.
 * This expect declaration is completed by actual implementations in platform-specific
 * source sets (JVM, Native, JS) to provide optimal time handling for each platform.
 *
 * Example JVM implementation might use System.currentTimeMillis(),
 * while JS implementation might use Date.now().
 */
public expect object DefaultTimeProvider : TimeProvider {
    override fun getCurrentTimestamp(): Long
}
