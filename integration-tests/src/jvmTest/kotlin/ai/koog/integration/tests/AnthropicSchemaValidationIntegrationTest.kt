package ai.koog.integration.tests

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.annotations.Retry
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
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
 *   "type": {"type": "string"}  // Invalid nesting
 * }
 *
 * This test verifies that the fix works by creating an agent with the Anthropic API and a tool
 * with complex nested structures, and then running it with a sample input.
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
     * Address type enum.
     */
    @Serializable
    enum class AddressType {
        HOME,
        WORK,
        OTHER
    }

    /**
     * An address with multiple fields.
     */
    @Serializable
    data class Address(
        @property:LLMDescription("The type of address (HOME, WORK, or OTHER)")
        val type: AddressType,
        @property:LLMDescription("The street address")
        val street: String,
        @property:LLMDescription("The city")
        val city: String,
        @property:LLMDescription("The state or province")
        val state: String,
        @property:LLMDescription("The ZIP or postal code")
        val zipCode: String
    )

    /**
     * A user profile with nested structures.
     */
    @Serializable
    data class UserProfile(
        @property:LLMDescription("The user's full name")
        val name: String,
        @property:LLMDescription("The user's email address")
        val email: String,
        @property:LLMDescription("The user's addresses")
        val addresses: List<Address>
    )

    /**
     * Arguments for the complex nested tool.
     */
    @Serializable
    data class ComplexNestedToolArgs(
        @property:LLMDescription("The user profile to process")
        val profile: UserProfile
    )

    /**
     * A complex nested tool that demonstrates the JSON schema validation error.
     * This tool has parameters with complex nested structures that would trigger
     * the error in the Anthropic API before the fix.
     */
    object ComplexNestedTool : SimpleTool<ComplexNestedToolArgs>() {
        override val argsSerializer = ComplexNestedToolArgs.serializer()

        override val name = "complex_nested_tool"

        override val description = "A tool that processes user profiles with complex nested structures."

        override suspend fun doExecute(args: ComplexNestedToolArgs): String {
            // Process the user profile
            val profile = args.profile
            val addressesInfo = profile.addresses.joinToString("\n") { address ->
                "- ${address.type} Address: ${address.street}, ${address.city}, ${address.state} ${address.zipCode}"
            }

            return """
                Successfully processed user profile:
                Name: ${profile.name}
                Email: ${profile.email}
                Addresses:
                $addressesInfo
            """.trimIndent()
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
                        onToolExecutionStarting { eventContext ->
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
