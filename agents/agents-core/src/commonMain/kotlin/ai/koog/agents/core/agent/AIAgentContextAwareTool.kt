package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.tools.Tool

/**
 * Opt-in interface for tools that need access to the current [AIAgentContext] during execution.
 *
 * Implement this interface alongside extending [Tool] when a tool requires information from the
 * surrounding agent run — for example the parent agent's [AIAgentContext.agentId] / [AIAgentContext.runId],
 * its [AIAgentContext.llm], [AIAgentContext.storage], or [AIAgentContext.pipeline]. The framework
 * dispatches to [execute] (this method) when the surrounding environment knows the context, and
 * falls back to [Tool.execute] otherwise.
 *
 * Because [Tool.execute] is `abstract`, an implementation must still provide a stub for it.
 * The conventional stub is `error(...)`, since it should never be called for context-aware tools
 * once the framework dispatch is in place.
 *
 * Example:
 * ```
 * class MyTool : Tool<MyArgs, MyResult>(...), AIAgentContextAwareTool<MyArgs, MyResult> {
 *     override suspend fun execute(args: MyArgs): MyResult =
 *         error("Use the context-aware overload")
 *
 *     override suspend fun execute(args: MyArgs, context: AIAgentContext): MyResult = ...
 * }
 * ```
 *
 * @param TArgs The type of arguments the tool accepts.
 * @param TResult The type of result the tool returns.
 */
public interface AIAgentContextAwareTool<TArgs, TResult> {
    /**
     * Executes the tool's logic with the provided [args] and the surrounding [context] of the
     * current agent run.
     */
    public suspend fun execute(args: TArgs, context: AIAgentContext): TResult
}
