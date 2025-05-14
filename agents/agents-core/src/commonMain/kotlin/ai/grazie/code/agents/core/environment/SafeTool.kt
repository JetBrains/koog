@file:Suppress("UNCHECKED_CAST")

package ai.grazie.code.agents.core.environment

import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolResult
import ai.jetbrains.code.prompt.message.Message

public data class SafeTool<TArgs : Tool.Args, TResult : ToolResult>(
    private val tool: Tool<TArgs, TResult>,
    private val environment: AIAgentEnvironment
) {
    public sealed interface Result<TResult : ToolResult> {
        public val content: String

        public fun isSuccessful(): Boolean = this is Success<TResult>
        public fun isFailure(): Boolean = this is Failure<TResult>
        public fun asSuccessful(): Success<TResult> = this as Success<TResult>
        public fun asFailure(): Failure<TResult> = this as Failure<TResult>

        public data class Success<TResult : ToolResult>(val result: TResult, override val content: String) : Result<TResult>
        public data class Failure<TResult : ToolResult>(val message: String) : Result<TResult> {
            override val content: String get() = message
        }
    }

    @Suppress("UNCHECKED_CAST")
    public suspend fun execute(args: TArgs): Result<TResult> {
        return environment.executeTool(
            Message.Tool.Call(
                id = null,
                tool = tool.name,
                content = tool.encodeArgs(args).toString()
            )
        ).toSafeResult()
    }

    public suspend fun executeRaw(args: TArgs): String {
        return environment.executeTool(
            Message.Tool.Call(
                id = null,
                tool = tool.name,
                content = tool.encodeArgs(args).toString()
            )
        ).content
    }

    @Suppress("UNCHECKED_CAST")
    public suspend fun executeUnsafe(args: Tool.Args): Result<TResult> {
        return environment.executeTool(
            Message.Tool.Call(
                id = null,
                tool = tool.name,
                content = tool.encodeArgs(args as TArgs).toString()
            )
        ).toSafeResult()
    }
}

public fun <TResult : ToolResult> ReceivedToolResult.toSafeResult(): SafeTool.Result<TResult> = when (result) {
    null -> SafeTool.Result.Failure(message = content)
    else -> SafeTool.Result.Success(result = result as TResult, content = content)
}
