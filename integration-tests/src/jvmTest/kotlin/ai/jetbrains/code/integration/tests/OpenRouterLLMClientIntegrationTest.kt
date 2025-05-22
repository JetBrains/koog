package ai.jetbrains.code.integration.tests

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

fun readTestOpenRouterKeyFromEnv(): String {
    return System.getenv("OPEN_ROUTER_API_TEST_KEY") ?: error("ERROR: environment variable `OPEN_ROUTER_API_TEST_KEY` not set")
}

/**
 * Integration tests for the OpenRouter client.
 * 
 * To run these tests, you need a valid OpenRouter API key set in the OPEN_ROUTER_API_TEST_KEY environment variable.
 * These tests use the free "microsoft/phi-4-reasoning:free" model which is available at no cost.
 */
class OpenRouterLLMClientTest {

    // API key for testing
    private val apiKey: String get() = readTestOpenRouterKeyFromEnv()
    
    // Free model for testing
    private val testModel = OpenRouterModels.Phi4Reasoning

    @Test
    @Ignore("This test is ignored because it requires a valid API key.")
    fun testCreateClient() {
        val client = OpenRouterLLMClient(apiKey)
        assertNotNull(client, "Client should be created successfully")
    }

    @Test
    @Ignore("This test is ignored because it requires a valid API key.")
    fun testExecuteSimplePrompt() = runTest {
        val client = OpenRouterLLMClient(apiKey)

        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = client.execute(prompt, testModel)

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")
        assertTrue(
            (response.first() as Message.Assistant).content.lowercase().contains("paris"),
            "Response should contain 'Paris'"
        )
    }

    @Test
    @Ignore("This test is ignored because it requires a valid API key.")
    fun testExecuteStreamingPrompt() = runTest {
        val client = OpenRouterLLMClient(apiKey)

        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }

        val responseChunks = client.executeStreaming(prompt, testModel).toList()

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
    @Ignore("This test is ignored because it requires a valid API key.")
    fun testExecuteWithTools() = runTest {
        val client = OpenRouterLLMClient(apiKey)

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

        val response = client.execute(prompt, testModel, listOf(calculatorTool))

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")

        // The response might be either a direct answer or a tool call
        if (response.first() is Message.Tool.Call) {
            val toolCall = response.first() as Message.Tool.Call
            assertEquals("calculator", toolCall.tool, "Tool name should be 'calculator'")
            assertTrue(toolCall.content.contains("ADD"), "Tool call should use 'ADD' operation")
            assertTrue(toolCall.content.contains("123"), "Tool call should include first number")
            assertTrue(toolCall.content.contains("456"), "Tool call should include second number")
        } else {
            val assistantMessage = response.first() as Message.Assistant
            assertTrue(
                assistantMessage.content.contains("579"),
                "Response should contain the correct answer '579' but was '${assistantMessage.content}'"
            )
        }
    }

    @Test
    @Ignore("This test is ignored because it requires a valid API key.")
    fun testCodeGeneration() = runTest {
        val client = OpenRouterLLMClient(apiKey)

        val prompt = Prompt.build("test-code") {
            system("You are a helpful coding assistant.")
            user("Write a simple Kotlin function to calculate the factorial of a number.")
        }

        val response = client.execute(prompt, testModel)

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")

        val content = (response.first() as Message.Assistant).content
        assertTrue(content.contains("fun factorial"), "Response should contain a factorial function")
        assertTrue(content.contains("return"), "Response should contain a return statement")
    }
}