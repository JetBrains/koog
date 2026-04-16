package ai.koog.agents.core.environment

import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.JSONSerializer
import kotlin.time.Clock

/**
 * A wrapper class designed to safely execute a tool within a given AI agent environment.
 * It provides mechanisms for handling tool execution results and differentiating between
 * success and failure cases.
 *
 * @property tool The tool instance to be executed. Defines the operation and its required input/output behavior.
 * @property clock The clock used to determine tool call message timestamps
 * @property environment The environment in which the tool operates. Handles the execution of tool logic.
 */
public data class SafeTool<TArgs, TResult>(
    internal val tool: Tool<TArgs, TResult>,
    internal val environment: AIAgentEnvironment,
    internal val clock: Clock
) {
    /**
     * Represents a sealed interface for results, which can either be a success or a failure.
     *
     * @param TResult The type of the result
     */
    public sealed interface Result<TResult> {
        /**
         * Content of the result
         *
         * - In the [Success] case, this corresponds to the provided content of the successful result.
         * - In the ]Failure] case, this corresponds to the failure message.
         */
        public val content: String

        /**
         * Determines if the current result represents a successful operation.
         *
         * @return `true` if the result is an instance of [Success], otherwise `false`.
         */
        public fun isSuccessful(): Boolean = this is Success<TResult>

        /**
         * Determines whether the current instance represents a failure state.
         *
         * @return `true` if the current instance is of type [Failure], otherwise `false`.
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
         * @param TResult The type of the tool result.
         * @property result The tool result
         * @property content The associated content describing or representing the result in string format.
         */
        public data class Success<TResult>(
            val result: TResult,
            override val content: String
        ) : Result<TResult>

        /**
         * Represents a failed result encapsulating an error message.
         * @param TResult The type of the tool result.
         * @property message A descriptive error message explaining the reason for the failure.
         */
        public data class Failure<TResult>(val message: String) : Result<TResult> {
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
     * @param serializer The JSON serializer for encoding the tool arguments.
     * @return A [Result] containing the outcome of the tool execution, either
     * a success or a failure.
     */
    public suspend fun execute(
        args: TArgs,
        serializer: JSONSerializer,
    ): Result<TResult> {
        return environment.executeTool(
            Message.Tool.Call(
                id = null,
                tool = tool.name,
                content = tool.encodeArgsToString(args, serializer),
                metaInfo = ResponseMetaInfo.create(clock)
            )
        ).toSafeResult(tool, serializer)
    }

    /**
     * Executes a tool with the provided arguments in an unsafe manner.
     * This method does not enforce type safety for the arguments provided to the tool.
     *
     * @param args The arguments to be passed to the tool.
     * @param serializer The JSON serializer for encoding the tool arguments.
     * @return A [Result] containing the outcome of the tool execution with TResult as the result type.
     */
    public suspend fun executeUnsafe(
        args: Any?,
        serializer: JSONSerializer,
    ): Result<TResult> {
        @Suppress("UNCHECKED_CAST")
        return execute(args as TArgs, serializer)
    }
}

/**
 * Converts a [ReceivedToolResult] instance into a [SafeTool.Result] for safer result handling.
 *
 * @return A [SafeTool.Result] which will either be a [SafeTool.Result.Failure] or [SafeTool.Result.Success]
 */
public fun <TResult> ReceivedToolResult.toSafeResult(
    tool: Tool<*, TResult>,
    serializer: JSONSerializer,
): SafeTool.Result<TResult> {
    val encodedResult = result ?: return SafeTool.Result.Failure(message = content)
    val decodedResult = try {
        tool.decodeResult(encodedResult, serializer)
    } catch (e: Exception) {
        return SafeTool.Result.Failure("Tool with name '${tool.name}' failed to deserialize result with error: ${e.message}")
    }

    return SafeTool.Result.Success(result = decodedResult, content = content)
}
