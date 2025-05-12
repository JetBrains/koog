package ai.jetbrains.code.prompt.executor.clients.openai

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.llm.LLMCapability
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

fun readTestOpenAIKeyFromEnv(): String {
    return System.getenv("OPEN_AI_API_TEST_KEY") ?: error("ERROR: environment variable `OPEN_AI_API_TEST_KEY` not set")
}

class OpenAISuspendableDirectClientTest {

    // API key for testing
    private val apiKey: String get() = readTestOpenAIKeyFromEnv()

    // TODO add parametrized test
    val allModels = listOf(
        OpenAIModels.Chat.GPT4o,
        OpenAIModels.Chat.GPT4_1,

        OpenAIModels.Reasoning.O3Mini,
        OpenAIModels.Reasoning.GPT4oMini,
        OpenAIModels.Reasoning.O3,
        OpenAIModels.Reasoning.O1,
        OpenAIModels.Reasoning.O1Mini,

        OpenAIModels.CostOptimized.O4Mini,
        OpenAIModels.CostOptimized.GPT4_1Mini,


        OpenAIModels.Embeddings.TextEmbeddingAda3Large,
        OpenAIModels.Embeddings.TextEmbeddingAda3Small,
        OpenAIModels.Embeddings.TextEmbeddingAda002
    )

    fun testWithAllModels(test: suspend (model: LLModel) -> Unit) {
        allModels.forEach { model ->
            println("Testing with model: $model")
            runTest { test(model) }
        }
    }

    @Disabled("This test requires a valid OpenAI API key")
    @Test
    fun testCreateClient() {
        val client = OpenAILLMClient(apiKey)
        assertNotNull(client, "Client should be created successfully")
    }

    @Disabled("This test requires a valid OpenAI API key")
    @Test
    fun testExecuteSimplePrompt_GPT4o() = testWithAllModels { model ->
        if (model.capabilities.contains(LLMCapability.Completion)) {
            val client = OpenAILLMClient(apiKey)

            val prompt = Prompt.build("test-prompt") {
                system("You are a helpful assistant.")
                user("What is the capital of France?")
            }

            val response = client.execute(prompt, OpenAIModels.Chat.GPT4o)

            assertNotNull(response, "Response should not be null")
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")
            assertTrue(
                (response.first() as Message.Assistant).content.lowercase().contains("paris"),
                "Response should contain 'Paris'"
            )
        }
    }

    @Disabled("This test requires a valid OpenAI API key")
    @Test
    fun testExecuteStreamingPrompt_O3Mini() = testWithAllModels { model ->
        if (model.capabilities.contains(LLMCapability.Completion)) {
            val client = OpenAILLMClient(apiKey)

            val prompt = Prompt.build("test-streaming") {
                system("You are a helpful assistant.")
                user("Count from 1 to 5.")
            }

            val responseChunks = client.executeStreaming(prompt, OpenAIModels.O3Mini).toList()

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
    }

    @Serializable
    enum class CalculatorOperation {
        ADD, SUBTRACT, MULTIPLY, DIVIDE
    }

    @Disabled("This test requires a valid OpenAI API key")
    @Test
    fun testExecuteWithTools_GPT4oMini() = testWithAllModels { model ->
        if (model.capabilities.contains(LLMCapability.Tools)) {
            val client = OpenAILLMClient(apiKey)

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

            val response = client.execute(prompt, OpenAIModels.Chat.GPT4o, listOf(calculatorTool))

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
    }

    @Disabled("This test requires a valid OpenAI API key")
    @Test
    fun testCodeGeneration_GPT4oMini() = testWithAllModels { model ->
        if (model.capabilities.contains(LLMCapability.Completion)) {
            val client = OpenAILLMClient(apiKey)

            val prompt = Prompt.build("test-code") {
                system("You are a helpful coding assistant.")
                user("Write a simple Kotlin function to calculate the factorial of a number.")
            }

            val response = client.execute(prompt, OpenAIModels.Chat.GPT4o)

            assertNotNull(response, "Response should not be null")
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")

            val content = (response.first() as Message.Assistant).content
            assertTrue(content.contains("fun factorial"), "Response should contain a factorial function")
            assertTrue(content.contains("return"), "Response should contain a return statement")
        }
    }
}