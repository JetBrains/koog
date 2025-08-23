package ai.koog.agents.core

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.jvm.JvmInline

object CalculatorTools {

    abstract class CalculatorTool(
        name: String,
        description: String,
    ) : Tool<CalculatorTool.Args, CalculatorTool.Result>() {
        @Serializable
        data class Args(val a: Float, val b: Float)

        @Serializable
        @JvmInline
        value class Result(val result: Float)

        final override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        final override val descriptor = ToolDescriptor(
            name = name,
            description = description,
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "a",
                    description = "First number",
                    type = ToolParameterType.Float,
                ),
                ToolParameterDescriptor(
                    name = "b",
                    description = "Second number",
                    type = ToolParameterType.Float,
                ),
            )
        )
    }

    object PlusTool : CalculatorTool(
        name = "plus",
        description = "Adds a and b",
    ) {
        override suspend fun execute(args: Args): Result {
            return Result(args.a + args.b)
        }
    }
}
