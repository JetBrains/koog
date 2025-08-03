package ai.koog.agents.example.permissions

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.*
import ai.koog.agents.core.tools.cache.CacheKeyContext
import ai.koog.agents.core.tools.cache.CacheKeyGenerator
import ai.koog.agents.core.tools.cache.DefaultCacheKeyGenerator
import ai.koog.agents.core.tools.cache.InMemoryToolCache
import ai.koog.agents.core.tools.permissions.*
import ai.koog.agents.core.tools.ratelimit.InMemoryRateLimiter
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Comprehensive example demonstrating all aspects of the permissions system:
 *
 * 1. Type-safe role hierarchy definition
 * 2. Tool permissions with minimum roles
 * 3. Rate limiting with role-based limits
 * 4. Caching with extensible cache key strategies
 * 5. Runtime role switching for agent pools
 * 6. Event handling for monitoring
 * 7. Custom cache key generators
 * 8. Node-level permissions
 */

// ========================================
// 1. Define your application's roles using the new consolidated approach
// ========================================
class AppRoles : Roles() {
    val guest by role {
        name = "guest"
        description = "Limited read-only access"
    }

    val user by role {
        name = "user"
        description = "Standard user with basic permissions"
        extends = guest
    }

    val premium by role {
        name = "premium"
        description = "Premium user with enhanced limits"
        extends = user
    }

    val moderator by role {
        name = "moderator"
        description = "Content moderation capabilities"
        extends = user
    }

    val admin by role {
        name = "admin"
        description = "Full administrative access"
        extends = premium
        isAdmin = true
    }
}

// ========================================
// 2. Define your tools
// ========================================
class ReadTool : SimpleTool<ReadTool.Args>() {
    @Serializable
    data class Args(val path: String) : ToolArgs

    override val argsSerializer = Args.serializer()
    override val descriptor = ToolDescriptor(
        name = "read",
        description = "Read a file",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "File path", ToolParameterType.String)
        )
    )

    override suspend fun doExecute(args: Args): String {
        return "Contents of ${args.path}: [file contents here]"
    }
}

class WriteTool : SimpleTool<WriteTool.Args>() {
    @Serializable
    data class Args(val path: String, val content: String) : ToolArgs

    override val argsSerializer = Args.serializer()
    override val descriptor = ToolDescriptor(
        name = "write",
        description = "Write to a file",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "File path", ToolParameterType.String),
            ToolParameterDescriptor("content", "Content to write", ToolParameterType.String)
        )
    )

    override suspend fun doExecute(args: Args): String {
        return "Wrote ${args.content.length} characters to ${args.path}"
    }
}

class DeleteTool : SimpleTool<DeleteTool.Args>() {
    @Serializable
    data class Args(val path: String) : ToolArgs

    override val argsSerializer = Args.serializer()
    override val descriptor = ToolDescriptor(
        name = "delete",
        description = "Delete a file",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "File path", ToolParameterType.String)
        )
    )

    override suspend fun doExecute(args: Args): String {
        return "Deleted ${args.path}"
    }
}

// ========================================
// 3. Custom cache key generator example
// ========================================
class TenantAwareCacheKeyGenerator(
    private val tenantId: String
) : CacheKeyGenerator {
    override fun generateKey(context: CacheKeyContext): String {
        return "tenant:$tenantId:tool:${context.tool.name}:args:${context.toolArgs.hashCode()}"
    }
}

