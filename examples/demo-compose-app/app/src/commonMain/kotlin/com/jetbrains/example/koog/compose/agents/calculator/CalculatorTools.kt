package com.jetbrains.example.koog.compose.agents.calculator

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

object CalculatorTools {
    abstract class CalculatorTool(
        override val name: String,
        override val description: String,
    ) : Tool<CalculatorTool.Args, CalculatorTool.Result>() {
        @Serializable
        data class Args(
            @property:LLMDescription("First number")
            val a: Float,
            @property:LLMDescription("Second number")
            val b: Float
        )

        @Serializable
        class Result(val result: Float)

        final override val argsSerializer = Args.serializer()
        final override val resultSerializer = Result.serializer()
    }

    /**
     * 2. Implement the tool (tools).
     */

    object PlusTool : CalculatorTool(
        name = "plus",
        description = "Adds a and b",
    ) {
        override suspend fun execute(args: Args): Result {
            return Result(args.a + args.b)
        }
    }

    object MinusTool : CalculatorTool(
        name = "minus",
        description = "Subtracts b from a",
    ) {
        override suspend fun execute(args: Args): Result {
            return Result(args.a - args.b)
        }
    }

    object DivideTool : CalculatorTool(
        name = "divide",
        description = "Divides a and b",
    ) {
        override suspend fun execute(args: Args): Result {
            return Result(args.a / args.b)
        }
    }

    object MultiplyTool : CalculatorTool(
        name = "multiply",
        description = "Multiplies a and b",
    ) {
        override suspend fun execute(args: Args): Result {
            return Result(args.a * args.b)
        }
    }
}
