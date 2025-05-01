package ai.grazie.code.agents.example.calculator

import ai.grazie.code.agents.core.tools.*
import kotlinx.serialization.Serializable

object CalculatorTools {
    /**
     * 1. Define the tool
     *
     * **IMPORTANT**: Defining tools in your own code is good for fast experiments only,
     * but for production purposes this method is not recommended.
     * Please contribute to [ai.grazie.code.agents.tools.registry.GlobalAgentToolStages]
     * [here](https://github.com/JetBrains/code-engine/tree/main/code-agents/code-agents-tools-registry)
     **/
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

    fun ToolStage.Builder.tools() {
        tool(PlusTool)
        tool(MinusTool)
        tool(DivideTool)
        tool(MultiplyTool)
    }

}