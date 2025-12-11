package ai.koog.prompt.processor

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

object Tools {
    object PlusTool : Tool<PlusTool.Args, PlusTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        name = "plus",
        description = "Adds a and b"
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

        override suspend fun execute(args: Args): Result {
            return Result(args.a + args.b)
        }
    }

    object StringTool : Tool<StringTool.Args, StringTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "string_tool",
            description = "A tool that takes string parameters",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "text1",
                    description = "First string",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "text2",
                    description = "Second string",
                    type = ToolParameterType.String
                )
            )
        )
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("First string")
            val text1: String,
            @property:LLMDescription("Second string")
            val text2: String
        )

        @Serializable
        @JvmInline
        value class Result(val result: String)

        override suspend fun execute(args: Args): Result {
            return Result(args.text1 + " " + args.text2)
        }
    }

    val tools = listOf(PlusTool, StringTool)
    val toolRegistry = ToolRegistry {
        tool(PlusTool)
        tool(StringTool)
    }
}
