package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.*
import ai.koog.agents.core.tools.permissions.*
import ai.koog.agents.core.tools.ratelimit.*
import ai.koog.agents.testing.TestRoles
import ai.koog.agents.testing.TestTools
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.testing.tools.mockLLMToolCall
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class RuntimeRoleTest {



    @Test
    fun testRuntimeRoleOverridesConfiguredRole() = runTest {
        // Create roles with type-safe access
        val roles = TestRoles.createTypeSafeRoles()

        // Create tool registry with permissions
        val toolRegistry = ToolRegistry {
            tool(TestTools.TestTool()) {
                minimumRole = roles.user // Type-safe!
            }
        }

        // Create mock executor - we'll use onRequestContains to match different messages
        val mockExecutor = getMockExecutor(toolRegistry = toolRegistry) {
            // For any request, try to use the tool
            mockLLMToolCall(TestTools.TestTool(), TestTools.TestTool.Args("test message")) onRequestContains "test tool"

            // After tool execution (or failure), return appropriate response based on result
            mockLLMAnswer("Tool executed successfully") onRequestContains "Executed: test message"
            mockLLMAnswer("Permission was denied") onRequestContains "requires user role"
        }

        // Create agent with guest role as default
        val agent = AIAgent<String, String>(
            promptExecutor = mockExecutor,
            strategy = singleRunStrategy(),
            agentConfig = AIAgentConfig(
                prompt = prompt("test-agent") {
                    system("You are a test agent.")
                },
                model = OpenAIModels.CostOptimized.GPT4oMini,
                maxAgentIterations = 50,
                roleHierarchy = roles.hierarchy,
                permissionChecker = StandardPermissionChecker()
            ),
            toolRegistry = toolRegistry
        )

        // Test 1: Guest role (should fail due to permissions)
        val guestResult = agent.run("Use the test tool", role = roles.guest)
        println("Guest result: '$guestResult'")
        assertTrue(
            guestResult.isEmpty() || guestResult.contains("denied", ignoreCase = true) || guestResult.contains("requires user role", ignoreCase = true),
            "Expected permission denial but got: '$guestResult'"
        )

        // Test 2: User role (should succeed)
        val userResult = agent.run("Use the test tool", role = roles.user)
        assertContains(userResult, "executed", ignoreCase = true)

        // Test 3: Admin role (should succeed)
        val adminResult = agent.run("Use the test tool", role = roles.admin)
        assertContains(adminResult, "executed", ignoreCase = true)

        // Test 4: No runtime role uses configured role (guest, should fail)  
        val defaultResult = agent.run("Use the test tool")
        // The default behavior should result in empty response or permission denial
        assertTrue(
            defaultResult.isEmpty() || defaultResult.contains("denied", ignoreCase = true) || defaultResult.contains("permission", ignoreCase = true),
            "Expected permission denial or empty result but got: '$defaultResult'"
        )
    }
}
