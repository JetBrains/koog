package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.serialization.ToolJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * This interface serves as a safeguard for controlling direct tool calls from outside
 * of the AIAgent or Environment context.
 *
 * Tool calls must not be performed by a user directly as this might cause issues
 * and side-effects, such as:
 * - Missing EventHandler events
 * - Bugs with feature pipelines
 * - Inability to test/mock
 * - And other potential problems
 *
 * For this reason, all tools should be called using the environment context.
 * Consider using methods like `findTool(tool: Class)` or similar, to retrieve a
 * `SafeTool`, and then call `execute` on it. This ensures that the tool call is
 * delegated properly to the underlying `environment` object.
 *
 * @suppress
 */
@InternalAgentToolsApi
public interface DirectToolCallsEnabler

/**
 * Represents a tool that, when executed, makes changes to the environment.
 */
@Suppress("UNCHECKED_CAST", "unused")
public abstract class Tool<TArgs : ToolArgs, TResult : ToolResult> {
    /**
     * Serializer responsible for encoding and decoding the arguments required for the tool execution.
     * This abstract property is used to define the specific [KSerializer] corresponding to the type of arguments
     * expected by the tool.
     *
     * The implementation must provide a concrete serializer for the `TArgs` type parameter, which ensures
     * proper serialization and deserialization of the tool arguments.
     */
    public abstract val argsSerializer: KSerializer<TArgs>

    /**
     * Provides a descriptor detailing the tool's metadata, including its name,
     * description, and parameter requirements. This property defines the structure
     * and characteristics of the tool, offering an overview of its functionality
     * and how it should be used.
     */
    public abstract val descriptor: ToolDescriptor

    /**
     * Represents the name property of the tool, derived from the tool's descriptor.
     * This property provides an immutable reference to the tool's unique name,
     * which is used for identification within tool registries.
     */
    public val name: String get() = descriptor.name

    /**
     * Executes the tool's logic with the provided arguments.
     *
     * Tool calls must not be performed directly by a user as this might cause issues
     * and side-effects, such as:
     * - Missing EventHandler events
     * - Bugs with feature pipelines
     * - Inability to test/mock
     * - And other potential problems
     *
     * For this reason, all tools should be called using the environment context.
     * Consider using methods like `findTool(tool: Class)` or similar, to retrieve a
     * `SafeTool`, and then call `execute` on it. This ensures that the tool call is
     * delegated properly to the underlying `environment` object.
     *
     * @param args The input arguments required to execute the tool.
     * @return The result of the tool's execution.
     */
    protected abstract suspend fun execute(args: TArgs): TResult

    /**
     * Executes the tool with the provided arguments, bypassing type safety checks.
     *
     * Tool calls must not be performed directly by a user as this might cause issues
     * and side-effects, such as:
     * - Missing EventHandler events
     * - Bugs with feature pipelines
     * - Inability to test/mock
     * - And other potential problems
     *
     * For this reason, all tools should be called using the environment context.
     * Consider using methods like `findTool(tool: Class)` or similar, to retrieve a
     * `SafeTool`, and then call `execute` on it. This ensures that the tool call is
     * delegated properly to the underlying `environment` object.
     *
     * This method allows the execution of the tool using arguments of any type, casting them to the expected type internally.
     * It requires a `DirectToolCallsEnabler` to validate that the unsafe execution is intentional and properly encapsulated.
     *
     * @param args The input arguments for the tool execution, provided as a generic `Any?` type. The method attempts to cast this to the expected argument type `TArgs`.
     * @param enabler An instance of `DirectToolCallsEnabler` that authorizes this unsafe execution path. Direct calls without proper enabling are not allowed.
     * @return The result of executing the tool, as an instance of type `TResult`.
     * @throws ClassCastException if the provided arguments cannot be cast to the expected type `TArgs`.
     *
     * @suppress
     */
    @InternalAgentToolsApi
    public suspend fun executeUnsafe(args: Any?, enabler: DirectToolCallsEnabler): TResult = execute(args as TArgs)

    /**
     * Executes the tool using the provided arguments and enabler.
     *
     * Tool calls must not be performed directly by a user as this might cause issues
     * and side-effects, such as:
     * - Missing EventHandler events
     * - Bugs with feature pipelines
     * - Inability to test/mock
     * - And other potential problems
     *
     * For this reason, all tools should be called using the environment context.
     * Consider using methods like `findTool(tool: Class)` or similar, to retrieve a
     * `SafeTool`, and then call `execute` on it. This ensures that the tool call is
     * delegated properly to the underlying `environment` object.
     *
     * @param args The arguments of type TArgs that are required for the execution of the tool.
     * @param enabler An instance of DirectToolCallsEnabler that ensures direct tool calls are controlled within the proper context.
     * @return The result of type TResult produced by executing the tool with the provided arguments.
     *
     * @suppress
     */
    @InternalAgentToolsApi
    public suspend fun execute(args: TArgs, enabler: DirectToolCallsEnabler): TResult = execute(args)

    /**
     * Decodes the provided raw JSON arguments into an instance of the specified arguments type.
     *
     * @param rawArgs the raw JSON object that contains the encoded arguments
     * @return the decoded arguments of type TArgs
     */
    public fun decodeArgs(rawArgs: JsonObject): TArgs = ToolJson.decodeFromJsonElement(argsSerializer, rawArgs)

    /**
     * Encodes the given arguments into a JSON representation.
     *
     * @param args The arguments to be encoded.
     * @return A JsonObject representing the encoded arguments.
     */
    public fun encodeArgs(args: TArgs): JsonObject = ToolJson.encodeToJsonElement(argsSerializer, args).jsonObject

    /**
     * Encodes the provided arguments into a JSON string representation using the configured serializer.
     *
     * @param args the arguments to be encoded into a JSON string
     * @return the JSON string representation of the provided arguments
     */
    public fun encodeArgsToString(args: TArgs): String = ToolJson.encodeToString(argsSerializer, args)

    /**
     * Encodes the given result of type TResult to its string representation for the LLM.s
     *
     * @param result The result object of type TResult to be encoded into a string.
     * @return The string representation of the given result.
     */
    public open fun encodeResultToString(result: TResult): String = result.toStringDefault()

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
    public fun encodeResultToStringUnsafe(result: ToolResult): String = encodeResultToString(result as TResult)

    /**
     * Base type, representing tool arguments.
     */
    @Deprecated("Use ToolArgs instead", ReplaceWith("ToolArgs", "ai.koog.agents.core.tools.ToolArgs"))
    public interface Args : ToolArgs

    /**
     * Args implementation that can be used for tools that expect no arguments.
     */
    @Serializable
    @Deprecated("Use ToolArgs.Empty instead", ReplaceWith("ToolArgs.Empty", "ai.koog.agents.core.tools.ToolArgs.Empty"))
    public data object EmptyArgs : Args
}
