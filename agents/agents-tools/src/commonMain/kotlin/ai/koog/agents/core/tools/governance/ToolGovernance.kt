package ai.koog.agents.core.tools.governance

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.cache.CacheKeyContext
import ai.koog.agents.core.tools.permissions.PermissionCheckResult
import ai.koog.agents.core.tools.permissions.PermissionChecker
import ai.koog.agents.core.tools.permissions.Role
import ai.koog.agents.core.tools.permissions.RoleRequirement
import ai.koog.agents.core.tools.permissions.StandardPermissionChecker
import ai.koog.agents.core.tools.permissions.ToolPolicy
import ai.koog.agents.core.tools.ratelimit.RateLimitKeyContext
import ai.koog.agents.core.tools.ratelimit.RateLimitResult
import ai.koog.agents.core.tools.ratelimit.RateLimiter
import ai.koog.agents.core.tools.cache.ToolCache

/**
 * Governance result for tool execution that encapsulates the outcome of 
 * permission, rate limit, and cache checks.
 */
public sealed class ToolGovernanceResult {
    /**
     * Tool execution is allowed to proceed.
     */
    public object Allowed : ToolGovernanceResult()
    
    /**
     * Tool execution should use cached result.
     * @param cacheKey The cache key used to retrieve the result
     * @param result The cached tool result
     */
    public data class CacheHit(
        val cacheKey: String,
        val result: ToolResult
    ) : ToolGovernanceResult()
    
    /**
     * Tool execution is denied due to insufficient permissions.
     * @param reason Human-readable reason for denial
     * @param requiredRole The role that would be required for access
     */
    public data class PermissionDenied(
        val reason: String,
        val requiredRole: String?
    ) : ToolGovernanceResult()
    
    /**
     * Tool execution is denied due to rate limiting.
     * @param reason Human-readable reason for denial
     * @param limit The rate limit that was exceeded
     * @param resetIn How long until the limit resets
     */
    public data class RateLimited(
        val reason: String,
        val limit: String,
        val resetIn: String?
    ) : ToolGovernanceResult()
}

/**
 * Context for tool governance operations.
 */
public data class ToolGovernanceContext(
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs,
    val effectiveRoles: Set<Role>,
    val agentId: String,
    val permission: ToolPolicy?
)

/**
 * Unified tool governance service that handles permission checking, rate limiting,
 * and caching in a coordinated manner following Koog's architectural patterns.
 * 
 * This service encapsulates all governance logic in a single, testable component
 * that follows the dependency injection patterns used throughout Koog.
 */
