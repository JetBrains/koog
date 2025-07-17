package ai.koog.agents.example.anthropic

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

/**
 * This example demonstrates the JSON schema validation error in the Anthropic API
 * when using complex nested structures in tool parameters.
 * 
 * The issue occurs in the AnthropicLLMClient.kt file, specifically in the getTypeMapForParameter() function
 * that converts ToolDescriptor objects to JSON schemas for the Anthropic API.
 * 
 * The problem is that when processing ToolParameterType.Object, the function creates invalid nested structures
 * by placing type information under a "type" key, resulting in invalid schema structures like:
 * {
 *   "type": {"type": "string"}  // Invalid nesting
 * }
 */

/**
 * Address type enum.
 */
enum class AddressType {
    HOME, WORK, OTHER
}

/**
 * An address with multiple fields.
 */
@Serializable
data class Address(
    val type: AddressType,
    val street: String,
    val city: String,
    val state: String,
    val zipCode: String
)

/**
 * A user profile with nested structures.
 */
@Serializable
data class UserProfile(
    val name: String,
    val email: String,
    val addresses: List<Address>
)

/**
 * Arguments for the complex nested tool.
 */
@Serializable
data class ComplexNestedToolArgs(
    val profile: UserProfile
) : ToolArgs

/**
 * A complex nested tool that demonstrates the JSON schema validation error.
 * This tool has parameters with complex nested structures that will trigger
 * the error in the Anthropic API.
 */
object ComplexNestedTool : SimpleTool<ComplexNestedToolArgs>() {
    override val argsSerializer = ComplexNestedToolArgs.serializer()

    override val descriptor = ToolDescriptor(
        name = "complex_nested_tool",
        description = "A tool that processes user profiles with complex nested structures.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "profile",
                description = "The user profile to process",
                type = ToolParameterType.Object(
                    properties = listOf(
                        ToolParameterDescriptor(
                            name = "name",
                            description = "The user's full name",
                            type = ToolParameterType.String
                        ),
                        ToolParameterDescriptor(
                            name = "email",
                            description = "The user's email address",
                            type = ToolParameterType.String
                        ),
                        ToolParameterDescriptor(
                            name = "addresses",
                            description = "The user's addresses",
                            type = ToolParameterType.List(
                                ToolParameterType.Object(
                                    properties = listOf(
                                        ToolParameterDescriptor(
                                            name = "type",
                                            description = "The type of address (HOME, WORK, or OTHER)",
                                            type = ToolParameterType.Enum(AddressType.entries.map { it.name }.toTypedArray())
                                        ),
                                        ToolParameterDescriptor(
                                            name = "street",
                                            description = "The street address",
                                            type = ToolParameterType.String
                                        ),
                                        ToolParameterDescriptor(
                                            name = "city",
                                            description = "The city",
                                            type = ToolParameterType.String
                                        ),
                                        ToolParameterDescriptor(
                                            name = "state",
                                            description = "The state or province",
                                            type = ToolParameterType.String
                                        ),
                                        ToolParameterDescriptor(
                                            name = "zipCode",
                                            description = "The ZIP or postal code",
                                            type = ToolParameterType.String
                                        )
                                    ),
                                    requiredProperties = listOf("type", "street", "city", "state", "zipCode")
                                )
                            )
                        )
                    ),
                    requiredProperties = listOf("name", "email", "addresses")
                )
            )
        )
    )

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
 * Main function that demonstrates the Anthropic API JSON schema validation error.
 */
fun main(): Unit = runBlocking {
    println("Starting Anthropic Schema Error Agent example...")
    println("This example demonstrates the JSON schema validation error in the Anthropic API.")
    println("The error occurs when using tools with complex nested structures.")
    println()
    
    try {
        // Create an agent with the Anthropic API
        val agent = AIAgent(
            executor = simpleAnthropicExecutor(ApiKeyService.anthropicApiKey),
            llmModel = AnthropicModels.Sonnet_3_7,
            systemPrompt = "You are a helpful assistant that can process user profiles. Please use the complex_nested_tool to process the user profile I provide.",
            toolRegistry = ToolRegistry {
                tool(AskUser)
                tool(SayToUser)
                tool(ComplexNestedTool)
            },
            installFeatures = {
                install(EventHandler) {
                    onAgentRunError { eventContext ->
                        println("ERROR: ${eventContext.throwable.javaClass.simpleName}(${eventContext.throwable.message})")
                        println(eventContext.throwable.stackTraceToString())
                        true
                    }
                    onToolCall { eventContext ->
                        println("Calling tool: ${eventContext.tool.name}")
                        println("Arguments: ${eventContext.toolArgs.toString().take(100)}...")
                    }
                }
            }
        )

        // Run the agent with a request to process a user profile
        val result = agent.run("""
            Please process this user profile:
            
            Name: John Doe
            Email: john.doe@example.com
            Addresses:
            1. HOME: 123 Main St, Springfield, IL 62701
            2. WORK: 456 Business Ave, Springfield, IL 62701
        """.trimIndent())

        println("\nResult: $result")
    } catch (e: IllegalStateException) {
        // Check if this is the specific JSON schema validation error we're looking for
        if (e.message?.contains("JSON schema is invalid") == true || 
            e.message?.contains("input_schema") == true) {
            println("\nERROR: ${e.javaClass.simpleName}(${e.message})")
            println(e.stackTraceToString())
            
            println("\nThis error is expected and demonstrates the JSON schema validation issue in the Anthropic API.")
            println("The error occurs because the getTypeMapForParameter() function in AnthropicLLMClient.kt")
            println("generates invalid JSON schemas when dealing with complex nested structures.")
            println("\nThe issue is that when processing ToolParameterType.Object, the function creates invalid nested structures")
            println("by placing type information under a \"type\" key, resulting in invalid schema structures like:")
            println("""
                {
                  "type": {"type": "string"}  // Invalid nesting
                }
            """.trimIndent())
            
            println("\nTo fix this issue, the getTypeMapForParameter() function needs to be modified to correctly")
            println("generate JSON Schema draft 2020-12 compliant schemas by:")
            println("1. Eliminating invalid nesting by merging type information directly into property schemas")
            println("2. Adding the required 'required' field for object schemas when applicable")
            println("3. Including schema metadata like 'additionalProperties: false' for stricter validation")
        } else {
            // This is some other Anthropic API error
            println("\nERROR: An unexpected error occurred with the Anthropic API: ${e.message}")
            println(e.stackTraceToString())
        }
    } catch (e: IllegalArgumentException) {
        // This is likely a missing API key error
        println("\nERROR: Missing or invalid API key: ${e.message}")
        println("Make sure you have set the ANTHROPIC_API_KEY environment variable.")
    } catch (e: Exception) {
        // Handle any other unexpected errors
        println("\nUnexpected error: ${e.javaClass.simpleName}(${e.message})")
        println(e.stackTraceToString())
    }
}