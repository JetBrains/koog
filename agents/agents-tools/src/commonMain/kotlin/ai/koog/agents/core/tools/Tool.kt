package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.schema.defaultJsonSchemaConfig
import ai.koog.agents.core.tools.schema.getToolDescriptor
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.KSerializerTypeToken
import ai.koog.serialization.TypeToken
import ai.koog.serialization.annotations.InternalKoogSerializationApi
import kotlinx.coroutines.withContext
import kotlinx.schema.generator.json.JsonSchemaConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Base class representing a tool that can be invoked by the LLM.
 * Tools are usually used to return results, make changes to the environment, or perform other actions.
 *
 * @param TArgs The type of arguments the tool accepts.
 * @param TResult The type of result the tool returns.
 * @property argsType Type token representing arguments type [TArgs].
 * @property resultType Type token representing result type [TResult].
 * @property descriptor A [ToolDescriptor] representing the tool's schema, including its name, description, and parameters.
 * @property metadata A map of arbitrary metadata associated with the tool.
 */
public abstract class Tool<TArgs, TResult>(
    public val argsType: TypeToken,
    public val resultType: TypeToken,
    public val descriptor: ToolDescriptor,
    // TODO replace with JSONObject, add to other constructors
    public val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * The name of the tool from the [descriptor]
     */
    public val name: String get() = descriptor.name

    /**
     * Convenience constructor for the base tool class that generates [ToolDescriptor] from the provided
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

    //region Deprecated constructors

    @Deprecated("Use TypeToken constructors instead")
    @OptIn(InternalKoogSerializationApi::class)
    public constructor(
        argsSerializer: KSerializer<TArgs>,
        resultSerializer: KSerializer<TResult>,
        descriptor: ToolDescriptor,
    ) : this(
        argsType = KSerializerTypeToken(argsSerializer),
        resultType = KSerializerTypeToken(resultSerializer),
        descriptor = descriptor,
    )

    @Deprecated("Use TypeToken constructors instead")
    @Suppress("DEPRECATION")
    @OptIn(InternalAgentToolsApi::class, InternalKoogSerializationApi::class)
    public constructor(
        argsSerializer: KSerializer<TArgs>,
        resultSerializer: KSerializer<TResult>,
        name: String,
        description: String,
    ) : this(
        argsSerializer = argsSerializer,
        resultSerializer = resultSerializer,
        descriptor = getToolDescriptor(KSerializerTypeToken(argsSerializer), name, description)
    )

    //endregion

    /**
     * Executes the tool's logic with the provided arguments.
     *
     * In the actual agent implementation, it is not recommended to call tools directly as this might cause issues, such as:
     * - Bugs with feature pipelines
     * - Inability to test/mock
     *
     * Consider using methods like `findTool(tool: Class)` or similar, to retrieve a `SafeTool`, and then call `execute`
     * on it. This ensures that the tool call is delegated properly to the underlying `environment` object.
     *
     * Subclasses must override at least one of the two `execute` overloads:
     * - Override this overload for tools that do not depend on per-call metadata.
     * - Override [execute] with [ToolCallMetadata] for tools that need a trace span id, correlation id,
     *   feature-contributed flag, or the live agent context. The default implementation here routes to
     *   that overload with [ToolCallMetadata.EMPTY], so a subclass that overrides only the metadata-aware
     *   overload still works when called via `execute(args)`.
     *
     * If a subclass overrides neither overload, the first invocation throws [NotImplementedError] with a
     * clear message; the mutual delegation between the two overloads is detected via a coroutine-context
     * marker so the failure is loud and immediate rather than a stack overflow.
     *
     * @param args The input arguments required to execute the tool.
     * @return The result of the tool's execution.
     */
    public open suspend fun execute(args: TArgs): TResult =
        withContext(NoOverrideMarker) {
            execute(args, ToolCallMetadata.EMPTY)
        }

    /**
     * Executes the tool's logic with the provided arguments and caller-contributed [metadata].
     *
     * The default implementation delegates to [execute] and ignores [metadata], preserving the behavior
     * of existing subclasses. Override this overload when the tool needs to read per-call context such as
     * a trace span id, a correlation id, or a feature-contributed flag. Metadata is a side channel: it is
     * not part of the argument schema and is not exposed to the LLM.
     *
     * A subclass that overrides only this overload reads metadata when invoked via `execute(args, metadata)`,
     * and observes [ToolCallMetadata.EMPTY] when invoked via `execute(args)` (the default of the
     * single-argument overload routes here).
     *
     * If a subclass overrides neither overload, this default detects the mutual delegation via a
     * coroutine-context marker placed by the default body of [execute] and throws
     * [NotImplementedError] with a clear message.
     *
     * @param args The input arguments required to execute the tool.
     * @param metadata Caller- and feature-contributed per-call context. [ToolCallMetadata.EMPTY] when absent.
     * @return The result of the tool's execution.
     */
    public open suspend fun execute(args: TArgs, metadata: ToolCallMetadata): TResult {
        if (coroutineContext[NoOverrideMarker.Key] != null) {
            throw NotImplementedError(
                "Tool '$name' overrides neither execute(args) nor execute(args, metadata). " +
                    "Override at least one of these overloads.",
            )
        }
        return execute(args)
    }

    /**
     * Executes the tool with the provided arguments without type safety checks.
     *
     * @throws ClassCastException if the provided arguments cannot be cast to the expected type [TArgs].
     */
    @InternalAgentToolsApi
    public suspend fun executeUnsafe(args: Any?): TResult {
        return withUnsafeCast<TArgs, TResult>(
            args,
            "executeUnsafe argument must be castable to TArgs"
        ) { execute(it) }
    }

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

    /**
     * Base type, representing tool arguments.
     */
    @Deprecated("Extending Tool.Args is no longer required.")
    @Suppress("DEPRECATION")
    public interface Args : ToolArgs

    /**
     * Args implementation that can be used for tools that expect no arguments.
     */
    @Deprecated("Extending Tool.Args is no longer required.")
    @Suppress("DEPRECATION")
    @Serializable
    public data object EmptyArgs : Args

    /**
     * Coroutine-context element placed by the default body of [execute] before it delegates to
     * [execute] with [ToolCallMetadata]. The default body of the metadata-aware overload checks
     * for this element; its presence indicates that the subclass overrode neither `execute`
     * overload, in which case continuing the delegation would mutually recurse, so it throws
     * [NotImplementedError] instead.
     *
     * Internal to [Tool]; not part of the public surface and never observable to user-supplied
     * [ToolCallMetadata].
     */
    private object NoOverrideMarker : AbstractCoroutineContextElement(Key) {
        object Key : CoroutineContext.Key<NoOverrideMarker>
    }
}
