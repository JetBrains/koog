package ai.koog.integration.tests.executor

import ai.koog.agents.core.tools.Tool
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import io.kotest.inspectors.shouldForAny
import io.kotest.matchers.collections.shouldNotBeEmpty
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
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
                OpenAIModels.Chat.GPT4_1Mini,
                AnthropicModels.Sonnet_4_5,
                GoogleModels.Gemini2_5Flash,
                BedrockModels.AnthropicClaude4_5Haiku,
                OpenRouterModels.Mistral7B,
                MistralAIModels.Chat.MistralLarge21,
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

    abstract class TestTool<T, R>(
        argsSerializer: KSerializer<T>,
        resultSerializer: KSerializer<R>,
        val toolName: ToolName,
    ) : Tool<T, R>(
        argsSerializer = argsSerializer,
        resultSerializer = resultSerializer,
        name = toolName.value,
        description = toolName.value
    ) {
        override fun toString(): String = toolName.displayName
    }

    class IntToStringTool : TestTool<Int, String>(
        argsSerializer = Int.serializer(),
        resultSerializer = String.serializer(),
        toolName = ToolName.INT_TO_STRING,
    ) {
        override suspend fun execute(args: Int): String = "Number: $args"
    }

    class StringToIntTool : TestTool<String, Int>(
        argsSerializer = String.serializer(),
        resultSerializer = Int.serializer(),
        toolName = ToolName.STRING_TO_INT,
    ) {
        override suspend fun execute(args: String): Int = args.length
    }

    class IntToIntTool : TestTool<Int, Int>(
        argsSerializer = Int.serializer(),
        resultSerializer = Int.serializer(),
        toolName = ToolName.INT_TO_INT,
    ) {
        override suspend fun execute(args: Int): Int = args * 2
    }

    class StringToStringTool : TestTool<String, String>(
        argsSerializer = String.serializer(),
        resultSerializer = String.serializer(),
        toolName = ToolName.STRING_TO_STRING,
    ) {
        override suspend fun execute(args: String): String = args.uppercase()
    }

    class BooleanToStringTool : TestTool<Boolean, String>(
        argsSerializer = Boolean.serializer(),
        resultSerializer = String.serializer(),
        toolName = ToolName.BOOLEAN_TO_STRING,
    ) {
        override suspend fun execute(args: Boolean): String = if (args) "TRUE_VALUE" else "FALSE_VALUE"
    }

    class DoubleToIntTool : TestTool<Double, Int>(
        argsSerializer = Double.serializer(),
        resultSerializer = Int.serializer(),
        toolName = ToolName.DOUBLE_TO_INT,
    ) {
        override suspend fun execute(args: Double): Int = args.toInt()
    }

    class LongToDoubleTool : TestTool<Long, Double>(
        argsSerializer = Long.serializer(),
        resultSerializer = Double.serializer(),
        toolName = ToolName.LONG_TO_DOUBLE,
    ) {
        override suspend fun execute(args: Long): Double = args + 0.5
    }

    class FloatToBooleanTool : TestTool<Float, Boolean>(
        argsSerializer = Float.serializer(),
        resultSerializer = Boolean.serializer(),
        toolName = ToolName.FLOAT_TO_BOOLEAN,
    ) {
        override suspend fun execute(args: Float): Boolean = args > 0f
    }

    class StringToBooleanTool : TestTool<String, Boolean>(
        argsSerializer = String.serializer(),
        resultSerializer = Boolean.serializer(),
        toolName = ToolName.STRING_TO_BOOLEAN,
    ) {
        override suspend fun execute(args: String): Boolean = args.equals("true", ignoreCase = true)
    }

    class IntToDoubleTool : TestTool<Int, Double>(
        argsSerializer = Int.serializer(),
        resultSerializer = Double.serializer(),
        toolName = ToolName.INT_TO_DOUBLE,
    ) {
        override suspend fun execute(args: Int): Double = args.toDouble()
    }

    class DoubleToLongTool : TestTool<Double, Long>(
        argsSerializer = Double.serializer(),
        resultSerializer = Long.serializer(),
        toolName = ToolName.DOUBLE_TO_LONG,
    ) {
        override suspend fun execute(args: Double): Long = args.toLong()
    }

    class BooleanToFloatTool : TestTool<Boolean, Float>(
        argsSerializer = Boolean.serializer(),
        resultSerializer = Float.serializer(),
        toolName = ToolName.BOOLEAN_TO_FLOAT,
    ) {
        override suspend fun execute(args: Boolean): Float = if (args) 1.0f else 0.0f
    }

    class LongToIntTool : TestTool<Long, Int>(
        argsSerializer = Long.serializer(),
        resultSerializer = Int.serializer(),
        toolName = ToolName.LONG_TO_INT,
    ) {
        override suspend fun execute(args: Long): Int = args.toInt()
    }

    class IntToLongTool : TestTool<Int, Long>(
        argsSerializer = Int.serializer(),
        resultSerializer = Long.serializer(),
        toolName = ToolName.INT_TO_LONG,
    ) {
        override suspend fun execute(args: Int): Long = args.toLong()
    }

    class FloatToStringTool : TestTool<Float, String>(
        argsSerializer = Float.serializer(),
        resultSerializer = String.serializer(),
        toolName = ToolName.FLOAT_TO_STRING,
    ) {
        override suspend fun execute(args: Float): String = "Float: $args"
    }

    class StringToFloatTool : TestTool<String, Float>(
        argsSerializer = String.serializer(),
        resultSerializer = Float.serializer(),
        toolName = ToolName.STRING_TO_FLOAT,
    ) {
        override suspend fun execute(args: String): Float = args.length.toFloat()
    }

    class DoubleToStringTool : TestTool<Double, String>(
        argsSerializer = Double.serializer(),
        resultSerializer = String.serializer(),
        toolName = ToolName.DOUBLE_TO_STRING,
    ) {
        override suspend fun execute(args: Double): String = "Double: $args"
    }

    class StringToDoubleTool : TestTool<String, Double>(
        argsSerializer = String.serializer(),
        resultSerializer = Double.serializer(),
        toolName = ToolName.STRING_TO_DOUBLE,
    ) {
        override suspend fun execute(args: String): Double = args.length.toDouble()
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("primitiveToolAndModelCombinations")
    fun integration_testPrimitiveTools(tool: Tool<*, *>, model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(
            model.capabilities.containsAll(listOf(LLMCapability.Tools, LLMCapability.ToolChoice)),
            "Model $model does not support tools and tool choice"
        )

        val client = getLLMClientForProvider(model.provider)

        with(tool as TestTool<*, *>) {
            withRetry {
                client.execute(
                    prompt(toolName.value, params = LLMParams(toolChoice = ToolChoice.Required)) {
                        system("You are a helpful assistant with access to tools. ALWAYS use the available tool.")
                        user(toolName.testUserMessage)
                    },
                    model,
                    listOf(descriptor)
                )
                    .shouldNotBeEmpty()
                    .shouldForAny { it is Message.Tool.Call && it.tool == name }
            }
        }
    }
}
