package ai.koog.prompt.executor.clients.anthropic

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

fun readTestAnthropicKeyFromEnv(): String {
    return System.getenv("ANTHROPIC_API_TEST_KEY")
        ?: error("ERROR: environment variable ANTHROPIC_API_TEST_KEY not set")
}

class AnthropicLLMClientTest {

    // API key for testing
    private val apiKey: String get() = readTestAnthropicKeyFromEnv()

    // TODO add parametrized test
    val allModels = listOf(
        AnthropicModels.Sonnet_3_7,
        AnthropicModels.Sonnet_3_5,
        AnthropicModels.Sonnet_3,
        AnthropicModels.Opus,
        AnthropicModels.Haiku_3,
        AnthropicModels.Haiku_3_5
    )

    @Disabled("TODO: pass the `ANTHROPIC_API_TEST_KEY`")
    @Test
    fun testCreateClient() {
        val client = AnthropicLLMClient(apiKey)
        assertNotNull(client, "Client should be created successfully")
    }

    fun testWithAllModels(test: suspend (model: LLModel) -> Unit) {
        allModels.forEach { model ->
            println("Testing with model: $model")
            runTest { test(model) }
        }
    }

    @Disabled("TODO: pass the `ANTHROPIC_API_TEST_KEY`")
    @Test
    fun testExecuteSimplePrompt() = testWithAllModels {
        val client = AnthropicLLMClient(apiKey)

        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = client.execute(prompt, AnthropicModels.Sonnet_3_7)

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")
        assertTrue(
            (response.first() as Message.Assistant).content.contains("Paris", ignoreCase = true),
            "Response should contain 'Paris'"
        )
    }

    @Disabled("TODO: pass the `ANTHROPIC_API_TEST_KEY`")
    @Test
    fun testExecuteStreamingPrompt() = testWithAllModels {
        val client = AnthropicLLMClient(apiKey)

        val prompt = Prompt.build( "test-streaming") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }

        val responseChunks = client.executeStreaming(prompt, AnthropicModels.Sonnet_3_7).toList()

        assertNotNull(responseChunks, "Response chunks should not be null")
        assertTrue(responseChunks.isNotEmpty(), "Response chunks should not be empty")

        // Combine all chunks to check the full response
        val fullResponse = responseChunks.joinToString("")
        assertTrue(
            fullResponse.contains("1") &&
                    fullResponse.contains("2") &&
                    fullResponse.contains("3") &&
                    fullResponse.contains("4") &&
                    fullResponse.contains("5"),
            "Full response should contain numbers 1 through 5"
        )
    }

    @Serializable
    enum class CalculatorOperation {
        ADD, SUBTRACT, MULTIPLY, DIVIDE
    }

    @Disabled("TODO: pass the `ANTHROPIC_API_TEST_KEY`")
    @Test
    fun testExecuteWithTools() = testWithAllModels {
        val client = AnthropicLLMClient(apiKey)

        // Define a simple calculator tool
        val calculatorTool = ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "operation",
                    description = "The operation to perform.",
                    type = ToolParameterType.Enum(CalculatorOperation.entries.map { it.name }.toTypedArray())
                ),
                ToolParameterDescriptor(
                    name = "a",
                    description = "The first argument (number)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "b",
                    description = "The second argument (number)",
                    type = ToolParameterType.Integer
                )
            )
        )

        val prompt = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a calculator tool.")
            user("What is 123 + 456?")
        }

        val response = client.execute(prompt, AnthropicModels.Sonnet_3_7, listOf(calculatorTool))

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")

        // The response might be either a direct answer or a tool call
        if (response.first() is Message.Tool.Call) {
            val toolCall = response.first() as Message.Tool.Call
            assertEquals("calculator", toolCall.tool, "Tool name should be 'calculator'")
            assertTrue(toolCall.content.contains("add"), "Tool call should use 'ADD' operation")
            assertTrue(toolCall.content.contains("123"), "Tool call should include first number")
            assertTrue(toolCall.content.contains("456"), "Tool call should include second number")
        } else {
            assertTrue(
                (response.first() as Message.Assistant).content.contains("579"),
                "Response should contain the correct answer '579'"
            )
        }
    }

    @Disabled("TODO: pass the `ANTHROPIC_API_TEST_KEY`")
    @Test
    fun testCodeGeneration() = testWithAllModels {
        val client = AnthropicLLMClient(apiKey)

        val prompt = Prompt.build("test-code") {
            system("You are a helpful coding assistant.")
            user("Write a simple Kotlin function to calculate the factorial of a number.")
        }

        val response = client.execute(prompt, AnthropicModels.Sonnet_3_5)

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")

        val content = (response.first() as Message.Assistant).content
        assertTrue(content.contains("fun factorial"), "Response should contain a factorial function")
        assertTrue(content.contains("return"), "Response should contain a return statement")
    }
}