package ai.koog.agents.core.tools.ratelimit

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.permissions.Role

/**
 * Context for generating rate limit keys.
 * Similar to CacheKeyContext but for rate limiting.
 */
public data class RateLimitKeyContext(
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs?,
    val effectiveRole: Role,
    val agentId: String
)

/**
 * Strategy for generating rate limit keys.
 * This allows customization of how rate limits are keyed.
 */
public interface RateLimitKeyStrategy {
    /**
     * Generate a rate limit key from the given context.
     */
    public fun generateKey(context: RateLimitKeyContext): String
}

/**
 * Default rate limit key strategy that includes tool name, role, and optionally args.
 */
public class DefaultRateLimitKeyStrategy(
    private val includeArgs: Boolean = true,
    private val includeAgent: Boolean = false,
    private val argsHasher: (ToolArgs) -> String = { it.hashCode().toString() }
) : RateLimitKeyStrategy {
    override fun generateKey(context: RateLimitKeyContext): String {
        val parts = mutableListOf(
            "tool:${context.tool.name}",
            "role:${context.effectiveRole.name}"
        )

        if (includeArgs && context.toolArgs != null) {
            parts.add("args:${argsHasher(context.toolArgs)}")
        }

        if (includeAgent) {
            parts.add("agent:${context.agentId}")
        }

        return parts.joinToString(":")
    }
}
