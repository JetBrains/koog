package com.jetbrains.example.kotlin_agents_demo_app.agents.calculator

import ai.koog.agents.core.tools.*
import kotlinx.serialization.Serializable

object CalculatorTools {
    abstract class CalculatorTool(
        name: String,
        description: String,
    ) : Tool<CalculatorTool.Args, CalculatorTool.Result>() {
        @Serializable
        data class Args(val a: Float, val b: Float) : Tool.Args

        @Serializable
        @JvmInline
        value class Result(val result: Float) : ToolResult {
            override fun toStringDefault(): String {
                return result.toString()
            }
        }

        final override val argsSerializer = Args.serializer()

        final override val descriptor = ToolDescriptor(
            name = name, description = description, requiredParameters = listOf(
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
