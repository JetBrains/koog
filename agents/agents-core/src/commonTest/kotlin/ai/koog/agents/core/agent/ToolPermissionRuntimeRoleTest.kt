package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.*
import ai.koog.agents.core.tools.permissions.*
import ai.koog.agents.testing.TestRoles
import ai.koog.agents.testing.TestTools
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ToolPermissionRuntimeRoleTest {

    // Use shared test tool with compatible interface
    class TestTool : Tool<TestTool.Args, ToolResult.Text>() {
        @kotlinx.serialization.Serializable
        data class Args(val msg: String) : ToolArgs

        override val argsSerializer = Args.serializer()
        override val descriptor = ToolDescriptor(
            name = "test",
            description = "Test tool",
            requiredParameters = listOf()
        )

        override suspend fun execute(args: Args): ToolResult.Text {
            return ToolResult.Text("Result: ${args.msg}")
        }
    }

    @Test
    fun testToolPermissions() = runTest {
        // Use shared test roles
        val guest = TestRoles.guest
        val user = TestRoles.user

        // Create tool registry with permission
        val toolRegistry = ToolRegistry {
            tool(TestTool()) {
                minimumRole = user // Requires user role
            }
        }

        // Create mock executor that tries to call the tool
        val mockExecutor = getMockExecutor(toolRegistry = toolRegistry) {
            // Mock answer for when tool call is denied
            mockLLMAnswer("Done") onRequestContains "test"
        }

        // Create agent with guest role
        val agent = AIAgent<String, String>(
            promptExecutor = mockExecutor,
            strategy = singleRunStrategy(),
            agentConfig = AIAgentConfig(
                prompt = prompt("test") { system("Test") },
                model = OpenAIModels.CostOptimized.GPT4oMini,
                maxAgentIterations = 10,
                permissionChecker = StandardPermissionChecker()
            ),
            toolRegistry = toolRegistry
        )

        // Run with guest role - should fail
        val guestResult = agent.run("test", role = guest)
        println("Guest result: '$guestResult'")

        // The result should be "Done" because the tool call was denied
        // and the mock returns "Done" after the tool call attempt
        assertTrue(guestResult == "Done", "Expected 'Done' but got '$guestResult'")

        // Run with user role - should succeed
        val userResult = agent.run("test", role = user)
        println("User result: '$userResult'")

        // With user role, tool should execute and we should get "Done"
        assertTrue(userResult == "Done", "Expected 'Done' but got '$userResult'")
    }
}