// ========================================
// Main example
// ========================================
fun main() = runBlocking {
    println("=== Comprehensive Permissions Example ===\n")

    // Create roles
    val roles = AppRoles()

    println("Role Hierarchy:")
    println("- ${roles.guest.name}: ${roles.guest.description}")
    println("- ${roles.user.name}: ${roles.user.description}")
    println("- ${roles.premium.name}: ${roles.premium.description}")
    println("- ${roles.moderator.name}: ${roles.moderator.description}")
    println("- ${roles.admin.name}: ${roles.admin.description}")
    println()

    // ========================================
    // 4. Create tool registry with comprehensive governance
    // ========================================
    val toolRegistry = ToolRegistry {
        // Read tool - accessible to all authenticated users
        tool(ReadTool()) {
            minimumRole = roles.guest

            // Rate limiting
            rateLimits {
                role(roles.guest) { limit(10, 1.minutes) }
                role(roles.user) { limit(100, 1.minutes) }
                role(roles.premium) { limit(1000, 1.minutes) }
                role(roles.admin) { unlimited() }
            }

            // Caching
            cache {
                enabled = true
                ttl = 5.minutes
                keyGenerator = DefaultCacheKeyGenerator(includeRole = true)
            }
        }

        // Write tool - requires user role
        tool(WriteTool()) {
            minimumRole = roles.user

            rateLimits {
                role(roles.user) { limit(10, 1.minutes) }
                role(roles.premium) { limit(50, 1.minutes) }
                role(roles.moderator) { limit(100, 1.minutes) }
                role(roles.admin) { unlimited() }
            }

            // No caching for write operations
            cache {
                enabled = false
            }
        }

        // Delete tool - admin only
        tool(DeleteTool()) {
            minimumRole = roles.admin

            // Even admins have rate limits for dangerous operations
            rateLimits {
                role(roles.admin) { limit(10, 1.minutes) }
            }
        }
    }

    // ========================================
    // 5. Create agent with governance components
    // ========================================

    // Create mock executor for demo
    val mockExecutor = getMockExecutor(toolRegistry = toolRegistry) {
        // Set up mock responses - in real use, these would be based on request patterns
        mockLLMAnswer("I've completed the requested operations.").asDefaultResponse
    }

    // Create agent configured with permissions system
    val agent = AIAgent<String, String>(
        promptExecutor = mockExecutor,
        strategy = singleRunStrategy(),
        agentConfig = AIAgentConfig(
            prompt = prompt("assistant") {
                system("You are a helpful file management assistant.")
            },
            model = OpenAIModels.CostOptimized.GPT4oMini,
            maxAgentIterations = 50,

            // Permission configuration
            roleHierarchy = roles.hierarchy,
            permissionChecker = StandardPermissionChecker(),
            rateLimiter = InMemoryRateLimiter(),
            toolCache = InMemoryToolCache()
        ),
        toolRegistry = toolRegistry
    ) {
        // ========================================
        // 6. Install event handler for monitoring
        // ========================================
        install(EventHandler) {
            onToolPermissionDenied { event ->
                println("🚫 Permission Denied: ${event.tool.name} requires ${event.requiredRole}, but user has ${event.effectiveRoles?.joinToString()}")
            }

            onToolRateLimitExceeded { event ->
                println("⏱️ Rate Limit Exceeded: ${event.tool.name} - limit: ${event.limit}, resets in: ${event.resetIn}")
            }

            onToolCacheHit { event ->
                println("💾 Cache Hit: ${event.tool.name} with key: ${event.cacheKey}")
            }

            onToolCacheMiss { event ->
                println("❌ Cache Miss: ${event.tool.name} with key: ${event.cacheKey}")
            }

            onToolCall { event ->
                println("🔧 Tool Called: ${event.tool.name}")
            }

            onToolCallResult { event ->
                println("✅ Tool Result: ${event.tool.name} returned successfully")
            }
        }
    }

    // ========================================
    // 7. Demonstrate runtime role switching
    // ========================================
    println("\n=== Testing with Different Roles ===\n")

    // Test 1: Guest role (can only read)
    println("--- Guest User ---")
    val guestResult = agent.run(
        "Please read /etc/passwd, write to /tmp/test.txt, and delete it",
        role = roles.guest
    )
    println("Result: $guestResult\n")

    // Test 2: Regular user (can read and write)
    println("--- Regular User ---")
    val userResult = agent.run(
        "Please read /etc/passwd, write to /tmp/test.txt, and delete it",
        role = roles.user
    )
    println("Result: $userResult\n")

    // Test 3: Admin (can do everything)
    println("--- Admin User ---")
    val adminResult = agent.run(
        "Please read /etc/passwd, write to /tmp/test.txt, and delete it",
        role = roles.admin
    )
    println("Result: $adminResult\n")

    // Test 4: Multiple roles (e.g., user is both premium and moderator)
    println("--- Premium + Moderator User ---")
    val multiRoleResult = agent.run(
        "Please read /etc/passwd and write to /tmp/test.txt",
        roles = setOf(roles.premium, roles.moderator)
    )
    println("Result: $multiRoleResult (highest limits from both roles applied)\n")

    // ========================================
    // 8. Demonstrate caching
    // ========================================
    println("=== Testing Caching ===\n")

    // First call - cache miss
    println("First read (cache miss expected):")
    agent.run("Read /etc/hosts", role = roles.user)

    // Second call - cache hit
    println("\nSecond read (cache hit expected):")
    agent.run("Read /etc/hosts", role = roles.user)

    // Different user - cache miss (because cache key includes role)
    println("\nDifferent user read (cache miss expected):")
    agent.run("Read /etc/hosts", role = roles.admin)

    // ========================================
    // 9. Demonstrate rate limiting
    // ========================================
    println("\n=== Testing Rate Limiting ===\n")

    // TODO: Create a fresh agent with clean rate limiter
    // This would require refactoring the agent creation pattern
    val rateLimitTestAgent = agent

    // Guest has limit of 10 reads per minute
    println("Testing guest rate limit (10 per minute):")
    repeat(12) { i ->
        println("Attempt ${i + 1}:")
        rateLimitTestAgent.run("Read /tmp/file$i.txt", role = roles.guest)
    }

    println("\n=== Key Features Demonstrated ===")
    println("1. Type-safe role definitions with no string lookups")
    println("2. Hierarchical roles with inheritance")
    println("3. Tool-specific permissions and rate limits")
    println("4. Role-based caching strategies")
    println("5. Runtime role switching for agent pools")
    println("6. Comprehensive event monitoring")
    println("7. Extensible cache key generation")
    println("8. Production-ready governance system")
}

/**
 * Additional examples of custom implementations:
 */

// Custom permission checker that logs all checks
class AuditingPermissionChecker : PermissionChecker {
    private val delegate = StandardPermissionChecker()

    override fun checkPermission(agentRole: Role, toolPolicy: ToolPolicy): PermissionCheckResult {
        val result = delegate.checkPermission(agentRole, toolPolicy)
        println("Permission check: Role=${agentRole.name}, Result=$result")
        return result
    }

    override fun checkPermission(agentRoles: Set<Role>, toolPolicy: ToolPolicy): PermissionCheckResult {
        val result = delegate.checkPermission(agentRoles, toolPolicy)
        println("Permission check: Roles=${agentRoles.map { it.name }}, Result=$result")
        return result
    }
}

// Time-window based cache key for analytics
class TimeWindowCacheKeyGenerator(
    private val windowSize: kotlin.time.Duration = 1.hours
) : CacheKeyGenerator {
    override fun generateKey(context: CacheKeyContext): String {
        val window = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() / windowSize.inWholeMilliseconds
        return "window:$window:tool:${context.tool.name}:args:${context.toolArgs.hashCode()}"
    }
}
