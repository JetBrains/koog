package ai.jetbrains.code.prompt.executor.clients.anthropic

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

fun readTestAnthropicKeyFromEnv(): String {
    return System.getenv("ANTHROPIC_API_TEST_KEY") ?: error("ERROR: environment variable ANTHROPIC_API_TEST_KEY not set")
}

class AnthropicSuspendableDirectClientTest {

    // API key for testing
    private val apiKey: String get() = readTestAnthropicKeyFromEnv()

    @Test
    fun testCreateClient() {
        // TODO: pass the `ANTHROPIC_API_TEST_KEY`
        return

        val client = AnthropicDirectLLMClient(apiKey)
        assertNotNull(client, "Client should be created successfully")
    }

    @Test
    fun testExecuteSimplePrompt() = runTest {
        // TODO: pass the `ANTHROPIC_API_TEST_KEY`
        return@runTest

        val client = AnthropicDirectLLMClient(apiKey)

        val prompt = Prompt.build(AnthropicModels.Sonnet_3_7, "test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = client.execute(prompt)

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")
        assertTrue(
            (response.first() as Message.Assistant).content.contains("Paris", ignoreCase = true),
            "Response should contain 'Paris'"
        )
    }

    @Test
    fun testExecuteStreamingPrompt() = runTest {
        // TODO: pass the `ANTHROPIC_API_TEST_KEY`
        return@runTest

        val client = AnthropicDirectLLMClient(apiKey)

        val prompt = Prompt.build(AnthropicModels.Sonnet_3_7, "test-streaming") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }

        val responseChunks = client.executeStreaming(prompt).toList()

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

    @Test
    fun testExecuteWithTools() = runTest {
        // TODO: pass the `ANTHROPIC_API_TEST_KEY`
        return@runTest

        val client = AnthropicDirectLLMClient(apiKey)

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

        val prompt = Prompt.build(AnthropicModels.Sonnet_3_7, "test-tools") {
            system("You are a helpful assistant with access to a calculator tool.")
            user("What is 123 + 456?")
        }

        val response = client.execute(prompt, listOf(calculatorTool))

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

    @Test
    fun testCodeGeneration() = runTest {
        // TODO: pass the `ANTHROPIC_API_TEST_KEY`
        return@runTest

        val client = AnthropicDirectLLMClient(apiKey)

        val prompt = Prompt.build(AnthropicModels.Sonnet_3_5, "test-code") {
            system("You are a helpful coding assistant.")
            user("Write a simple Kotlin function to calculate the factorial of a number.")
        }

        val response = client.execute(prompt)

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")

        val content = (response.first() as Message.Assistant).content
        assertTrue(content.contains("fun factorial"), "Response should contain a factorial function")
        assertTrue(content.contains("return"), "Response should contain a return statement")
    }
}