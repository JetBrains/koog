package ai.koog.agents.core.tools.permissions

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.cache.CacheKeyContext
import ai.koog.agents.core.tools.cache.DefaultCacheKeyGenerator

/**
 * Utility functions for permission and caching operations.
 */

/**
 * Generate a cache key for tool execution using the default strategy.
 *
 * This is a convenience function for simple use cases. For more complex
 * key generation strategies, implement CacheKeyGenerator directly.
 *
 * @param tool The tool being executed
 * @param toolArgs The arguments for the tool
 * @param effectiveRole The role executing the tool
 * @param agentId The agent ID
 * @param includeRole Whether to include role in the cache key
 * @param includeAgent Whether to include agent ID in the cache key
 * @return The generated cache key
 */
public fun generateCacheKey(
    tool: Tool<*, *>,
    toolArgs: ToolArgs?,
    effectiveRole: Role?,
    agentId: String,
    includeRole: Boolean = false,
    includeAgent: Boolean = true
): String {
    val generator = DefaultCacheKeyGenerator(
        includeRole = includeRole,
        includeAgent = includeAgent
    )

    val context = CacheKeyContext(
        tool = tool,
        toolArgs = toolArgs,
        effectiveRole = effectiveRole,
        agentId = agentId
    )

    return generator.generateKey(context)
}

/**
 * Generate a rate limit key for tool execution.
 *
 * @param toolName The name of the tool
 * @param effectiveRole The role executing the tool
 * @param toolArgs Optional tool arguments to include in the key
 * @return The generated rate limit key
 */
public fun generateRateLimitKey(
    toolName: String,
    effectiveRole: Role,
    toolArgs: ToolArgs? = null
): String {
    val parts = mutableListOf(
        "tool:$toolName",
        "role:${effectiveRole.name}"
    )

    // Include args hash if provided
    toolArgs?.let { args ->
        parts.add("args:${args.hashCode()}")
    }

    return parts.joinToString(":")
}
