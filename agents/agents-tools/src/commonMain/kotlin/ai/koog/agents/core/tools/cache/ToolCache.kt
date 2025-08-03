package ai.koog.agents.core.tools.cache

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.permissions.Role
import kotlin.time.Duration

/**
 * Interface for caching tool execution results.
 *
 * Implementations can be in-memory, distributed (Redis), or custom.
 * Designed to prevent redundant tool executions and provide
 * stampede protection for expensive operations.
 *
 * Monitoring and metrics should be handled through the event system
 * (onToolCacheHit, onToolCacheMiss, onToolResultCached) rather than
 * built-in statistics.
 */
public interface ToolCache {
    /**
     * Get a cached tool result if available and not expired.
     *
     * @param key The cache key
     * @return The cached result or null if not found/expired
     */
    public suspend fun get(key: String): ToolResult?

    /**
     * Store a tool result in the cache.
     *
     * @param key The cache key
     * @param value The tool result to cache
     * @param ttl Time to live for this cache entry
     */
    public suspend fun put(key: String, value: ToolResult, ttl: Duration)

    /**
     * Invalidate cache entries matching a pattern.
     *
     * @param pattern Pattern to match keys (e.g., "tool_name:*")
     */
    public suspend fun invalidate(pattern: String)

    /**
     * Clear all cache entries.
     */
    public suspend fun clear()
}

/**
 * Configuration for tool result caching.
 */
public data class CacheConfig(
    val enabled: Boolean = true,
    val ttl: Duration = Duration.parse("5m"),
    val keyGenerator: CacheKeyGenerator = DefaultCacheKeyGenerator()
)

/**
 * Interface for generating cache keys from execution context.
 * Implementations can create keys based on any combination of:
 * tool name, arguments, role, user, session, time windows, etc.
 */
public interface CacheKeyGenerator {
    /**
     * Generate a cache key for the given execution context.
     */
    public fun generateKey(context: CacheKeyContext): String
}

/**
 * Context provided for cache key generation.
 *
 * This base context includes the essential information needed for most
 * cache key strategies. For additional context, extend this class or
 * pass custom data through your CacheKeyGenerator implementation.
 */
public data class CacheKeyContext(
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs?,
    val effectiveRole: Role?,
    val agentId: String
)

/**
 * Default cache key generator that uses tool name and optionally args hash.
 * Uses improved hash generation to reduce collisions.
 */
public class DefaultCacheKeyGenerator(
    private val includeArgs: Boolean = true,
    private val includeRole: Boolean = false,
    private val includeAgent: Boolean = true,
    private val argsHasher: (ToolArgs) -> String = { args -> generateArgsHash(args) }
) : CacheKeyGenerator {
    override fun generateKey(context: CacheKeyContext): String {
        val parts = mutableListOf(
            "tool:${context.tool.name}"
        )

        if (includeArgs && context.toolArgs != null) {
            parts.add("args:${argsHasher(context.toolArgs)}")
        }

        if (includeRole && context.effectiveRole != null) {
            parts.add("role:${context.effectiveRole.name}")
        }

        if (includeAgent) {
            parts.add("agent:${context.agentId}")
        }

        return parts.joinToString(":")
    }

    private companion object {
        /**
         * Generates a more robust hash for tool arguments to reduce collisions.
         * Includes class name, truncated string representation, and hash code.
         */
        private fun generateArgsHash(args: ToolArgs): String = buildString {
            append(args::class.simpleName ?: "Unknown")
            append(":")
            append(args.toString().take(100)) // Limit length to prevent overly long keys
            append(":")
            append(args.hashCode())
        }
    }
}
