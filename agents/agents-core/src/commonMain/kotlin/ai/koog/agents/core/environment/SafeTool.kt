@file:Suppress("UNCHECKED_CAST")

package ai.koog.agents.core.environment

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock

/**
 * A wrapper class designed to safely execute a tool within a given AI agent environment.
 * It provides mechanisms for handling tool execution results and differentiating between
 * success and failure cases.
 *
 * @param TArgs The type of arguments accepted by the underlying tool. Must extend [ToolArgs].
 * @param TResult The type of result produced by the underlying tool. Must extend [ToolResult].
 * @property tool The tool instance to be executed. Defines the operation and its required input/output behavior.
 * @property clock The clock used to determine tool call message timestamps
 * @property environment The environment in which the tool operates. Handles the execution of tool logic.
 */
public data class SafeTool<TArgs : ToolArgs, TResult : ToolResult>(
    private val tool: Tool<TArgs, TResult>,
    private val environment: AIAgentEnvironment,
    private val clock: Clock
) {
    /**
     * Represents a sealed interface for results, which can either be a success or a failure.
     *
     * This interface models the outcome of an operation, encapsulating both the result content
     * and the status of the operation (successful or failed). It is parameterized by `TResult`,
     * which must extend the `ToolResult` interface.
     *
     * @param TResult The type of the result, constrained to types that implement `ToolResult`.
     */
    public sealed interface Result<TResult : ToolResult> {
        /**
         * Represents the content of the result within the sealed interface `Result`.
         *
         * This property is used to hold the content as a `String`, which could vary depending on whether
         * the result is successful or a failure.
         *
         * - In the `Success` case, this corresponds to the provided content of the successful result.
         * - In the `Failure` case, this corresponds to the failure message.
         */
        public val content: String

        /**
         * Determines if the current result represents a successful operation.
         *
         * @return `true` if the result is an instance of `Success`, otherwise `false`.
         */
        public fun isSuccessful(): Boolean = this is Success<TResult>
        /**
         * Determines whether the current instance represents a failure state.
         *
         * @return `true` if the current instance is of type `Failure`, otherwise `false`.
         */
        public fun isFailure(): Boolean = this is Failure<TResult>
        /**
         * Casts the current instance of `Result` to a `Success` type if it is a successful result.
         *
         * @return The current instance cast to `Success<TResult>`.
         * @throws IllegalStateException if not [Success]
         */
        public fun asSuccessful(): Success<TResult> = when (this) {
            is Success<TResult> -> this
            is Failure<TResult> -> throw IllegalStateException("Result is not a success: $this")
        }

        /**
         * Casts the current object to a `Failure` type.
         *
         * This function assumes that the calling instance is of type `Failure<TResult>`.
         * Use it to retrieve the object as a `Failure` and access its specific properties and behaviors.
         *
         * @return The current instance cast to `Failure<TResult>`.
         * @throws IllegalStateException if not [Failure]
         */
        public fun asFailure(): Failure<TResult> = when (this) {
            is Success<TResult> -> throw IllegalStateException("Result is not a failure: $this")
            is Failure<TResult> -> this
        }

        /**
         * Represents a successful result of an operation, wrapping a specific tool result and its corresponding content.
         *
         * The `Success` class is a concrete implementation of the `Result` sealed interface,
         * specifically used to indicate successful outcomes. It stores both the result of type `TResult`
         * and its associated content as a string.
         *
         * @param TResult The type of the tool result, which must implement the `ToolResult` interface.
         * @property result The tool result encapsulated within this success instance.
         * @property content The associated content describing or representing the result in string format.
         */
        public data class Success<TResult : ToolResult>(val result: TResult, override val content: String) : Result<TResult>
        /**
         * Represents a failed result encapsulating an error message.
         *
         * This class extends the base `Result` interface and is used to indicate that a particular operation
         * has failed. The associated failure message provides additional context or details about the failure.
         *
         * @param TResult The type of the tool result associated with the operation.
         * @property message A descriptive error message explaining the reason for the failure.
         */
        public data class Failure<TResult : ToolResult>(val message: String) : Result<TResult> {
            /**
             * Returns the failure message associated with this result.
             *
             * The `content` property provides access to the descriptive error message encapsulated
             * within a `Failure` instance of the `Result` interface. This message is useful for
             * understanding the cause or nature of a failure in a tool operation.
             */
            override val content: String get() = message
        }
    }

    /**
     * Executes the tool with the provided arguments and returns the result.
     *
     * This method constructs a `Message.Tool.Call` with the given arguments,
     * passes it to the environment for execution, and converts the received tool result
     * into a safe result encapsulated in a `Result` type.
     *
     * @param args The arguments required for the tool execution.
     * @return A `Result` containing the outcome of the tool execution, either
     * a success or a failure.
     */
    @Suppress("UNCHECKED_CAST")
    public suspend fun execute(args: TArgs): Result<TResult> {
        return environment.executeTool(
            Message.Tool.Call(
                id = null,
                tool = tool.name,
                content = tool.encodeArgs(args).toString(),
                metaInfo = ResponseMetaInfo.create(clock)
            )
        ).toSafeResult()
    }

    /**
     * Executes a raw tool call with the given arguments, returning the resulting content as a string.
     *
     * @param args The arguments to encode and use in the tool call.
     * @return The content of the response from executing the tool.
     */
    public suspend fun executeRaw(args: TArgs): String {
        return environment.executeTool(
            Message.Tool.Call(
                id = null,
                tool = tool.name,
                content = tool.encodeArgs(args).toString(),
                metaInfo = ResponseMetaInfo.create(clock)
            )
        ).content
    }

    /**
     * Executes a tool with the provided arguments in an unsafe manner.
     * This method does not enforce type safety for the arguments provided to the tool.
     *
     * @param args The arguments to be passed to the tool, represented as a ToolArgs object.
     * @return A Result containing the outcome of the tool execution with TResult as the result type.
     */
    @Suppress("UNCHECKED_CAST")
    public suspend fun executeUnsafe(args: ToolArgs): Result<TResult> {
        return environment.executeTool(
            Message.Tool.Call(
                id = null,
                tool = tool.name,
                content = tool.encodeArgs(args as TArgs).toString(),
                metaInfo = ResponseMetaInfo.create(clock)
            )
        ).toSafeResult()
    }
}

/**
 * Converts a [ReceivedToolResult] instance into a [SafeTool.Result] for safer result handling.
 *
 * If the `result` in the [ReceivedToolResult] is null, it returns a [SafeTool.Result.Failure]
 * containing the `content` as a failure message. Otherwise, it casts the `result` to the given
 * type parameter [TResult] and returns a [SafeTool.Result.Success] containing the `result` and `content`.
 *
 * @return A [SafeTool.Result] which will either be a [SafeTool.Result.Failure] or [SafeTool.Result.Success]
 * based on the presence and validity of the `result` in the [ReceivedToolResult].
 */
public fun <TResult : ToolResult> ReceivedToolResult.toSafeResult(): SafeTool.Result<TResult> = when (result) {
    null -> SafeTool.Result.Failure(message = content)
    else -> SafeTool.Result.Success(result = result as TResult, content = content)
}
