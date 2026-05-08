package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.tools.ToolCallMetadata

private const val AGENT_CONTEXT_KEY: String = "ai.koog.agents.core.AIAgentContext"

/**
 * The current [AIAgentContext] of the agent run that is invoking this tool, or `null` if the metadata
 * was not produced by the framework (for example when [ai.koog.agents.core.tools.Tool.execute] is called
 * directly outside an agent run, such as from a unit test).
 *
 * The framework injects the live agent context under a reserved namespace key after merging caller- and
 * feature-supplied entries, so the value returned here is always the real context driving the current
 * tool call. Callers and features cannot override it via the metadata bag.
 *
 * Tools that need access to the agent's full state (LLM context, run id, configuration, storage, ...)
 * should read it through this extension rather than expecting it through their argument schema:
 *
 * ```kotlin
 * override suspend fun execute(args: Args, metadata: ToolCallMetadata): Result {
 *     val runId = metadata.agentContext?.runId
 *     // ...
 * }
 * ```
 */
public val ToolCallMetadata.agentContext: AIAgentContext?
    get() = this[AGENT_CONTEXT_KEY] as? AIAgentContext

/**
 * Returns a new [ToolCallMetadata] with [context] stored under the framework's reserved key.
 *
 * Used by the framework to inject the live [AIAgentContext] before tool execution. Any prior entry under
 * the reserved key, including caller- or feature-supplied values, is overwritten so that
 * [ToolCallMetadata.agentContext] always reflects the real context driving the current tool call.
 */
internal fun ToolCallMetadata.withAgentContext(context: AIAgentContext): ToolCallMetadata =
    this + ToolCallMetadata.of(AGENT_CONTEXT_KEY to context)
