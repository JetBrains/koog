package ai.koog.agents.core.tools.ratelimit

/**
 * Scope for rate limiting.
 */
public enum class RateLimitScope {
    /**
     * Rate limit applies globally across all users/roles.
     */
    GLOBAL,

    /**
     * Rate limit applies per user/role combination.
     */
    PER_USER
}
