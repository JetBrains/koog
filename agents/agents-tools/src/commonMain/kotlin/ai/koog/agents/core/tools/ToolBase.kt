package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.schema.defaultJsonSchemaConfig
import ai.koog.agents.core.tools.schema.getToolDescriptor
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.TypeToken
import ai.koog.serialization.annotations.InternalKoogSerializationApi
import kotlinx.schema.generator.json.JsonSchemaConfig

/**
 * The runtime contract that every tool dispatched by the agent satisfies.
 *
 * The agent runtime invokes [execute] uniformly, threading caller- and feature-supplied
 * [ToolCallMetadata] alongside the typed [TArgs]. Subclasses define what that overload means:
 *
 * - [Tool] is the conventional shape with an [Tool.execute] taking only the arguments. Existing
 *   subclasses keep working unchanged.
 * - [ai.koog.agents.core.agent.tools.AgentContextAwareTool] (in `agents-core`) gives the implementation
 *   typed access to the live `AIAgentContext` driving the current call.
 *
 * A subclass that needs raw [ToolCallMetadata] (for example to read a trace span id contributed by a
 * feature) can extend [ToolBase] directly and override [execute]. Splitting that concern from the
 * agent-context concern keeps `agents-tools` free of any `agents-core` dependency.
 *
 * @param TArgs The type of arguments the tool accepts.
 * @param TResult The type of result the tool returns.
 * @property argsType Type token representing arguments type [TArgs].
 * @property resultType Type token representing result type [TResult].
 * @property descriptor A [ToolDescriptor] representing the tool's schema, including its name, description, and parameters.
 * @property metadata A map of static metadata associated with the tool. Distinct from the per-call
 *  [ToolCallMetadata] threaded into [execute]; this field describes the tool, not the invocation.
 */
