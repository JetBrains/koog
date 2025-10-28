package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.serialization.ToolJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Represents a tool that, when executed, makes changes to the environment.
 */
public abstract class Tool<TArgs, TResult> {
    /**
     * Serializer responsible for encoding and decoding the arguments required for the tool execution.
     * This abstract property is used to define the specific [KSerializer] corresponding to the type of arguments
     * expected by the tool.
     *
     * The implementation must provide a concrete serializer for the `TArgs` type parameter, which ensures
     * proper serialization and deserialization of the tool arguments.
     */
    public abstract val argsSerializer: KSerializer<TArgs>

    // FIXME just a quickfix, more proper and thorough update is required for Tool
    @OptIn(InternalAgentToolsApi::class)
    private val actualArgsSerializer by lazy {
        argsSerializer.asToolDescriptorSerializer()
    }

    /**
     * Serializer responsible for encoding the result of the tool execution.
     * This abstract property is used to define the specific [KSerializer] corresponding to the type of arguments
     * expected by the tool.
     *
     * The implementation must provide a concrete serializer for the `TResult` type parameter, which ensures
     * proper serialization and deserialization of the tool arguments.
     */
    public abstract val resultSerializer: KSerializer<TResult>

    /**
     * The [StringFormat] used to encode and decode the arguments and results of the tool.
     * This property is used to serialize and deserialize the tool arguments and results.
     */
    protected open val format: StringFormat = ToolJson

    /**
     * The name of the tool.
     *
     * This property provides a descriptive name (visible to the LLM) that can be used to identify the tool.
     */
    public open val name: String by lazy {
        this::class.simpleName ?: throw IllegalStateException("Class ${this::class} doesn't have a name")
    }

    /**
     * Describes the functionality and purpose of the tool.
     *
     * This property provides a textual explanation of what the tool does and how it can be utilized (for the LLM).
     */
    public abstract val description: String

    /**
     * Provides a descriptor detailing the tool's metadata, including its name,
     * description, and parameter requirements. This property defines the structure
     * and characteristics of the tool, offering an overview of its functionality
     * and how it should be used.
     */
    @OptIn(InternalAgentToolsApi::class)
    public open val descriptor: ToolDescriptor by lazy {
        // Needs to be calculated lazily because argsSerializer from the subclass might be unavailable on initialization of the base class:
        argsSerializer.descriptor.asToolDescriptor(
            name,
            description
        )
    }

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
        @Suppress("UNCHECKED_CAST")
        return execute(args as TArgs)
    }

    /**
     * Decodes the provided raw JSON arguments into an instance of the specified arguments type.
     *
     * @param rawArgs the raw JSON object that contains the encoded arguments
     * @return the decoded arguments of type TArgs
     */
    public fun decodeArgs(rawArgs: JsonObject): TArgs = ToolJson.decodeFromJsonElement(actualArgsSerializer, rawArgs)

    /**
     * Encodes the given arguments into a JSON representation.
     *
     * @param args The arguments to be encoded.
     * @return A JsonObject representing the encoded arguments.
     */
    public fun encodeArgs(args: TArgs): JsonObject = ToolJson.encodeToJsonElement(actualArgsSerializer, args).jsonObject

    /**
     * Encodes the provided arguments into a JSON string representation using the configured serializer.
     *
     * @param args the arguments to be encoded into a JSON string
     * @return the JSON string representation of the provided arguments
     */
    public fun encodeArgsToString(args: TArgs): String = ToolJson.encodeToString(actualArgsSerializer, args)

    /**
     * Encodes the given result of type TResult to its string representation for the LLM.s
     *
     * @param result The result object of type TResult to be encoded into a string.
     * @return The string representation of the given result.
     */
    public open fun encodeResultToString(result: TResult): String = format.encodeToString(resultSerializer, result)

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
        @Suppress("UNCHECKED_CAST")
        return encodeResultToString(result as TResult)
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
