package ai.koog.agents.core.tools.permissions

import ai.koog.agents.core.tools.cache.CacheConfig
import ai.koog.agents.core.tools.cache.CacheKeyGenerator
import ai.koog.agents.core.tools.cache.DefaultCacheKeyGenerator
import ai.koog.agents.core.tools.ratelimit.DefaultRateLimitKeyStrategy
import ai.koog.agents.core.tools.ratelimit.RateLimit
import ai.koog.agents.core.tools.ratelimit.RateLimitKeyStrategy
import ai.koog.agents.core.tools.ratelimit.RoleLimits
import kotlin.time.Duration

/**
 * DSL builder for configuring tool policies including permissions, rate limiting, and caching.
 *
 * Example:
 * ```
 * tool(myTool) {
 *     // Permission settings - choose one approach:
 *
 *     // Option 1: Hierarchical (admin inherits from user inherits from guest)
 *     minimumRole = userRole
 *
 *     // Option 2: Explicit list (only these specific roles, no inheritance)
 *     allowedRoles = setOf(adminRole, moderatorRole)
 *
 *     // Rate limiting settings
 *     rateLimits {
 *         role(Guest) { limit(10, 1.minutes) }
 *         role(User) { limit(100, 1.minutes) }
 *         role(Admin) { unlimited() }
 *     }
 *
 *     // Caching settings
 *     cache {
 *         ttl = 5.minutes
 *         roleSpecific()
 *     }
 * }
 * ```
 */
@DslMarker
public annotation class PolicyDsl

/**
 * Builder for creating tool policies with a DSL.
 */
@PolicyDsl
public class ToolPolicyBuilder {
    // Permission settings - internal state
    private var roleRequirement: RoleRequirement = RoleRequirement.None

    /**
     * Minimum role required (uses hierarchy).
     * Use this when you want hierarchical role inheritance.
     */
    public var minimumRole: Role?
        get() = (roleRequirement as? RoleRequirement.MinimumRole)?.role
        set(value) {
            roleRequirement = if (value != null) {
                RoleRequirement.MinimumRole(value)
            } else {
                RoleRequirement.None
            }
        }

    /**
     * Specific roles allowed to use this tool (any of).
     * Use this when you want explicit role control without hierarchy.
     */
    public var allowedRoles: Set<Role>
        get() = (roleRequirement as? RoleRequirement.AllowedRoles)?.roles ?: emptySet()
        set(value) {
            roleRequirement = if (value.isNotEmpty()) {
                RoleRequirement.AllowedRoles(value)
            } else {
                RoleRequirement.None
            }
        }

    // Rate limiting settings
    /**
     * Rate limits for different roles.
     */
    private var rateLimits: RoleLimits? = null

    // Caching settings
    /**
     * Cache configuration for tool results.
     */
    private var cacheConfig: CacheConfig? = null

    /**
     * Configure rate limits for different roles.
     */
    public fun rateLimits(block: RoleLimitsBuilder.() -> Unit) {
        val builder = RoleLimitsBuilder()
        builder.block()
        rateLimits = builder.build()
    }

    /**
     * Configure caching for tool results.
     */
    public fun cache(block: CacheConfigBuilder.() -> Unit) {
        val builder = CacheConfigBuilder()
        builder.block()
        cacheConfig = builder.build()
    }

    /**
     * Build the ToolPolicy.
     */
    public fun build(): ToolPolicy = ToolPolicy(
        roleRequirement = roleRequirement,
        rateLimits = rateLimits,
        cacheConfig = cacheConfig
    )
}

/**
 * Builder for configuring rate limits per role.
 */
@PolicyDsl
public class RoleLimitsBuilder {
    private val limits = mutableMapOf<Role, RateLimit?>()

    /**
     * Strategy for generating rate limit keys.
     * Defaults to DefaultRateLimitKeyStrategy with args included.
     */
    public var keyStrategy: RateLimitKeyStrategy = DefaultRateLimitKeyStrategy()

    /**
     * Configure rate limit for a specific role.
     */
    public fun role(role: Role, block: RoleLimitBuilder.() -> Unit) {
        val builder = RoleLimitBuilder()
        builder.block()
        limits[role] = builder.build()
    }

    /**
     * Use a custom key strategy.
     */
    public fun keyStrategy(strategy: RateLimitKeyStrategy) {
        keyStrategy = strategy
    }

    /**
     * Configure key strategy to exclude arguments from rate limit keys.
     * Useful when you want to rate limit by tool usage only, not specific arguments.
     */
    public fun excludeArgs() {
        keyStrategy = DefaultRateLimitKeyStrategy(includeArgs = false)
    }

    /**
     * Configure key strategy to include agent ID in rate limit keys.
     * Useful for per-agent rate limiting.
     */
    public fun includeAgent() {
        keyStrategy = DefaultRateLimitKeyStrategy(includeArgs = true, includeAgent = true)
    }

    internal fun build(): RoleLimits = RoleLimits(limits.toMap(), keyStrategy)
}

/**
 * Builder for configuring rate limit for a single role.
 */
@PolicyDsl
public class RoleLimitBuilder {
    private var value: RateLimit? = null

    /**
     * Set a rate limit.
     */
    public fun limit(requests: Int, window: Duration) {
        value = RateLimit(requests, window)
    }

    /**
     * Set unlimited access (no rate limit).
     */
    public fun unlimited() {
        value = null
    }

    internal fun build(): RateLimit? = value
}

/**
 * Builder for configuring tool result caching.
 */
@PolicyDsl
public class CacheConfigBuilder {
    /**
     * Whether caching is enabled.
     */
    public var enabled: Boolean = true

    /**
     * Time to live for cached results.
     */
    public var ttl: Duration = Duration.parse("5m")

    /**
     * Key generator for creating cache keys.
     * Defaults to DefaultCacheKeyGenerator with no role-specific caching.
     */
    public var keyGenerator: CacheKeyGenerator = DefaultCacheKeyGenerator()

    /**
     * Use role-specific caching (convenience method).
     */
    public fun roleSpecific() {
        keyGenerator = DefaultCacheKeyGenerator(includeRole = true)
    }

    /**
     * Configure cache keys to exclude tool arguments.
     * Useful when you want to cache results regardless of specific arguments.
     */
    public fun excludeArgs() {
        keyGenerator = DefaultCacheKeyGenerator(includeArgs = false)
    }

    /**
     * Configure cache keys to exclude agent ID.
     * Useful for shared caching across all agents.
     */
    public fun excludeAgent() {
        keyGenerator = DefaultCacheKeyGenerator(includeArgs = true, includeAgent = false)
    }

    /**
     * Use a custom key generator.
     */
    public fun keyGenerator(generator: CacheKeyGenerator) {
        keyGenerator = generator
    }

    internal fun build(): CacheConfig = CacheConfig(
        enabled = enabled,
        ttl = ttl,
        keyGenerator = keyGenerator
    )
}
