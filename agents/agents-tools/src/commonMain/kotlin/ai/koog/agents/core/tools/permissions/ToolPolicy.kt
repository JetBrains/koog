package ai.koog.agents.core.tools.permissions

import ai.koog.agents.core.tools.cache.CacheConfig
import ai.koog.agents.core.tools.ratelimit.RoleLimits

/**
 * Role-based access control for tools.
 */
public sealed class RoleRequirement {
    /**
     * No role restrictions - tool is publicly accessible.
     */
    public object None : RoleRequirement()

    /**
     * Requires a minimum role level using hierarchy.
     * Users with this role or any role that inherits from it can access the tool.
     */
    public data class MinimumRole(val role: Role) : RoleRequirement()

    /**
     * Requires one of the specific roles (no hierarchy).
     * Only users with exactly one of these roles can access the tool.
     */
    public data class AllowedRoles(val roles: Set<Role>) : RoleRequirement() {
        init {
            require(roles.isNotEmpty()) { "AllowedRoles must contain at least one role" }
        }
    }
}

/**
 * Defines the execution policy for a tool, including permissions, rate limiting, and caching.
 *
 * @property roleRequirement Role-based access control for the tool
 * @property rateLimits Role-based rate limiting configuration
 * @property cacheConfig Tool result caching configuration
 */
public data class ToolPolicy(
    public val roleRequirement: RoleRequirement = RoleRequirement.None,
    public val rateLimits: RoleLimits? = null,
    public val cacheConfig: CacheConfig? = null
)
