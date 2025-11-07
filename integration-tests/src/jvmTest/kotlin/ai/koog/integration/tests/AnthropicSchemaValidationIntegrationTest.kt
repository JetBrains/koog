package ai.koog.integration.tests

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.integration.tests.utils.APIKeys.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.annotations.Retry
import ai.koog.integration.tests.utils.tools.ComplexNestedTool
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for verifying the fix for the Anthropic API JSON schema validation error
 * when using complex nested structures in tool parameters.
 *
 * The issue was in the AnthropicLLMClient.kt file, specifically in the getTypeMapForParameter() function
 * that converts ToolDescriptor objects to JSON schemas for the Anthropic API.
 *
 * The problem was that when processing ToolParameterType.Object, the function created invalid nested structures
 * by placing type information under a "type" key, resulting in invalid schema structures like:
 * {
 *   "type": {"type": "string"} // Invalid nesting
 * }
 *
 * This test verifies that the fix works by creating an agent with the Anthropic API and a tool
 * with complex nested structures and then running it with a sample input.
 */
class AnthropicSchemaValidationIntegrationTest {
    companion object {
        private var anthropicApiKey: String? = null
        private var apiKeyAvailable = false

        @BeforeAll
        @JvmStatic
        fun setup() {
            try {
                anthropicApiKey = readTestAnthropicKeyFromEnv()
                // Check that the API key is not empty or blank
                apiKeyAvailable = !anthropicApiKey.isNullOrBlank()
                if (!apiKeyAvailable) {
                    println("Anthropic API key is empty or blank")
                    println("Tests requiring Anthropic API will be skipped")
                }
            } catch (e: Exception) {
                println("Anthropic API key not available: ${e.message}")
                println("Tests requiring Anthropic API will be skipped")
                apiKeyAvailable = false
            }
        }
    }

    /**
     * Test that verifies the fix for the Anthropic API JSON schema validation error
     * when using complex nested structures in tool parameters.
     *
     * Before the fix, this test would fail with an error like:
     * "tools.0.custom.input_schema: JSON schema is invalid. It must match JSON Schema draft 2020-12"
     *
     * After the fix, the test should pass, demonstrating that the Anthropic API
     * can now correctly handle complex nested structures in tool parameters.
     *
     * Note: This test requires a valid Anthropic API key to be set in the environment variable
     * ANTHROPIC_API_TEST_KEY. If the key is not available, the test will be skipped.
     */
    @Retry
    @Test
    fun integration_testAnthropicComplexNestedStructures() {
        // Skip the test if the Anthropic API key is not available
        assumeTrue(apiKeyAvailable, "Anthropic API key is not available")

        runBlocking {
            // Create an agent with the Anthropic API and the complex nested tool
            val agent = AIAgent(
                promptExecutor = simpleAnthropicExecutor(anthropicApiKey!!),
                llmModel = AnthropicModels.Sonnet_3_7,
                systemPrompt = "You are a helpful assistant that can process user profiles. Please use the complex_nested_tool to process the user profile I provide.",
                toolRegistry = ToolRegistry {
                    tool(ComplexNestedTool)
                },
                installFeatures = {
                    install(EventHandler) {
                        onAgentExecutionFailed { eventContext ->
                            println(
                                "ERROR: ${eventContext.throwable.javaClass.simpleName}(${eventContext.throwable.message})"
                            )
                            println(eventContext.throwable.stackTraceToString())
                        }
                        onToolCallStarting { eventContext ->
                            println("Calling tool: ${eventContext.tool.name}")
                            println("Arguments: ${eventContext.toolArgs.toString().take(100)}...")
                        }
                    }
                }
            )

            // Run the agent with a request to process a user profile
            val result = agent.run(
                """
                Please process this user profile:
                
                Name: John Doe
                Email: john.doe@example.com
                Addresses:
                1. HOME: 123 Main St, Springfield, IL 62701
                2. WORK: 456 Business Ave, Springfield, IL 62701
                """.trimIndent()
            )

            // Verify the result
            println("\nResult: $result")
            assertNotNull(result, "Result should not be null")
            assertTrue(result.isNotBlank(), "Result should not be empty or blank")

            // Check that the result contains expected information
            assertTrue(result.lowercase().contains("john doe"), "Result should contain the user's name")
            assertTrue(result.lowercase().contains("john.doe@example.com"), "Result should contain the user's email")
            assertTrue(result.lowercase().contains("main st"), "Result should contain the home address street")
            assertTrue(result.lowercase().contains("business ave"), "Result should contain the work address street")
        }
    }
}
