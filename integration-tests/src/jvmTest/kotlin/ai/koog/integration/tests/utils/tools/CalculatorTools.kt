package ai.koog.integration.tests.utils.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.Prompt
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
enum class CalculatorOperation {
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE
}

@Serializable
data class SimpleCalculatorArgs(
    @property:LLMDescription("The operation to perform.")
    val operation: CalculatorOperation,
    @property:LLMDescription("The first argument (integer)")
    val a: Int,
    @property:LLMDescription("The second argument (integer)")
    val b: Int
)

object CalculatorToolNoArgs : SimpleTool<Unit>(
    argsSerializer = Unit.serializer(),
    name = "calculator",
    description = "A simple calculator that performs basic calculations. No parameters needed."
) {
    override suspend fun execute(args: Unit): String {
        return "The result of 123 + 456 is 579"
    }
}

object SimpleCalculatorTool : SimpleTool<SimpleCalculatorArgs>(
    argsSerializer = SimpleCalculatorArgs.serializer(),
    name = "calculator",
    description = "A simple calculator that can add, subtract, multiply, and divide two integers."
) {
    override suspend fun execute(args: SimpleCalculatorArgs): String {
        return when (args.operation) {
            CalculatorOperation.ADD -> (args.a + args.b).toString()
            CalculatorOperation.SUBTRACT -> (args.a - args.b).toString()
            CalculatorOperation.MULTIPLY -> (args.a * args.b).toString()
            CalculatorOperation.DIVIDE -> {
                if (args.b == 0) {
                    "Error: Division by zero"
                } else {
                    (args.a / args.b).toString()
                }
            }
        }
    }
}

object CalculatorTool : Tool<SimpleCalculatorArgs, Int>(
    argsSerializer = SimpleCalculatorArgs.serializer(),
    resultSerializer = Int.serializer(),
    name = "calculator",
    description = "A simple calculator that can add, subtract, multiply, and divide two integers."
) {
    override suspend fun execute(args: SimpleCalculatorArgs): Int = when (args.operation) {
        CalculatorOperation.ADD -> args.a + args.b
        CalculatorOperation.SUBTRACT -> args.a - args.b
        CalculatorOperation.MULTIPLY -> args.a * args.b
        CalculatorOperation.DIVIDE -> args.a / args.b
    }
}

@Serializable
data class CalculateSumArgs(
    @property:LLMDescription("List of amounts to sum")
    val amounts: List<Double>
)

object CalculateSumTool : SimpleTool<CalculateSumArgs>(
    argsSerializer = CalculateSumArgs.serializer(),
    name = "calculate_sum",
    description = "Calculate the sum of a list of amounts"
) {
    override suspend fun execute(args: CalculateSumArgs): String {
        val sum = args.amounts.sum()
        return sum.toString()
    }
}

val calculatorToolDescriptor = CalculatorTool.descriptor
val calculatorToolDescriptorOptionalParams = CalculatorTool.descriptor.copy(
    optionalParameters = listOf(
        ToolParameterDescriptor(
            name = "comment",
            description = "Comment to the result (string)",
            type = ToolParameterType.String
        )
    )
)

val calculatorPrompt = Prompt.build("test-calculator-tool") {
    system(
        "You are a helpful assistant with access to a calculator tool. " +
            "You MUST call the calculator tool!!!."
    )
    user("What is 123 + 456?")
}

val calculatorPromptNotRequiredOptionalParams = Prompt.build("test-calculator-tool") {
    system(
        "You are a helpful assistant with access to a calculator tool. " +
            "You MUST call the calculator tool!!!." +
            "You MUST AVOID returning optional parameters."
    )
    user("What is 123 + 456?")
}
