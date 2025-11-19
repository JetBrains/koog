@file:Suppress("UNCHECKED_CAST")

package ai.koog.agents.core.environment

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.reflect.ToolFromCallable
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlin.reflect.KFunction

/**
 * A wrapper class that creates a safer abstraction for executing a tool function alongside an associated environment.
 * The tool function is backed by a callable in the form of a `KFunction` and interacts with the `AIAgentEnvironment`
 * to perform operations and exchange data.
 *
 * @param TResult The type of the result produced by the tool function.
 * @property toolFunction The Kotlin function to be wrapped and executed.
 * @property environment An environment object responsible for executing tools and handling results.
 * @property clock The clock used to determine tool call message time
 */
public data class SafeToolFromCallable<TResult>(
    public val toolFunction: KFunction<TResult>,
    private val environment: AIAgentEnvironment,
    private val clock: Clock
) {

    /**
     * Provides access to a `ToolFromCallable` instance derived from the `toolFunction` property.
     *
     * This property lazily retrieves a tool implementation, wrapping the callable
     * represented by `toolFunction` and converting it into an executable tool.
     *
     * The `ToolFromCallable` instance encapsulates metadata about the callable,
     * including its name, description, parameters, and serialization mechanism.
     *
     * Primarily used in the context of tool execution, such as invoking the `execute` or
     * `executeRaw` methods on the enclosing class, to resolve and interact with the callable as a tool.
     */
    private val tool: Tool<ToolFromCallable.VarArgs, TResult> get() = toolFunction.asTool()

    /**
     * Encodes the provided arguments into a `VarArgs` object to be used by the tool function.
     *
     * @param args The arguments to map to the parameters of the tool function. The number of arguments
     * must match the number of parameters defined in the tool function.
     * @return A `VarArgs` object containing the mapping of function parameters to their respective arguments.
     * @throws IllegalArgumentException If the number of arguments does not match the number of parameters
     * in the tool function.
     */
    private fun encodeArgs(vararg args: Any?): ToolFromCallable.VarArgs {
        val params = toolFunction.parameters
        check(args.size == params.size) {
            "Number of arguments provided must match the the number " +
                "of parameters in the tool function:" +
                " ${toolFunction.name} ${params.size} != ${args.size}"
        }

        val argsMap = (params zip args).associate { (param, arg) ->
            param to arg
        }

        return ToolFromCallable.VarArgs(argsMap)
    }

    /**
     * Represents the outcome of an operation, encapsulating the result state and its associated content.
     *
     * A `Result` can either be a successful outcome represented by [Success], or a failure represented
     * by [Failure]. This interface provides utility functions to determine the specific result type
     * and safely access the encapsulated data.
     *
     * @param TResult The type of the result data when the operation is successful.
     */
    public sealed interface Result<TResult> {
        /**
         * Represents the associated content of a `Result`, which may vary depending on its implementation type.
         *
         * In a `Success` instance, this usually holds the content alongside the successful result.
         * In a `Failure` instance, this typically contains the failure message.
         */
        public val content: String

        /**
         * Determines whether the current Result instance represents a successful outcome.
         *
         * @return true if the instance is of type Success, false otherwise.
         */
        public fun isSuccessful(): Boolean = this is Success<TResult>

        /**
         * Checks whether the current instance represents a failure result.
         *
         * @return true if the instance is of type `Failure`, otherwise false.
         */
        public fun isFailure(): Boolean = this is Failure<TResult>

        /**
         * Casts the current `Result` instance to `Success<TResult>`.
         *
         * @return The current instance as a `Success<TResult>`.
         * @throws ClassCastException if the instance is not of type `Success<TResult>`.
         */
        public fun asSuccessful(): Success<TResult> = this as Success<TResult>

        /**
         * Casts the current `Result` instance to a `Failure` type.
         *
         * @return The current instance cast as a `Failure<TResult>`.
         * @throws ClassCastException if the current instance is not of type `Failure<TResult>`.
         */
        public fun asFailure(): Failure<TResult> = this as Failure<TResult>

        /**
         * Represents a successful result of an operation.
         *
         * @param TResult The type of the result.
         * @property result The result of the operation.
         * @property content Additional content or metadata associated with the operation result.
         */
        public data class Success<TResult>(val result: TResult, override val content: String) :
            Result<TResult>

        /**
         * Represents a failure result in the context of a result model.
         *
         * This class is a concrete implementation of the `Result` sealed interface, specifically
         * used to represent a failure case. It contains an error message that can be used
         * to describe the failure or provide additional context about the error.
         *
         * @param TResult The type parameter representing the type of the result in successful cases.
         * @property message A string containing the error message or details about the failure.
         */
        public data class Failure<TResult>(val message: String) : Result<TResult> {
            /**
             * Provides access to the error message associated with the failure result.
             * Retrieves the content as a string representation of the error message.
             */
            override val content: String get() = message
        }
    }

    /**
     * Executes the tool call using the provided arguments and returns the result.
     *
     * @param args The arguments to be passed to the tool during its execution.
     * @return A result object containing the outcome of the tool call, including success or failure information.
     */
    public suspend fun execute(vararg args: Any?): Result<TResult> {
        return environment.executeTool(
            Message.Tool.Call(
                id = null,
                tool = tool.name,
                content = tool.encodeArgsToString(encodeArgs(*args)),
                metaInfo = ResponseMetaInfo.create(clock = clock)
            )
        ).toSafeResultFromCallable(tool)
    }

    /**
     * Executes a raw call to the specified tool with the provided arguments and returns the result as a string.
     *
     * @param args the arguments to be passed to the tool during execution
     * @return the raw output from the tool as a string
     */
    public suspend fun executeRaw(vararg args: Any?): String {
        return environment.executeTool(
            Message.Tool.Call(
                id = null,
                tool = tool.name,
                content = tool.encodeArgsToString(encodeArgs(*args)),
                metaInfo = ResponseMetaInfo.create(clock = clock)
            )
        ).content
    }
}

/**
 * Converts a `ReceivedToolResult` instance to a `SafeToolFromCallable.Result` object.
 *
 * This function evaluates the `result` property of the `ReceivedToolResult`. If the `result`
 * is null, it returns a `SafeToolFromCallable.Result.Failure` containing the `content` as the message.
 * If the `result` is non-null, it attempts to cast the `result` to a `ToolFromCallable.Result`
 * and extracts its `result` value as the `SafeToolFromCallable.Result.Success` with the given content.
 *
 * @return A `SafeToolFromCallable.Result` object, either a `Success` with the extracted result
 *         and content or a `Failure` with an appropriate message.
 */
private fun <TResult> ReceivedToolResult.toSafeResultFromCallable(tool: Tool<ToolFromCallable.VarArgs, TResult>): SafeToolFromCallable.Result<TResult> =
    when (result) {
        null -> SafeToolFromCallable.Result.Failure(message = content)
        else -> SafeToolFromCallable.Result.Success(
            result = tool.decodeResult(result),
            content = content
        )
    }