public abstract class ToolBase<TArgs, TResult>(
    public val argsType: TypeToken,
    public val resultType: TypeToken,
    public val descriptor: ToolDescriptor,
    // TODO replace with JSONObject, add to other constructors
    public val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * The name of the tool from the [descriptor].
     */
    public val name: String get() = descriptor.name

    /**
     * Convenience constructor that generates [ToolDescriptor] from the provided
     * [name], [description] and [argsType].
     *
     * @param argsType Type token representing arguments type [TArgs].
     * @param resultType Type token representing result type [TResult].
     * @param name The name of the tool.
     * @param description Textual explanation of what the tool does and how it can be used (for the LLM).
     * @param jsonSchemaConfig Optional custom [JsonSchemaConfig] for the tool schema generation.
     */
    @OptIn(InternalAgentToolsApi::class, InternalKoogSerializationApi::class)
    public constructor(
        argsType: TypeToken,
        resultType: TypeToken,
        name: String,
        description: String,
        jsonSchemaConfig: JsonSchemaConfig = defaultJsonSchemaConfig,
    ) : this(
        argsType = argsType,
        resultType = resultType,
        descriptor = getToolDescriptor(argsType, name, description, jsonSchemaConfig)
    )

    /**
     * Executes the tool's logic with the provided arguments and per-call [metadata].
     *
     * This is the single dispatch entry point used by the agent runtime. [metadata] is an additive
     * side channel for cross-cutting context (for example a tracing span id or a correlation id);
     * it is not part of the argument schema and is not exposed to the LLM.
     *
     * Most tools should extend [Tool] (which provides a no-metadata `execute(args)` shape) or
     * [ai.koog.agents.core.tools.AgentContextAwareTool] (which provides typed access to the live
     * `AIAgentContext`) rather than overriding this overload directly. Override here only when you
     * need to read raw [ToolCallMetadata] entries.
     *
     * In the actual agent implementation, it is not recommended to call tools directly as this might
     * cause issues, such as:
     * - Bugs with feature pipelines
     * - Inability to test/mock
     *
     * Consider using methods like `findTool(tool: Class)` or similar, to retrieve a `SafeTool`, and
     * then call `execute` on it. This ensures that the tool call is delegated properly to the
     * underlying `environment` object.
     *
     * @param args The input arguments required to execute the tool.
     * @param metadata Caller- and feature-contributed per-call context. [ToolCallMetadata.EMPTY] when absent.
     * @return The result of the tool's execution.
     */
    public abstract suspend fun execute(args: TArgs, metadata: ToolCallMetadata): TResult

    /**
     * Executes the tool with the provided arguments without type safety checks.
     *
     * @throws ClassCastException if the provided arguments cannot be cast to the expected type [TArgs].
     */
    @InternalAgentToolsApi
    public suspend fun executeUnsafe(args: Any?): TResult =
        executeUnsafe(args, ToolCallMetadata.EMPTY)

    /**
     * Executes the tool with the provided arguments and [metadata] without type safety checks.
     *
     * @throws ClassCastException if the provided arguments cannot be cast to the expected type [TArgs].
     */
    @InternalAgentToolsApi
    public suspend fun executeUnsafe(args: Any?, metadata: ToolCallMetadata): TResult {
        return withUnsafeCast<TArgs, TResult>(
            args,
            "executeUnsafe argument must be castable to TArgs"
        ) { execute(it, metadata) }
    }

    /**
     * Decodes the provided raw JSON arguments into an instance of the specified arguments type.
     *
     * @param rawArgs The raw JSON object that contains the encoded arguments
     * @param serializer The JSON serializer to use.
     */
    public open fun decodeArgs(
        rawArgs: JSONObject,
        serializer: JSONSerializer
    ): TArgs = serializer.decodeFromJSONElement(rawArgs, argsType)

    /**
     * Decodes the provided raw JSON element into an instance of the specified result type.
     *
     * @param rawResult The raw JSON element that contains the encoded result.
     * @param serializer The JSON serializer to use.
     */
    public open fun decodeResult(
        rawResult: JSONElement,
        serializer: JSONSerializer,
    ): TResult = serializer.decodeFromJSONElement(rawResult, resultType)

    /**
     * Encodes the given arguments into a JSON representation.
     *
     * @param args The arguments to be encoded.
     * @param serializer The JSON serializer to use.
     */
    public open fun encodeArgs(
        args: TArgs,
        serializer: JSONSerializer,
    ): JSONObject = serializer.encodeToJSONElement(args, argsType) as JSONObject

    /**
     * Encodes the given arguments into a JSON representation without type safety checks.
     *
     * @throws ClassCastException If the provided arguments cannot be cast to the expected type.
     */
    @OptIn(InternalAgentToolsApi::class)
    public fun encodeArgsUnsafe(
        args: Any?,
        serializer: JSONSerializer,
    ): JSONObject {
        return withUnsafeCast<TArgs, JSONObject>(
            args,
            "encodeArgsUnsafe argument must be castable to TArgs"
        ) { serializer.encodeToJSONElement(it, argsType) as JSONObject }
    }

    /**
     * Encodes the given result into a JSON representation.
     *
     * @param result The result object of type TResult to be encoded.
     * @param serializer The JSON serializer to use.
     */
    public open fun encodeResult(
        result: TResult,
        serializer: JSONSerializer,
    ): JSONElement = serializer.encodeToJSONElement(result, resultType)

    /**
     * Encodes the given result object into a JSON representation without type safety checks.
     *
     * @throws ClassCastException If the provided result cannot be cast to the expected type TResult.
     */
    @InternalAgentToolsApi
    public fun encodeResultUnsafe(
        result: Any?,
        serializer: JSONSerializer,
    ): JSONElement {
        return withUnsafeCast<TResult, JSONElement>(
            result,
            "encodeResultUnsafe argument must be castable to TResult",
        ) { encodeResult(it, serializer) }
    }

    /**
     * Encodes the provided arguments into a JSON string representation.
     *
     * @param args the arguments to be encoded into a JSON string
     * @param serializer The JSON serializer to use.
     */
    public fun encodeArgsToString(
        args: TArgs,
        serializer: JSONSerializer,
    ): String = serializer.encodeJSONElementToString(encodeArgs(args, serializer))

    /**
     * Encodes the provided arguments into a JSON string representation without type safety checks.
     *
     * @throws ClassCastException If the provided arguments cannot be cast to the expected type `TArgs`.
     */
    @OptIn(InternalAgentToolsApi::class)
    public fun encodeArgsToStringUnsafe(
        args: Any?,
        serializer: JSONSerializer,
    ): String {
        return withUnsafeCast<TArgs, String>(
            args,
            "encodeArgsToStringUnsafe argument must be castable to TArgs",
        ) { encodeArgsToString(it, serializer) }
    }

    /**
     * Encodes the given result of type [TResult] to its string representation.
     * This is used to provide the LLM with the result of the tool execution.
     * It can be overridden to customize the string representation the LLM will see.
     *
     * @param result The result object of type TResult to be encoded into a string.
     * @param serializer The JSON serializer to use.
     */
    public open fun encodeResultToString(
        result: TResult,
        serializer: JSONSerializer,
    ): String = serializer.encodeJSONElementToString(encodeResult(result, serializer))

    /**
     * Encodes the provided result object into a JSON string representation without type safety checks.
     *
     * @throws ClassCastException If the provided result cannot be cast to the expected type TResult.
     */
    @OptIn(InternalAgentToolsApi::class)
    public fun encodeResultToStringUnsafe(
        result: Any?,
        serializer: JSONSerializer,
    ): String {
        return withUnsafeCast<TResult, String>(
            result,
            "encodeResultToStringUnsafe argument must be castable to TResult",
        ) { encodeResultToString(it, serializer) }
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
    @InternalAgentToolsApi
    protected inline fun <T, R> withUnsafeCast(
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
}
