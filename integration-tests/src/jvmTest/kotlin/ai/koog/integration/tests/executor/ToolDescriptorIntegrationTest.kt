package ai.koog.integration.tests.executor

import ai.koog.agents.core.tools.Tool
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ToolDescriptorIntegrationTest {

    enum class ToolName(val value: String, val displayName: String, val testUserMessage: String) {
        INT_TO_STRING(
            "int_to_string",
            "Tool<Int, String>",
            "Convert the number 42 to its string representation using the tool."
        ),
        STRING_TO_INT("string_to_int", "Tool<String, Int>", "Get the length of the string 'hello' using the tool."),
        INT_TO_INT("int_to_int", "Tool<Int, Int>", "Double the number 21 using the tool."),
        STRING_TO_STRING(
            "string_to_string",
            "Tool<String, String>",
            "Convert 'hello world' to uppercase using the tool."
        ),
        BOOLEAN_TO_STRING(
            "boolean_to_string",
            "Tool<Boolean, String>",
            "Convert the boolean value true to its string representation using the tool."
        ),
        STRING_TO_BOOLEAN(
            "string_to_boolean",
            "Tool<String, Boolean>",
            "Convert the string 'true' to a boolean using the tool."
        ),
        DOUBLE_TO_INT(
            "double_to_int",
            "Tool<Double, Int>",
            "Convert the double value 3.7 to an integer using the tool."
        ),
        INT_TO_DOUBLE("int_to_double", "Tool<Int, Double>", "Convert the integer value 42 to a double using the tool."),
        LONG_TO_DOUBLE(
            "long_to_double",
            "Tool<Long, Double>",
            "Convert the long value 100 to a double with decimal places using the tool."
        ),
        DOUBLE_TO_LONG(
            "double_to_long",
            "Tool<Double, Long>",
            "Convert the double value 15.8 to a long using the tool."
        ),
        FLOAT_TO_BOOLEAN(
            "float_to_boolean",
            "Tool<Float, Boolean>",
            "Convert the float value 2.5 to a boolean using the tool."
        ),
        BOOLEAN_TO_FLOAT(
            "boolean_to_float",
            "Tool<Boolean, Float>",
            "Convert the boolean value true to a float using the tool."
        ),
        LONG_TO_INT("long_to_int", "Tool<Long, Int>", "Convert the long value 12345 to an integer using the tool."),
        INT_TO_LONG("int_to_long", "Tool<Int, Long>", "Convert the integer value 789 to a long using the tool."),
        FLOAT_TO_STRING(
            "float_to_string",
            "Tool<Float, String>",
            "Convert the float value 3.14 to its string representation using the tool."
        ),
        STRING_TO_FLOAT(
            "string_to_float",
            "Tool<String, Float>",
            "Convert the string 'hello' to a float based on its length using the tool."
        ),
        DOUBLE_TO_STRING(
            "double_to_string",
            "Tool<Double, String>",
            "Convert the double value 2.718 to its string representation using the tool."
        ),
        STRING_TO_DOUBLE(
            "string_to_double",
            "Tool<String, Double>",
            "Convert the string 'world' to a double based on its length using the tool."
        );

        override fun toString(): String = displayName
    }

    companion object {
        @JvmStatic
        fun allModels(): Stream<LLModel> {
            return Stream.of(
                OpenAIModels.CostOptimized.GPT4_1Mini,
                AnthropicModels.Sonnet_3_7,
                GoogleModels.Gemini2_5Flash,
                BedrockModels.AnthropicClaude35Haiku,
                OpenRouterModels.Mistral7B,
            )
        }

        @JvmStatic
        fun primitiveToolAndModelCombinations(): Stream<Arguments> {
            val primitiveTools = listOf(
                IntToStringTool(),
                StringToIntTool(),
                IntToIntTool(),
                StringToStringTool(),
                BooleanToStringTool(),
                StringToBooleanTool(),
                DoubleToIntTool(),
                IntToDoubleTool(),
                LongToDoubleTool(),
                DoubleToLongTool(),
                FloatToBooleanTool(),
                BooleanToFloatTool(),
                LongToIntTool(),
                IntToLongTool(),
                FloatToStringTool(),
                StringToFloatTool(),
                DoubleToStringTool(),
                StringToDoubleTool()
            )

            return allModels().flatMap { model ->
                primitiveTools.map { tool ->
                    Arguments.arguments(tool, model)
                }.stream()
            }
        }
    }

    abstract class TestTool<T, R> : Tool<T, R>() {
        abstract val toolName: ToolName
        override val name: String get() = toolName.value
        override fun toString(): String = toolName.displayName
    }

    class IntToStringTool : TestTool<Int, String>() {
        override val toolName = ToolName.INT_TO_STRING
        override val argsSerializer: KSerializer<Int> = Int.serializer()
        override val resultSerializer: KSerializer<String> = String.serializer()
        override val description: String = "Converts an integer to its string representation"

        override suspend fun execute(args: Int): String = "Number: $args"
    }

    class StringToIntTool : TestTool<String, Int>() {
        override val toolName = ToolName.STRING_TO_INT
        override val argsSerializer: KSerializer<String> = String.serializer()
        override val resultSerializer: KSerializer<Int> = Int.serializer()
        override val description: String = "Converts a string to an integer"

        override suspend fun execute(args: String): Int = args.length
    }

    class IntToIntTool : TestTool<Int, Int>() {
        override val toolName = ToolName.INT_TO_INT
        override val argsSerializer: KSerializer<Int> = Int.serializer()
        override val resultSerializer: KSerializer<Int> = Int.serializer()
        override val description: String = "Doubles an integer value"

        override suspend fun execute(args: Int): Int = args * 2
    }

    class StringToStringTool : TestTool<String, String>() {
        override val toolName = ToolName.STRING_TO_STRING
        override val argsSerializer: KSerializer<String> = String.serializer()
        override val resultSerializer: KSerializer<String> = String.serializer()
        override val description: String = "Converts string to uppercase"

        override suspend fun execute(args: String): String = args.uppercase()
    }

    class BooleanToStringTool : TestTool<Boolean, String>() {
        override val toolName = ToolName.BOOLEAN_TO_STRING
        override val argsSerializer: KSerializer<Boolean> = Boolean.serializer()
        override val resultSerializer: KSerializer<String> = String.serializer()
        override val description: String = "Converts boolean to descriptive string"

        override suspend fun execute(args: Boolean): String = if (args) "TRUE_VALUE" else "FALSE_VALUE"
    }

    class DoubleToIntTool : TestTool<Double, Int>() {
        override val toolName = ToolName.DOUBLE_TO_INT
        override val argsSerializer: KSerializer<Double> = Double.serializer()
        override val resultSerializer: KSerializer<Int> = Int.serializer()
        override val description: String = "Converts double to integer by rounding"

        override suspend fun execute(args: Double): Int = args.toInt()
    }

    class LongToDoubleTool : TestTool<Long, Double>() {
        override val toolName = ToolName.LONG_TO_DOUBLE
        override val argsSerializer: KSerializer<Long> = Long.serializer()
        override val resultSerializer: KSerializer<Double> = Double.serializer()
        override val description: String = "Converts long to double with decimal places"

        override suspend fun execute(args: Long): Double = args + 0.5
    }

    class FloatToBooleanTool : TestTool<Float, Boolean>() {
        override val toolName = ToolName.FLOAT_TO_BOOLEAN
        override val argsSerializer: KSerializer<Float> = Float.serializer()
        override val resultSerializer: KSerializer<Boolean> = Boolean.serializer()
        override val description: String = "Converts float to boolean (positive = true)"

        override suspend fun execute(args: Float): Boolean = args > 0f
    }

    class StringToBooleanTool : TestTool<String, Boolean>() {
        override val toolName = ToolName.STRING_TO_BOOLEAN
        override val argsSerializer: KSerializer<String> = String.serializer()
        override val resultSerializer: KSerializer<Boolean> = Boolean.serializer()
        override val description: String = "Converts string to boolean ('true' = true, others = false)"

        override suspend fun execute(args: String): Boolean = args.equals("true", ignoreCase = true)
    }

    class IntToDoubleTool : TestTool<Int, Double>() {
        override val toolName = ToolName.INT_TO_DOUBLE
        override val argsSerializer: KSerializer<Int> = Int.serializer()
        override val resultSerializer: KSerializer<Double> = Double.serializer()
        override val description: String = "Converts integer to double"

        override suspend fun execute(args: Int): Double = args.toDouble()
    }

    class DoubleToLongTool : TestTool<Double, Long>() {
        override val toolName = ToolName.DOUBLE_TO_LONG
        override val argsSerializer: KSerializer<Double> = Double.serializer()
        override val resultSerializer: KSerializer<Long> = Long.serializer()
        override val description: String = "Converts double to long by rounding"

        override suspend fun execute(args: Double): Long = args.toLong()
    }

    class BooleanToFloatTool : TestTool<Boolean, Float>() {
        override val toolName = ToolName.BOOLEAN_TO_FLOAT
        override val argsSerializer: KSerializer<Boolean> = Boolean.serializer()
        override val resultSerializer: KSerializer<Float> = Float.serializer()
        override val description: String = "Converts boolean to float (true = 1.0f, false = 0.0f)"

        override suspend fun execute(args: Boolean): Float = if (args) 1.0f else 0.0f
    }

    class LongToIntTool : TestTool<Long, Int>() {
        override val toolName = ToolName.LONG_TO_INT
        override val argsSerializer: KSerializer<Long> = Long.serializer()
        override val resultSerializer: KSerializer<Int> = Int.serializer()
        override val description: String = "Converts long to integer"

        override suspend fun execute(args: Long): Int = args.toInt()
    }

    class IntToLongTool : TestTool<Int, Long>() {
        override val toolName = ToolName.INT_TO_LONG
        override val argsSerializer: KSerializer<Int> = Int.serializer()
        override val resultSerializer: KSerializer<Long> = Long.serializer()
        override val description: String = "Converts integer to long"

        override suspend fun execute(args: Int): Long = args.toLong()
    }

    class FloatToStringTool : TestTool<Float, String>() {
        override val toolName = ToolName.FLOAT_TO_STRING
        override val argsSerializer: KSerializer<Float> = Float.serializer()
        override val resultSerializer: KSerializer<String> = String.serializer()
        override val description: String = "Converts float to string"

        override suspend fun execute(args: Float): String = "Float: $args"
    }

    class StringToFloatTool : TestTool<String, Float>() {
        override val toolName = ToolName.STRING_TO_FLOAT
        override val argsSerializer: KSerializer<String> = String.serializer()
        override val resultSerializer: KSerializer<Float> = Float.serializer()
        override val description: String = "Converts string length to float"

        override suspend fun execute(args: String): Float = args.length.toFloat()
    }

    class DoubleToStringTool : TestTool<Double, String>() {
        override val toolName = ToolName.DOUBLE_TO_STRING
        override val argsSerializer: KSerializer<Double> = Double.serializer()
        override val resultSerializer: KSerializer<String> = String.serializer()
        override val description: String = "Converts double to string"

        override suspend fun execute(args: Double): String = "Double: $args"
    }

    class StringToDoubleTool : TestTool<String, Double>() {
        override val toolName = ToolName.STRING_TO_DOUBLE
        override val argsSerializer: KSerializer<String> = String.serializer()
        override val resultSerializer: KSerializer<Double> = Double.serializer()
        override val description: String = "Converts string length to double"

        override suspend fun execute(args: String): Double = args.length.toDouble()
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("primitiveToolAndModelCombinations")
    fun integration_testPrimitiveTools(tool: Tool<*, *>, model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val client = when (model.provider) {
            is LLMProvider.Anthropic -> AnthropicLLMClient(TestUtils.readTestAnthropicKeyFromEnv())
            is LLMProvider.Google -> GoogleLLMClient(TestUtils.readTestGoogleAIKeyFromEnv())
            else -> OpenAILLMClient(TestUtils.readTestOpenAIKeyFromEnv())
        }

        val testTool = tool as TestTool<*, *>
        val prompt = prompt(testTool.toolName.value, params = LLMParams(toolChoice = ToolChoice.Required)) {
            system("You are a helpful assistant with access to tools. ALWAYS use the available tool.")
            user(testTool.toolName.testUserMessage)
        }

        withRetry {
            val response = client.execute(prompt, model, listOf(tool.descriptor))
            assertTrue(response.isNotEmpty(), "Response should not be empty for tool ${tool.name} with model $model")
            val hasToolCall = response.any { message ->
                message is Message.Tool.Call && message.tool == tool.name
            }
            assertTrue(
                hasToolCall,
                "Response should contain a Tool.Call message for tool '${tool.name}' with model $model."
            )
        }
    }
}
