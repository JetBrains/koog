package ai.koog.agents.core

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

object CalculatorTools {

    abstract class CalculatorTool(
        name: String,
        description: String,
    ) : Tool<CalculatorTool.Args, CalculatorTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        name = name,
        description = description
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("First number")
            val a: Float,
            @property:LLMDescription("Second number")
            val b: Float
        )

        @Serializable
        @JvmInline
        value class Result(val result: Float)
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