public class ToolGovernanceService(
    private val permissionChecker: PermissionChecker? = null,
    private val rateLimiter: RateLimiter? = null,
    private val toolCache: ToolCache? = null
) {
    /**
     * Checks if tool execution should be allowed, cached, or denied.
     * This is the main entry point for all governance decisions.
     */
    public suspend fun checkGovernance(context: ToolGovernanceContext): ToolGovernanceResult {
        // 1. Check permissions first (fastest check)
        val permissionResult = checkPermissions(context)
        if (permissionResult is ToolGovernanceResult.PermissionDenied) {
            return permissionResult
        }
        
        // 2. Check cache (avoid expensive operations if cached)
        val cacheResult = checkCache(context)
        if (cacheResult is ToolGovernanceResult.CacheHit) {
            return cacheResult
        }
        
        // 3. Check rate limits (more expensive, check last)
        val rateLimitResult = checkRateLimit(context)
        if (rateLimitResult is ToolGovernanceResult.RateLimited) {
            return rateLimitResult
        }
        
        return ToolGovernanceResult.Allowed
    }
    
    /**
     * Caches a tool result after successful execution.
     */
    public suspend fun cacheResult(
        context: ToolGovernanceContext,
        result: ToolResult
    ): String? {
        val toolCache = this.toolCache
        val cacheConfig = context.permission?.cacheConfig
        
        if (toolCache != null && cacheConfig != null) {
            // Cache result for the primary role (use first role for key generation)
            val primaryRole = context.effectiveRoles.firstOrNull()
            if (primaryRole != null) {
                val cacheKeyContext = CacheKeyContext(
                    tool = context.tool,
                    toolArgs = context.toolArgs,
                    effectiveRole = primaryRole,
                    agentId = context.agentId
                )
                val cacheKey = cacheConfig.keyGenerator.generateKey(cacheKeyContext)
                toolCache.put(cacheKey, result, cacheConfig.ttl)
                return cacheKey
            }
        }
        
        return null
    }
    
    private fun checkPermissions(context: ToolGovernanceContext): ToolGovernanceResult {
        val permission = context.permission ?: return ToolGovernanceResult.Allowed
        val checker = permissionChecker ?: StandardPermissionChecker.Default
        
        val permissionResult = checker.checkPermission(context.effectiveRoles, permission)
        return when (permissionResult) {
            is PermissionCheckResult.Granted -> ToolGovernanceResult.Allowed
            is PermissionCheckResult.Denied -> {
                val requiredRole = when (val requirement = permission.roleRequirement) {
                    is RoleRequirement.MinimumRole -> requirement.role.name
                    is RoleRequirement.AllowedRoles -> requirement.roles.joinToString { it.name }
                    else -> null
                }
                ToolGovernanceResult.PermissionDenied(
                    reason = permissionResult.reason,
                    requiredRole = requiredRole
                )
            }
        }
    }
    
    private suspend fun checkCache(context: ToolGovernanceContext): ToolGovernanceResult {
        val toolCache = this.toolCache
        val cacheConfig = context.permission?.cacheConfig
        
        if (toolCache != null && cacheConfig != null) {
            // Check cache for each role - return first hit
            for (role in context.effectiveRoles) {
                val cacheKeyContext = CacheKeyContext(
                    tool = context.tool,
                    toolArgs = context.toolArgs,
                    effectiveRole = role,
                    agentId = context.agentId
                )
                val cacheKey = cacheConfig.keyGenerator.generateKey(cacheKeyContext)
                
                val cachedResult = toolCache.get(cacheKey)
                if (cachedResult != null) {
                    return ToolGovernanceResult.CacheHit(cacheKey, cachedResult)
                }
            }
        }
        
        return ToolGovernanceResult.Allowed
    }
    
    private suspend fun checkRateLimit(context: ToolGovernanceContext): ToolGovernanceResult {
        val rateLimiter = this.rateLimiter
        val rateLimits = context.permission?.rateLimits
        
        if (rateLimiter != null && rateLimits != null) {
            // Check rate limits for all roles and use the most permissive
            val roleRateLimits = context.effectiveRoles.mapNotNull { role ->
                rateLimits.getRateLimitForRole(role)?.let { role to it }
            }
            
            if (roleRateLimits.isNotEmpty()) {
                // Check each role's rate limit
                for ((role, rateLimit) in roleRateLimits) {
                    val keyContext = RateLimitKeyContext(
                        tool = context.tool,
                        toolArgs = context.toolArgs,
                        effectiveRole = role,
                        agentId = context.agentId
                    )
                    val key = rateLimits.keyStrategy.generateKey(keyContext)
                    
                    // If any role allows it, the action is allowed
                    if (rateLimiter.isAllowed(key, rateLimit.limit, rateLimit.window)) {
                        return ToolGovernanceResult.Allowed
                    }
                }
                
                // All roles exceeded their limits
                val (_, firstLimit) = roleRateLimits.first()
                val message = "Rate limit exceeded for tool '${context.tool.name}'. " +
                        "Limit: ${firstLimit.limit}, resets in: ${firstLimit.window}"
                
                return ToolGovernanceResult.RateLimited(
                    reason = message,
                    limit = firstLimit.limit.toString(),
                    resetIn = firstLimit.window.toString()
                )
            }
        }
        
        return ToolGovernanceResult.Allowed
    }
}