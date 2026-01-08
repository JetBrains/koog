package ai.koog.prompt.executor.litertlm.client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LiteRTLMModels
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File

/**
 * End-to-end chat completion test using LiteRT-LM provider and executor.
 *
 * This test demonstrates the complete flow:
 * 1. Create a LiteRT-LM client with tool support
 * 2. Send a prompt with available tools
 * 3. Model calls tools via the bridge
 * 4. Tool executor handles the calls
 * 5. Responses are streamed back
 *
 * ## Running this test
 *
 * ```bash
 * LITERTLM_MODEL_PATH=/path/to/gemma-3n-e4b.litertlm \
 *   ./gradlew :prompt:prompt-executor:prompt-executor-clients:prompt-executor-litertlm-client:jvmTest \
 *   --tests "*.LiteRTLMChatCompletionTest"
 * ```
 */
@Disabled("Requires LiteRT-LM library and model file. Set LITERTLM_MODEL_PATH env var.")
class LiteRTLMChatCompletionTest {

    private val modelPath: String by lazy {
        System.getenv("LITERTLM_MODEL_PATH")
            ?: throw IllegalStateException("LITERTLM_MODEL_PATH environment variable not set")
    }

    private var client: LiteRTLMClient? = null

    // Mock tool implementations
    private val weatherData = mapOf(
        "paris" to """{"city": "Paris", "temperature": 22, "condition": "sunny"}""",
        "london" to """{"city": "London", "temperature": 15, "condition": "cloudy"}""",
        "tokyo" to """{"city": "Tokyo", "temperature": 28, "condition": "humid"}"""
    )

    private val calculatorResults = mutableListOf<String>()

