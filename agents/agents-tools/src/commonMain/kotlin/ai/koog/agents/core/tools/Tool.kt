package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.schema.defaultJsonSchemaConfig
import ai.koog.agents.core.tools.schema.getToolDescriptor
import ai.koog.serialization.KSerializerTypeToken
import ai.koog.serialization.TypeToken
import ai.koog.serialization.annotations.InternalKoogSerializationApi
import kotlinx.schema.generator.json.JsonSchemaConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * A tool with a no-metadata [execute] shape. The runtime dispatches it through
 * [ToolBase.execute]; this subclass's final override forwards to [execute] and discards any per-call
 * [ToolCallMetadata] supplied by the caller or contributed by features.
 *
 * Existing tool implementations subclass this type. Implementations that need typed access to the
 * live `AIAgentContext` should extend
 * [ai.koog.agents.core.agent.tools.AgentContextAwareTool] (in `agents-core`) instead; implementations
 * that need raw [ToolCallMetadata] entries (for example a tracing span id contributed by a feature)
 * should extend [ToolBase] directly.
 *
 * @param TArgs The type of arguments the tool accepts.
 * @param TResult The type of result the tool returns.
 */
public abstract class Tool<TArgs, TResult>(
    argsType: TypeToken,
    resultType: TypeToken,
    descriptor: ToolDescriptor,
    metadata: Map<String, String> = emptyMap(),
) : ToolBase<TArgs, TResult>(argsType, resultType, descriptor, metadata) {

    /**
     * Convenience constructor that generates [ToolDescriptor] from the provided
     * [name], [description] and [argsType].
     */
    @OptIn(InternalAgentToolsApi::class)
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
     * @return The result of the tool's execution.
     */
    public abstract suspend fun execute(args: TArgs): TResult

    final override suspend fun execute(args: TArgs, metadata: ToolCallMetadata): TResult =
        execute(args)

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
}
