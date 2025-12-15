package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.serialization.ToolJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Base class representing a tool that can be invoked by the LLM.
 * Tools are usually used to return results, make changes to the environment, or perform other actions.
 *
 * @param TArgs The type of arguments the tool accepts.
 * @param TResult The type of result the tool returns.
 * Provides a textual explanation of what the tool does and how it can be used (for the LLM).
 * @property argsSerializer A [KSerializer] responsible for encoding and decoding the arguments required for the tool execution.
 * @property resultSerializer A [KSerializer] responsible for encoding and decoding the result returned by the tool execution.
 * @property descriptor A [ToolDescriptor] representing the tool's schema, including its name, description, and parameters.
 */
public abstract class Tool<TArgs, TResult>(
    public val argsSerializer: KSerializer<TArgs>,
    public val resultSerializer: KSerializer<TResult>,
    public val descriptor: ToolDescriptor,
) {
    /**
     * The name of the tool from the [descriptor]
     */
    public val name: String get() = descriptor.name

    /**
     * Wraps [argsSerializer] to handle primitive types, ensuring all tool arguments serialize to [JsonObject] as required by LLM APIs.
     */
    @OptIn(InternalAgentToolsApi::class)
    private val actualArgsSerializer: KSerializer<TArgs> = argsSerializer.asToolDescriptorSerializer()

    /**
     * The [Json] used to encode and decode the arguments and results of the tool.
     */
    @OptIn(InternalAgentToolsApi::class)
    protected open val json: Json = ToolJson

    /**
     * Convenience constructor for the base tool class that generates [ToolDescriptor] from the provided
     * [name], [description] and [argsSerializer] using [asToolDescriptor]
     *
     * @param argsSerializer A [KSerializer] responsible for encoding and decoding the arguments required for the tool execution.
     * @param resultSerializer A [KSerializer] responsible for encoding and decoding the result returned by the tool execution.
     * @param name The name of the tool.
     * @param description Textual explanation of what the tool does and how it can be used (for the LLM).
     */
    @OptIn(InternalAgentToolsApi::class)
    public constructor(
        argsSerializer: KSerializer<TArgs>,
        resultSerializer: KSerializer<TResult>,
        name: String,
        description: String,
    ) : this(
        argsSerializer = argsSerializer,
        resultSerializer = resultSerializer,
        descriptor = argsSerializer.descriptor.asToolDescriptor(name, description)
    )

    /**
     * Executes the tool's logic with the provided arguments.
     *
     * In the actual agent implementation, it is not recommended to call tools directly as this might cause issues, such as:
     * - Missing EventHandler events
     * - Bugs with feature pipelines
     * - Inability to test/mock
     *
     * Consider using methods like `findTool(tool: Class)` or similar, to retrieve a `SafeTool`, and then call `execute`
     * on it. This ensures that the tool call is delegated properly to the underlying `environment` object.
     *
     * @param args The input arguments required to execute the tool.
     * @return The result of the tool's execution.
     */
    public abstract suspend fun execute(args: TArgs): TResult

    /**
     * Executes the tool with the provided arguments, bypassing type safety checks.
     *
     * @param args The input arguments for the tool execution, provided as a generic [Any] type. The method attempts to cast this to the expected argument type [TArgs].
     * @return The result of executing the tool, as an instance of type [TResult].
     * @throws ClassCastException if the provided arguments cannot be cast to the expected type [TArgs].
     *
     * @suppress
     */
    @InternalAgentToolsApi
    public suspend fun executeUnsafe(args: Any?): TResult {
        return withUnsafeCast<TArgs, TResult>(
            args,
            "executeUnsafe argument must be castable to TArgs"
        ) { execute(it) }
    }

    /**
     * Decodes the provided raw JSON arguments into an instance of the specified arguments type.
     *
     * @param rawArgs the raw JSON object that contains the encoded arguments
     * @return the decoded arguments of type TArgs
     */
    public fun decodeArgs(rawArgs: JsonObject): TArgs = json.decodeFromJsonElement(actualArgsSerializer, rawArgs)

    /**
     * Decodes the provided raw JSON element into an instance of the specified result type.
     *
     * @param rawResult The raw JSON element that contains the encoded result.
     * @return The decoded result of type TResult.
     */
    public fun decodeResult(rawResult: JsonElement): TResult =
        json.decodeFromJsonElement(resultSerializer, rawResult)

    /**
     * Encodes the given arguments into a JSON representation.
     *
     * @param args The arguments to be encoded.
     * @return A JsonObject representing the encoded arguments.
     */
    public fun encodeArgs(args: TArgs): JsonObject = json.encodeToJsonElement(actualArgsSerializer, args).jsonObject

    /**
     * Encodes the given arguments into a JSON representation without type safety checks.
     *
     * This method attempts to cast the arguments to the expected type and uses the configured serializer
     * for the actual encoding. Use caution when calling this method, as bypassing type safety may lead
     * to runtime exceptions if the cast is invalid.
     *
     * @param args The input arguments to be encoded. These are provided as a generic `Any?` type and are
     *             internally cast to the expected type.
     * @return A JsonObject representing the encoded arguments.
     * @throws ClassCastException If the provided arguments cannot be cast to the expected type.
     */
    public fun encodeArgsUnsafe(args: Any?): JsonObject {
        return withUnsafeCast<TArgs, JsonObject>(
            args,
            "encodeArgsUnsafe argument must be castable to TArgs"
        ) { json.encodeToJsonElement(actualArgsSerializer, it).jsonObject }
    }

    /**
     * Encodes the given result into a JSON representation using the configured result serializer.
     *
     * @param result The result object of type TResult to be encoded.
     * @return A JsonObject representing the encoded result.
     */
    public fun encodeResult(result: TResult): JsonElement =
        json.encodeToJsonElement(resultSerializer, result)

    /**
     * Encodes the given result object into a JSON representation without type safety checks.
     * This method casts the provided result to the expected `TResult` type and leverages the `encodeResult` method
     * to produce the JSON output.
     *
     * @param result The result object of type `Any?` to be encoded. It is internally cast to `TResult`,
     *               which may lead to runtime exceptions if the cast is invalid.
     * @return A JsonObject representing the encoded result.
     */
    @InternalAgentToolsApi
    public fun encodeResultUnsafe(result: Any?): JsonElement {
        return withUnsafeCast<TResult, JsonElement>(
            result,
            "encodeResultUnsafe argument must be castable to TResult",
        ) { encodeResult(it) }
    }

    /**
     * Encodes the provided arguments into a JSON string representation using the configured serializer.
     *
     * @param args the arguments to be encoded into a JSON string
     * @return the JSON string representation of the provided arguments
     */
    public fun encodeArgsToString(args: TArgs): String = json.encodeToString(actualArgsSerializer, args)

    /**
     * Encodes the provided arguments into a JSON string representation without type safety checks.
     *
     * This method casts the provided `args` to the expected `TArgs` type and invokes the type-safe
     * `encodeArgsToString` method to perform the encoding. Use caution when calling this method,
     * as it bypasses type safety and may result in a runtime exception if the cast fails.
     *
     * @param args The arguments to be encoded into a JSON string, provided as a generic `Any?` type.
     * @return A JSON string representation of the provided arguments.
     * @throws ClassCastException If the provided arguments cannot be cast to the expected type `TArgs`.
     */
    public fun encodeArgsToStringUnsafe(args: Any?): String {
        return withUnsafeCast<TArgs, String>(
            args,
            "encodeArgsToStringUnsafe argument must be castable to TArgs",
        ) { encodeArgsToString(it) }
    }

    /**
     * Encodes the given result of type TResult to its string representation for the LLM.s
     *
     * @param result The result object of type TResult to be encoded into a string.
     * @return The string representation of the given result.
     */
    public open fun encodeResultToString(result: TResult): String = json.encodeToString(resultSerializer, result)

    /**
     * Encodes the provided result object into a JSON string representation without type safety checks.
     *
     * This method casts the given result to the expected `TResult` type and uses the `resultSerializer`
     * to encode it into a string. Use with caution, as it bypasses type safety and may throw runtime exceptions
     * if the cast fails.
     *
     * @param result The result object of type `Tool.Result` to be encoded.
     * @return A JSON string representation of the provided result.
     */
    public fun encodeResultToStringUnsafe(result: Any?): String {
        return withUnsafeCast<TResult, String>(
            result,
            "encodeResultToStringUnsafe argument must be castable to TResult",
        ) { encodeResultToString(it) }
    }

    /**
     * Utility method to perform unsafe cast while providing a more descriptive error message.
     * Because default [ClassCastException] contains very little information, making it harder to debug in concurrent workflows with tools.
     *
     * @param T Expected type of the input object to be cast unsafely to.
     * @param R Result type.
     * @param input Input object to be cast.
     * @param errorMessage Additional short error message to include in the exception message, e.g. method name with an explanation.
     * @param action Action to be performed with the input object after successful cast.
     * @throws ClassCastException containing additional debug information in its message
     */
    private inline fun <T, R> withUnsafeCast(
        input: Any?,
        errorMessage: String,
        action: (T) -> R,
    ): R {
        return try {
            @Suppress("UNCHECKED_CAST")
            action(input as T)
        } catch (e: ClassCastException) {
            throw ClassCastException(
                """
                Unsafe cast failed in tool with name: $name
                Error message: $errorMessage
                Original ClassCastException message: ${e.message}
                """.trimIndent()
            )
        }
    }

    /**
     * Base type, representing tool arguments.
     */
    @Deprecated("Extending Tool.Args is no longer required. Tool arguments are entirely handled by KotlinX Serialization.")
    @Suppress("DEPRECATION")
    public interface Args : ToolArgs

    /**
     * Args implementation that can be used for tools that expect no arguments.
     */
    @Deprecated("Extending Tool.Args is no longer required. Tool arguments are entirely handled by KotlinX Serialization.")
    @Suppress("DEPRECATION")
    @Serializable
    public data object EmptyArgs : Args
}