    // Tool descriptors
    private val weatherTool = ToolDescriptor(
        name = "get_weather",
        description = "Get the current weather for a city. Returns temperature and conditions.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "city",
                description = "The city name (e.g., Paris, London, Tokyo)",
                type = ToolParameterType.String
            )
        )
    )

    private val calculatorTool = ToolDescriptor(
        name = "calculate",
        description = "Evaluate a mathematical expression and return the result.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "expression",
                description = "The math expression to evaluate (e.g., '2 + 2', '10 * 5')",
                type = ToolParameterType.String
            )
        )
    )

    // Tool executor that handles tool calls
    private val toolExecutor = ToolExecutor { toolName, args ->
        when (toolName) {
            "get_weather" -> {
                val city = (args["city"] as? String)?.lowercase() ?: "unknown"
                weatherData[city] ?: """{"error": "City not found: $city"}"""
            }
            "calculate" -> {
                val expression = args["expression"] as? String ?: ""
                try {
                    // Simple evaluation for basic operations
                    val result = evaluateSimpleExpression(expression)
                    calculatorResults.add("$expression = $result")
                    """{"expression": "$expression", "result": $result}"""
                } catch (e: Exception) {
                    """{"error": "Cannot evaluate: $expression"}"""
                }
            }
            else -> """{"error": "Unknown tool: $toolName"}"""
        }
    }

    @BeforeEach
    fun setup() {
        require(File(modelPath).exists()) { "Model file not found: $modelPath" }
        LiteRTLMClient.setLogSeverity(LiteRTLMLogSeverity.ERROR)
        calculatorResults.clear()
    }

    @AfterEach
    fun teardown() {
        client?.close()
        client = null
    }

    @Test
    fun `chat completion without tools`() = runTest {
        val config = LiteRTLMClientConfig(modelPath = modelPath)
        client = LiteRTLMClient.create(config)

        val chatPrompt = prompt {
            system("You are a helpful assistant. Be concise.")
            user("What is the capital of France?")
        }

        val responses = client!!.execute(chatPrompt, LiteRTLMModels.Google.GEMMA_3N_E4B)

        assert(responses.isNotEmpty()) { "Expected a response" }
        val answer = responses.first().content
        println("Answer: $answer")
        assert(answer.contains("Paris", ignoreCase = true)) {
            "Expected answer to mention Paris"
        }
    }

    @Test
    fun `chat completion with streaming`() = runTest {
        val config = LiteRTLMClientConfig(modelPath = modelPath)
        client = LiteRTLMClient.create(config)

        val chatPrompt = prompt {
            system("You are a helpful assistant.")
            user("Count from 1 to 5, one number per line.")
        }

        val chunks = mutableListOf<String>()
        val frames = client!!.executeStreaming(chatPrompt, LiteRTLMModels.Google.GEMMA_3N_E4B)
            .toList()

        frames.forEach { frame ->
            // Collect streaming content
            println(frame)
        }

        assert(frames.isNotEmpty()) { "Expected streaming frames" }
    }

    @Test
    fun `chat completion with tool calling - weather`() = runTest {
        val config = LiteRTLMClientConfig(modelPath = modelPath)
        client = LiteRTLMClient.create(config, toolExecutor)

        val chatPrompt = prompt {
            system("""
                You are a helpful assistant with access to tools.
                When asked about weather, use the get_weather tool.
                Respond naturally after getting the tool result.
            """.trimIndent())
            user("What's the weather like in Paris right now?")
        }

        val responses = client!!.execute(
            chatPrompt,
            LiteRTLMModels.Google.GEMMA_3N_E4B,
            tools = listOf(weatherTool)
        )

        assert(responses.isNotEmpty()) { "Expected a response" }
        val answer = responses.first().content
        println("Weather response: $answer")

        // The model should either call the tool and respond, or mention weather
        assert(answer.isNotBlank()) { "Expected non-empty response" }
    }

    @Test
    fun `chat completion with tool calling - calculator`() = runTest {
        val config = LiteRTLMClientConfig(modelPath = modelPath)
        client = LiteRTLMClient.create(config, toolExecutor)

        val chatPrompt = prompt {
            system("""
                You are a helpful assistant with access to a calculator tool.
                When asked to do math, use the calculate tool.
            """.trimIndent())
            user("What is 42 multiplied by 7?")
        }

        val responses = client!!.execute(
            chatPrompt,
            LiteRTLMModels.Google.GEMMA_3N_E4B,
            tools = listOf(calculatorTool)
        )

        assert(responses.isNotEmpty()) { "Expected a response" }
        val answer = responses.first().content
        println("Calculator response: $answer")
        println("Calculator was called with: $calculatorResults")
    }

    @Test
    fun `chat completion with multiple tools available`() = runTest {
        val config = LiteRTLMClientConfig(modelPath = modelPath)
        client = LiteRTLMClient.create(config, toolExecutor)

        val chatPrompt = prompt {
            system("""
                You are a helpful assistant with access to tools:
                - get_weather: Get current weather for a city
                - calculate: Do math calculations

                Use the appropriate tool when needed.
            """.trimIndent())
            user("What's the temperature in London, and what's 15 + 7?")
        }

        val responses = client!!.execute(
            chatPrompt,
            LiteRTLMModels.Google.GEMMA_3N_E4B,
            tools = listOf(weatherTool, calculatorTool)
        )

        assert(responses.isNotEmpty()) { "Expected a response" }
        println("Multi-tool response: ${responses.first().content}")
    }

    @Test
    fun `multi-turn conversation with tools`() = runTest {
        val config = LiteRTLMClientConfig(modelPath = modelPath)
        client = LiteRTLMClient.create(config, toolExecutor)

        client!!.conversation(
            systemPrompt = "You are a helpful weather assistant. Use tools when asked about weather."
        ).use { conv ->
            // First turn
            val r1 = conv.send("Hi! Can you check the weather in Tokyo?")
            println("Turn 1: ${r1.content}")

            // Second turn - follow-up
            val r2 = conv.send("Is it hotter there than in London?")
            println("Turn 2: ${r2.content}")

            // Third turn - unrelated
            val r3 = conv.send("Thanks! What's your favorite color?")
            println("Turn 3: ${r3.content}")

            assert(r1.content.isNotBlank())
            assert(r2.content.isNotBlank())
            assert(r3.content.isNotBlank())
        }
    }

    @Test
    fun `streaming chat with managed conversation`() = runTest {
        val config = LiteRTLMClientConfig(modelPath = modelPath)
        client = LiteRTLMClient.create(config)

        client!!.conversation(systemPrompt = "You are a storyteller.").use { conv ->
            print("Story: ")
            conv.sendStreaming("Tell me a very short story about a robot.")
                .collect { chunk ->
                    print(chunk)
                }
            println()
        }
    }

    // Simple expression evaluator for basic math
    private fun evaluateSimpleExpression(expr: String): Double {
        val cleaned = expr.replace(" ", "")

        // Handle basic operations: +, -, *, /
        return when {
            cleaned.contains("+") -> {
                val parts = cleaned.split("+")
                parts.sumOf { it.toDouble() }
            }
            cleaned.contains("*") -> {
                val parts = cleaned.split("*")
                parts.fold(1.0) { acc, s -> acc * s.toDouble() }
            }
            cleaned.contains("-") && !cleaned.startsWith("-") -> {
                val parts = cleaned.split("-")
                parts.drop(1).fold(parts.first().toDouble()) { acc, s -> acc - s.toDouble() }
            }
            cleaned.contains("/") -> {
                val parts = cleaned.split("/")
                parts.drop(1).fold(parts.first().toDouble()) { acc, s -> acc / s.toDouble() }
            }
            else -> cleaned.toDouble()
        }
    }
}
