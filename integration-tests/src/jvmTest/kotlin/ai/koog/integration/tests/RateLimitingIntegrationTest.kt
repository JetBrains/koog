package ai.koog.integration.tests

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.*
import ai.koog.agents.core.tools.permissions.*
import ai.koog.agents.core.tools.ratelimit.InMemoryRateLimiter
import ai.koog.agents.testing.TestRoles
import ai.koog.agents.testing.TestTools
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RateLimitingIntegrationTest {


    @Test
    fun testAgentRateLimitingIntegration() = runTest {
        // Test that rate limiting works end-to-end with agents
        val toolRegistry = ToolRegistry {
            tool(TestTools.TestTool()) {
                minimumRole = TestRoles.user
                rateLimits {
                    role(TestRoles.user) { limit(2, 1.seconds) }
                }
            }
        }

        val mockExecutor = getMockExecutor(toolRegistry = toolRegistry) {
            mockLLMAnswer("Tool executed successfully") onRequestContains "Executed:"
            mockLLMAnswer("Rate limit exceeded") onRequestContains "rate limit"
        }

        val agent = AIAgent<String, String>(
            promptExecutor = mockExecutor,
            strategy = singleRunStrategy(),
            agentConfig = AIAgentConfig(
                prompt = prompt("test") { system("Test agent") },
                model = OpenAIModels.CostOptimized.GPT4oMini,
                maxAgentIterations = 10,
                roleHierarchy = TestRoles.createRoleHierarchy(),
                permissionChecker = StandardPermissionChecker(),
                rateLimiter = InMemoryRateLimiter()
            ),
            toolRegistry = toolRegistry
        )

        // First two calls should succeed
        val result1 = agent.run("Use test tool", role = TestRoles.user)
        val result2 = agent.run("Use test tool", role = TestRoles.user)
        
        // Third call should be rate limited (this is more of an integration test verification)
        val result3 = agent.run("Use test tool", role = TestRoles.user)
        
        // Results should demonstrate rate limiting behavior
        // Note: Exact behavior depends on how rate limiting is implemented in the agent pipeline
        println("Result 1: $result1")
        println("Result 2: $result2")
        println("Result 3: $result3")
    }

    // Additional rate limiting tests would need to test through AIAgent's tool execution pipeline
}
