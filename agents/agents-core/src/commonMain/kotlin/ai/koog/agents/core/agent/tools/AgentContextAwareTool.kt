package ai.koog.agents.core.agent.tools

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.schema.defaultJsonSchemaConfig
import ai.koog.agents.core.tools.schema.getToolDescriptor
import ai.koog.serialization.TypeToken
import ai.koog.serialization.annotations.InternalKoogSerializationApi
import kotlinx.schema.generator.json.JsonSchemaConfig

/**
 * A tool whose [execute] receives the live [AIAgentContext] driving the current call.
 *
 * The framework injects the context under [AgentContextKey] in [ToolCallMetadata] before dispatch
 * (see [ai.koog.agents.core.environment.ContextualAgentEnvironment]). This subclass reads that
 * entry and forwards it to the user-facing overload, so implementors get a typed
 * `AIAgentContext` parameter rather than a `Map` lookup.
 *
 * Use this when a tool needs the agent's full state (LLM context, run id, configuration, storage,
 * ...). For tools that only need the typed arguments, extend [Tool] instead; for tools that read
 * raw [ToolCallMetadata] entries contributed by features (for example a trace span id), extend
 * [ToolBase] directly.
 *
 * Invoking an `AgentContextAwareTool` outside an agent run is a programming error: with no
 * [AgentContextKey] entry in the metadata, [execute] throws [IllegalStateException] naming the
 * tool. Tests that need to exercise such a tool can construct metadata via
 * `ToolCallMetadata.of(AgentContextAwareTool.AgentContextKey to context)` or call
 * `execute(args, context)` directly.
 *
 * @param TArgs The type of arguments the tool accepts.
 * @param TResult The type of result the tool returns.
 */
public abstract class AgentContextAwareTool<TArgs, TResult>(
    argsType: TypeToken,
    resultType: TypeToken,
    descriptor: ToolDescriptor,
    metadata: Map<String, String> = emptyMap(),
) : ToolBase<TArgs, TResult>(argsType, resultType, descriptor, metadata) {

    /**
     * Convenience constructor that generates [ToolDescriptor] from the provided
     * [name], [description] and [argsType].
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
     * Executes the tool's logic with the provided arguments and the live [AIAgentContext] driving
     * the current call.
     *
     * @param args The input arguments required to execute the tool.
     * @param context The agent context (LLM context, run id, configuration, storage, ...).
     * @return The result of the tool's execution.
     */
    public abstract suspend fun execute(args: TArgs, context: AIAgentContext): TResult

    final override suspend fun execute(args: TArgs, metadata: ToolCallMetadata): TResult {
        val context = metadata[AgentContextKey] as? AIAgentContext
            ?: throw IllegalStateException(
                "AgentContextAwareTool '$name' was invoked without an AIAgentContext. " +
                    "Tools of this type must execute inside an agent run; the framework injects " +
                    "the context via ContextualAgentEnvironment before dispatch."
            )
        return execute(args, context)
    }

    public companion object {
        /**
         * Reserved [ToolCallMetadata] key under which the framework stores the live
         * [AIAgentContext] before dispatching a tool. The framework writes this entry last in the
         * metadata merge, so caller- and feature-supplied values never override it.
         */
        public const val AgentContextKey: String = "ai.koog.agents.core.agentContext"
    }
}

/**
 * The [AIAgentContext] of the agent run that is invoking this tool, or `null` if the metadata was
 * not produced by the framework (for example when [ToolBase.execute] is called directly outside an
 * agent run, such as from a unit test).
 *
 * The framework injects the live agent context under [AgentContextAwareTool.AgentContextKey] after
 * merging caller- and feature-supplied entries, so the value here is always the real context
 * driving the current tool call. Tools that extend [AgentContextAwareTool] receive that context as
 * a typed parameter; this extension is the ergonomic accessor for tools that extend [ToolBase]
 * directly and want both the raw metadata bag and the context.
 */
public val ToolCallMetadata.agentContext: AIAgentContext?
    get() = this[AgentContextAwareTool.AgentContextKey] as? AIAgentContext
