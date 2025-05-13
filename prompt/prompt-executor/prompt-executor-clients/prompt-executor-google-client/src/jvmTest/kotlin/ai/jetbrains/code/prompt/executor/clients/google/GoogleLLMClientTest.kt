package ai.jetbrains.code.prompt.executor.clients.google

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.llm.LLMCapability
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Disabled
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reads the Gemini API key from environment variables.
 * This is required for live tests against the actual API.
 */
fun readTestGeminiKeyFromEnv(): String = System.getenv("GEMINI_API_TEST_KEY")
    ?: error("ERROR: environment variable GEMINI_API_TEST_KEY not set")

/**
 * Tests for the GoogleAI API client.
 */
class GoogleLLMClientTest {
    private val apiKey: String get() = readTestGeminiKeyFromEnv()
    private val model = GoogleModels.Gemini2_0Flash

    private lateinit var client: GoogleLLMClient

    @Disabled("TODO: pass the `GEMINI_API_TEST_KEY`")
    @Test
    @BeforeTest
    fun testCreateClient() {
        client = GoogleLLMClient(apiKey)
        assertNotNull(client, "Client should be created successfully")
    }

    /**
     * Tests a simple prompt execution with different models.
     */
    @Disabled("TODO: pass the `GEMINI_API_TEST_KEY`")
    @Test
    fun testExecuteSimplePrompt() = runTest {
        val client = GoogleLLMClient(apiKey)

        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = client.execute(prompt, model)

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")
        assertTrue(
            (response.first() as Message.Assistant).content.contains("Paris", ignoreCase = true),
            "Response should contain 'Paris'"
        )
    }


    /**
     * Tests streaming response functionality.
     */
    @Disabled("TODO: pass the `GEMINI_API_TEST_KEY`")
    @Test
    fun testExecuteStreamingPrompt() = runTest {
        val client = GoogleLLMClient(apiKey)

        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }

        val responseChunks = client.executeStreaming(prompt, model).toList()

        assertNotNull(responseChunks, "Response chunks should not be null")
        assertTrue(responseChunks.isNotEmpty(), "Response chunks should not be empty")

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

    /**
     * Operation enum for the calculator tool test.
     */
    @Serializable
    enum class CalculatorOperation {
        ADD, SUBTRACT, MULTIPLY, DIVIDE
    }

    /**
     * Tests function calling capabilities with a simple calculator tool.
     */
    @Disabled("TODO: pass the `GEMINI_API_TEST_KEY`")
    @Test
    fun testExecuteWithTools() = runTest {
        if (model.capabilities.contains(LLMCapability.Tools)) {
            val client = GoogleLLMClient(apiKey)

            val calculatorTool = ToolDescriptor(
                name = "calculator",
                description = "A calculator tool that performs basic arithmetic operations on two integer numbers.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "operation",
                        description = "The arithmetic operation to perform (ADD, SUBTRACT, MULTIPLY, or DIVIDE).",
                        type = ToolParameterType.Enum(CalculatorOperation.entries.map { it.name }.toTypedArray())
                    ),
                    ToolParameterDescriptor(
                        name = "a",
                        description = "The first integer argument for the calculation.",
                        type = ToolParameterType.Integer
                    ),
                    ToolParameterDescriptor(
                        name = "b",
                        description = "The second integer argument for the calculation.",
                        type = ToolParameterType.Integer
                    )
                )
            )

            val prompt = Prompt.build("test-tools") {
                system("You are a helpful assistant with access to a calculator tool. When asked to perform calculations, use the calculator tool instead of calculating the answer yourself.")
                user("What is 123 + 456?")
            }

            val response = client.execute(prompt, model, listOf(calculatorTool))

            assertNotNull(response, "Response should not be null")
            assertTrue(response.isNotEmpty(), "Response should not be empty")

            if (response.first() is Message.Tool.Call) {
                val toolCall = response.first() as Message.Tool.Call
                assertEquals("calculator", toolCall.tool, "Tool name should be 'calculator'")
                assertTrue(
                    toolCall.content.contains("ADD", ignoreCase = true) ||
                            toolCall.content.contains("add", ignoreCase = true),
                    "Tool call should use 'ADD' operation"
                )
                assertTrue(toolCall.content.contains("123"), "Tool call should include first number")
                assertTrue(toolCall.content.contains("456"), "Tool call should include second number")
            } else {
                assertTrue(
                    (response.first() as Message.Assistant).content.contains("579"),
                    "Response should contain the correct answer '579'"
                )
            }
        }
    }

    /**
     * Tests code generation capabilities.
     */
    @Disabled("TODO: pass the `GEMINI_API_TEST_KEY`")
    @Test
    fun testCodeGeneration() = runTest {
        val client = GoogleLLMClient(apiKey)

        val prompt = Prompt.build("test-code") {
            system("You are a helpful coding assistant.")
            user("Write a simple Kotlin function to calculate the factorial of a number.")
        }

        val response = client.execute(prompt, model)

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")

        val content = (response.first() as Message.Assistant).content
        assertTrue(content.contains("fun factorial"), "Response should contain a factorial function")
        assertTrue(content.contains("return"), "Response should contain a return statement")
    }
}