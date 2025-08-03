package ai.koog.agents.core.tools.ratelimit

import ai.koog.agents.core.tools.permissions.Role
import kotlin.collections.iterator
import kotlin.time.Duration

/**
 * Simple rate limit configuration.
 */
public data class RateLimit(
    val limit: Int,
    val window: Duration
) {
    init {
        require(limit >= 0) { "Limit must be positive or zero" }
        require(!window.isNegative() && window.isFinite()) { "Window must be positive and finite" }
    }
}

/**
 * Role-based rate limit configuration.
 * Maps roles to their rate limits.
 *
 * @property limits Map of roles to their rate limits
 * @property keyStrategy Strategy for generating rate limit keys
 */
public data class RoleLimits(
    private val limits: Map<Role, RateLimit?>,
    public val keyStrategy: RateLimitKeyStrategy = DefaultRateLimitKeyStrategy()
) {
    /**
     * Get the rate limit for a specific role.
     * Returns the most specific limit defined for the role or its ancestors.
     * Returns null if no limit is defined (unlimited).
     */
    public fun getRateLimitForRole(role: Role): RateLimit? {
        // First check direct role mapping
        if (role in limits) {
            return limits[role]
        }

        // Then check inherited roles
        for ((limitRole, limit) in limits) {
            if (limit != null && role.hasRole(limitRole)) {
                return limit
            }
        }

        // No limit found
        return null
    }
}
