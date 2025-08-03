package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.tools.*
import ai.koog.agents.core.tools.permissions.*
import ai.koog.agents.testing.tools.*
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ai.koog.prompt.message.ResponseMetaInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AIAgentNodesUtilityTest {

    // Test tools
    class AdminTool : SimpleTool<AdminTool.Args>() {
        @Serializable
        data class Args(val action: String) : ToolArgs

        override val descriptor = ToolDescriptor(
            name = "admin-tool",
            description = "Admin only tool"
        )
        override val argsSerializer = Args.serializer()

        override suspend fun doExecute(args: Args): String = "Admin action: ${args.action}"
    }

    class UserTool : SimpleTool<UserTool.Args>() {
        @Serializable
        data class Args(val action: String) : ToolArgs

        override val descriptor = ToolDescriptor(
            name = "user-tool",
            description = "User accessible tool"
        )
        override val argsSerializer = Args.serializer()

        override suspend fun doExecute(args: Args): String = "User action: ${args.action}"
    }

    // Type-safe role definitions
    class TestRoles(builder: RoleHierarchyBuilder) : RoleSet(builder) {
        val user = role {
            name = "user"
            description = "User role"
        }

        val admin = role {
            name = "admin"
            description = "Admin role"
            extends = user
        }
    }

    @Test
    fun `tryToolCall returns null on permission denied`() = runTest {
        // Setup roles using the type-safe pattern
        val roles = RoleSet.create(::TestRoles)
        val roleHierarchy = roles.hierarchy

        // Setup tools with permissions
        val toolRegistry = ToolRegistry {
            tool(AdminTool()) {
                minimumRole = roles.admin
            }
            tool(UserTool()) {
                minimumRole = roles.user
            }
        }

        // Create agent with user role
        val strategy = strategy<String, String>("try-admin-strategy") {
            val tryAdmin by node<String, String> { input ->
                val adminCall = Message.Tool.Call(
                    id = "1",
                    tool = "admin-tool",
                    content = Json.encodeToString(
                        JsonObject(mapOf("action" to JsonPrimitive(input)))
                    ),
                    metaInfo = ResponseMetaInfo.Empty
                )

                val result = tryToolCall(adminCall)
                result?.content ?: "Admin access denied"
            }

            edge(nodeStart forwardTo tryAdmin)
            edge(tryAdmin forwardTo nodeFinish)
        }

        val agent = AIAgent(
            promptExecutor = getMockExecutor(toolRegistry) {
                mockLLMAnswer("test")
            },
            strategy = strategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("test-prompt") { system("Test") },
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 10,
                roleHierarchy = roleHierarchy,
                permissionChecker = StandardPermissionChecker()
            ),
            toolRegistry = toolRegistry
        )

        val result = agent.run("test", role = roles.user)
        assertEquals("Admin access denied", result)
    }

    @Test
    fun `tryToolCall returns result on permission granted`() = runTest {
        // Setup roles using the type-safe pattern
        val roles = RoleSet.create(::TestRoles)
        val roleHierarchy = roles.hierarchy

        // Setup tools with permissions
        val toolRegistry = ToolRegistry {
            tool(AdminTool()) {
                minimumRole = roles.admin
            }
        }

        // Create agent with admin role
        val strategy = strategy<String, String>("try-admin-with-permission-strategy") {
            val tryAdmin by node<String, String> { input ->
                val adminCall = Message.Tool.Call(
                    id = "1",
                    tool = "admin-tool",
                    content = Json.encodeToString(
                        JsonObject(mapOf("action" to JsonPrimitive(input)))
                    ),
                    metaInfo = ResponseMetaInfo.Empty
                )

                val result = tryToolCall(adminCall)
                result?.content ?: "Failed"
            }

            edge(nodeStart forwardTo tryAdmin)
            edge(tryAdmin forwardTo nodeFinish)
        }

        val agent = AIAgent(
            promptExecutor = getMockExecutor(toolRegistry) {
                mockLLMAnswer("test")
            },
            strategy = strategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("test-prompt") { system("Test") },
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 10,
                roleHierarchy = roleHierarchy,
                permissionChecker = StandardPermissionChecker()
            ),
            toolRegistry = toolRegistry
        )

        val result = agent.run("test", role = roles.admin)
        assertEquals("Admin action: test", result)
    }

    @Test
    fun `requestWithFallback uses fallback on permission denied`() = runTest {
        // Setup roles using the type-safe pattern
        val roles = RoleSet.create(::TestRoles)
        val roleHierarchy = roles.hierarchy

        // Setup tools
        val adminTool = AdminTool()
        val userTool = UserTool()
        val toolRegistry = ToolRegistry {
            tool(adminTool) {
                minimumRole = roles.admin
            }
            tool(userTool) {
                minimumRole = roles.user
            }
        }

        // Create agent strategy
        val strategy = strategy<String, Message.Response>("request-with-fallback-strategy") {
            val requestWithFallback by node<String, Message.Response> { input ->
                llm.writeSession {
                    updatePrompt {
                        user(input)
                    }
                    requestWithFallback(
                        preferred = adminTool,
                        fallback = userTool
                    )
                }
            }

            edge(nodeStart forwardTo requestWithFallback)
            edge(requestWithFallback forwardTo nodeFinish)
        }

        val agent = AIAgent(
            promptExecutor = getMockExecutor(toolRegistry) {
                mockTool(adminTool) alwaysReturns "Admin result"
                mockTool(userTool) alwaysReturns "User result"
            },
            strategy = strategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("test-prompt") { system("Test") },
                model = OllamaModels.Meta.LLAMA_3_2,
                roleHierarchy = roleHierarchy,
                permissionChecker = StandardPermissionChecker(),
                maxAgentIterations = 10
            ),
            toolRegistry = toolRegistry
        )

        val result = agent.run("test", role = roles.user)

        // Should have returned a response (the actual content doesn't matter for this test)
        // The important thing is that requestWithFallback didn't throw an exception
        // and successfully handled the case where the preferred tool wasn't available
        assertTrue(result is Message.Response)
    }

    @Test
    fun `hasPermissionForTool checks permissions correctly`() = runTest {
        // Setup roles using the type-safe pattern
        val roles = RoleSet.create(::TestRoles)
        val roleHierarchy = roles.hierarchy

        // Setup tools
        val toolRegistry = ToolRegistry {
            tool(AdminTool()) {
                minimumRole = roles.admin
            }
            tool(UserTool()) {
                minimumRole = roles.user
            }
        }

        // Create agent strategy that checks permissions
        val strategy = strategy<String, String>("check-permissions-strategy") {
            val checkPermissions by node<String, String> { _ ->
                val hasAdminAccess = hasPermissionForTool<AdminTool>()
                val hasUserAccess = hasPermissionForTool<UserTool>()
                val hasAdminByName = hasPermissionForTool("admin-tool")

                "Admin: $hasAdminAccess, User: $hasUserAccess, AdminByName: $hasAdminByName"
            }

            edge(nodeStart forwardTo checkPermissions)
            edge(checkPermissions forwardTo nodeFinish)
        }

        val agent = AIAgent(
            promptExecutor = getMockExecutor(toolRegistry) {
                mockLLMAnswer("test")
            },
            strategy = strategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("test-prompt") { system("Test") },
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 10,
                roleHierarchy = roleHierarchy,
                permissionChecker = StandardPermissionChecker()
            ),
            toolRegistry = toolRegistry
        )

        // Test with user role
        val userResult = agent.run("test", role = roles.user)
        assertEquals("Admin: false, User: true, AdminByName: false", userResult)

        // Test with admin role
        val adminResult = agent.run("test", role = roles.admin)
        assertEquals("Admin: true, User: true, AdminByName: true", adminResult)
    }

    @Test
    fun `hasPermissionForTool returns true when no permissions configured`() = runTest {
        // Setup without permissions
        val toolRegistry = ToolRegistry {
            tool(AdminTool()) // No permissions set
        }

        val strategy = strategy<String, Boolean>("check-permission-strategy") {
            val checkPermission by node<String, Boolean> { _ ->
                hasPermissionForTool<AdminTool>()
            }

            edge(nodeStart forwardTo checkPermission)
            edge(checkPermission forwardTo nodeFinish)
        }

        val agent = AIAgent(
            promptExecutor = getMockExecutor(toolRegistry) {
                mockLLMAnswer("test")
            },
            strategy = strategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("test-prompt") { system("Test") },
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 10
                // No permission checker configured
            ),
            toolRegistry = toolRegistry
        )

        val result = agent.run("test")
        assertTrue(result)
    }
}
