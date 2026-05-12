package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.schema.defaultJsonSchemaConfig
import ai.koog.agents.core.tools.schema.getToolDescriptor
import ai.koog.serialization.TypeToken
import kotlinx.schema.generator.json.JsonSchemaConfig

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
}
