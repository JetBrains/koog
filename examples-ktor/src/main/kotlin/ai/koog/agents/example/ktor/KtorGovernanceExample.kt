package ai.koog.agents.example.ktor

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.cache.DefaultCacheKeyGenerator
import ai.koog.agents.core.tools.permissions.*
import ai.koog.agents.core.tools.ratelimit.InMemoryRateLimiter
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.ktor.Koog
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive example demonstrating how to integrate the unified tool governance system
 * with a Ktor web application.
 *
 * This example shows:
 * 1. JWT-based authentication with role extraction
 * 2. Dynamic role assignment based on user attributes
 * 3. Tool permissions enforced through governance
 * 4. Rate limiting per user/tenant/API version
 * 5. Caching of tool results
 * 6. Real-time permission events
 * 7. Context-aware agent creation per request
 * 8. Automatic mock/real LLM mode detection for testing and production
 */

// Example tools for the web API
class SearchTool : SimpleTool<SearchTool.Args>() {
    @Serializable
    data class Args(val query: String) : ToolArgs

    override val descriptor = ToolDescriptor(
        name = "search",
        description = "Search for information",
        requiredParameters = listOf(
            ToolParameterDescriptor("query", "Search query", ToolParameterType.String)
        )
    )

    override val argsSerializer = Args.serializer()

    override suspend fun doExecute(args: Args): String {
        return "Found 10 results for '${args.query}'"
    }
}

class DatabaseTool : SimpleTool<DatabaseTool.Args>() {
    @Serializable
    data class Args(val operation: String, val data: String) : ToolArgs

    override val descriptor = ToolDescriptor(
        name = "database",
        description = "Database operations",
        requiredParameters = listOf(
            ToolParameterDescriptor("operation", "Operation type", ToolParameterType.String),
            ToolParameterDescriptor("data", "Data to process", ToolParameterType.String)
        )
    )

    override val argsSerializer = Args.serializer()

    override suspend fun doExecute(args: Args): String {
        return "Database ${args.operation} completed"
    }
}

class AdminTool : SimpleTool<AdminTool.Args>() {
    @Serializable
    data class Args(val command: String) : ToolArgs

    override val descriptor = ToolDescriptor(
        name = "admin",
        description = "Administrative commands",
        requiredParameters = listOf(
            ToolParameterDescriptor("command", "Admin command", ToolParameterType.String)
        )
    )

    override val argsSerializer = Args.serializer()

    override suspend fun doExecute(args: Args): String {
        return "Admin command '${args.command}' executed"
    }
}

// User principal that includes role information
data class UserPrincipal(
    val userId: String,
    val email: String,
    val tenant: String,
    val isAdmin: Boolean
) : Principal

fun Application.module() {
    // Configure JSON serialization
    install(ContentNegotiation) {
        json()
    }

    // 1. Configure authentication
    install(Authentication) {
        jwt("auth-jwt") {
            // In production, configure proper JWT verification
            validate { credential ->
                val userId = credential.payload.getClaim("sub").asString()
                val email = credential.payload.getClaim("email").asString()
                val tenant = credential.payload.getClaim("tenant").asString() ?: "default"
                val isAdmin = credential.payload.getClaim("admin")?.asBoolean() ?: false

                if (userId != null && email != null) {
                    UserPrincipal(userId, email, tenant, isAdmin)
                } else {
                    null
                }
            }
        }
    }

    // 2. Define type-safe roles
    class KtorRoles : Roles() {
        val guest by role {
            name = "guest"
            description = "Unauthenticated users"
        }

        val user by role {
            name = "user"
            description = "Authenticated users"
            extends = guest
        }

        val premium by role {
            name = "premium"
            description = "Premium subscribers"
            extends = user
        }

        val admin by role {
            name = "admin"
            description = "System administrators"
            extends = premium
            isAdmin = true
        }
    }

    val roles = KtorRoles()
    val roleHierarchy = roles.hierarchy

    // 3. Configure tool permissions
    val toolRegistry = ToolRegistry {
        // Search is available to all
        tool(SearchTool()) {
            // No restrictions
        }

        // Database requires authentication with rate limits
        tool(DatabaseTool()) {
            minimumRole = roles.user

            rateLimits {
                role(roles.guest) {
                    limit(0, 1.minutes) // No access
                }
                role(roles.user) {
                    limit(10, 1.minutes)
                }
                role(roles.premium) {
                    limit(50, 1.minutes)
                }
                role(roles.admin) {
                    unlimited()
                }
            }

            cache {
                enabled = true
                ttl = 30.seconds
                keyGenerator = DefaultCacheKeyGenerator()
            }
        }

        // Admin tool is admin-only
        tool(AdminTool()) {
            minimumRole = roles.admin
        }
    }

    // 4. Install Koog with mock mode enabled by default
    // - Uses mock mode by default for examples and testing
    // - Automatically detects test environments
    // - Uses real LLMs if properly configured (uncomment LLM config below)
    // - Falls back to mock mode if no LLM configuration found
    val koog = install(Koog) {
        mockMode()

        // Optional: Override with real LLM configuration if API keys are available
        // Uncomment and provide real API keys to use actual LLMs instead of mocks
        /*
        llm {
            openAI(apiKey = System.getenv("OPENAI_API_KEY") ?: error("OpenAI API key required")) {
                // Configure OpenAI settings if needed
            }

            fallback { }
        }
         */

        agentConfig {
            prompt {
                system("You are a helpful AI assistant.")
            }
            maxAgentIterations = 10
        }
    }

    // 5. Define routes
    routing {
        // Public endpoint - no authentication
        post("/api/v1/agent/public") {
            val request = call.receive<AgentRequest>()

            // Create agent with guest role - Koog automatically handles mock vs real LLM mode
            val agent = createAgent(
                koog = koog,
                toolRegistry = toolRegistry,
                roleHierarchy = roleHierarchy,
                role = roles.guest
            )

            try {
                val result = agent.run(request.message)
                call.respond(AgentResponse(result, "guest"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // Authenticated endpoints
        authenticate("auth-jwt") {
            post("/api/v1/agent") {
                val principal = call.principal<UserPrincipal>()!!
                val request = call.receive<AgentRequest>()

                // Determine role based on user attributes
                val role = when {
                    principal.isAdmin -> roles.admin
                    principal.email.endsWith("@premium.com") -> roles.premium
                    else -> roles.user
                }

                // Create agent with user's role and governance - Koog handles mode automatically
                val agent = createAgent(
                    koog = koog,
                    toolRegistry = toolRegistry,
                    roleHierarchy = roleHierarchy,
                    role = role,
                    userId = principal.userId
                )

                try {
                    val result = agent.run(request.message)
                    call.respond(AgentResponse(result, role.name))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Unknown error"))
                }
            }

            // Admin endpoint for testing governance events
            get("/api/v1/agent/events") {
                val principal = call.principal<UserPrincipal>()!!

                if (!principal.isAdmin) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin access required"))
                    return@get
                }

                // Stream governance events (simplified - in production use SSE or WebSockets)
                call.respondText("Governance events would be streamed here")
            }
        }
    }
}

// Helper to create agent with governance
private suspend fun createAgent(
    koog: Koog,
    toolRegistry: ToolRegistry,
    roleHierarchy: RoleHierarchy,
    role: Role,
    userId: String = "anonymous"
): AIAgent<String, String> {
    val rateLimiter = InMemoryRateLimiter()
    val permissionChecker = StandardPermissionChecker()

    // Create agent config with role-specific system prompt
    val agentConfig = AIAgentConfig(
        prompt = prompt("agent-with-role") {
            system("You are an AI assistant with role: ${role.name}")
        },
        model = OpenAIModels.CostOptimized.GPT4oMini,
        maxAgentIterations = 10,
        roleHierarchy = roleHierarchy,
        permissionChecker = permissionChecker,
        rateLimiter = rateLimiter
    )

    // Use Koog's configured executor (handles mock vs real LLM automatically)
    return AIAgent(
        strategy = singleRunStrategy(),
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
        promptExecutor = koog.promptExecutor
    ) {
        // Install event handler to log governance events
        install(EventHandler) {
            onToolPermissionDenied { context ->
                koog.application.log.warn("Permission denied for user $userId: ${context.tool.name} requires ${context.requiredRole}")
            }

            onToolRateLimitExceeded { context ->
                koog.application.log.warn("Rate limited for user $userId: ${context.tool.name}")
            }

            onToolCacheHit { context ->
                koog.application.log.debug("Cache hit for user $userId: ${context.tool.name}")
            }
        }
    }
}

// Request/Response models
@Serializable
data class AgentRequest(val message: String)

@Serializable
data class AgentResponse(val result: String, val role: String)

@Serializable
data class ErrorResponse(val error: String)

/**
 * Example usage:
 *
 * 1. Public access (guest):
 *    POST /api/v1/agent/public
 *    Body: {"message": "Search for Kotlin tutorials"}
 *
 * 2. Authenticated access (user/premium/admin):
 *    POST /api/v1/agent
 *    Headers: Authorization: Bearer <JWT>
 *    Body: {"message": "Query the user database"}
 *
 * 3. Admin monitoring:
 *    GET /api/v1/agent/events
 *    Headers: Authorization: Bearer <ADMIN_JWT>
 *
 * Note: This example demonstrates how to integrate the permission system with Ktor
 * using Koog's automatic mode detection. The same Koog instance seamlessly handles
 * both test and production modes without requiring manual configuration.
 *
 * To run it as a standalone server via gradle: ./gradlew runExampleKtorGovernance
 * The server will start on port 8080 (default Ktor port).
 *
 * Test with: curl -X POST http://localhost:8080/api/v1/agent/public -H "Content-Type: application/json" -d '{"message":"Search for Kotlin tutorials"}'
 *
 * For testing mode, set the environment variable: ktor.test=true
 */

// This main function allows the example to be run as a standalone Ktor server if needed
fun main(args: Array<String>) = io.ktor.server.cio.EngineMain.main(args)
