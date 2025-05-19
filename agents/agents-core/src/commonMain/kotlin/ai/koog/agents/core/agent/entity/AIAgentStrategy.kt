package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.runCatchingCancellable

/**
 * Represents a strategy for managing and executing AI agent workflows built as subgraphs of interconnected nodes.
 *
 * @property name The unique identifier for the strategy.
 * @property nodeStart The starting node of the strategy, initiating the subgraph execution. By default Start node gets the agent input and returns
 * @property nodeFinish The finishing node of the strategy, marking the subgraph's endpoint.
 * @property toolSelectionStrategy The strategy responsible for determining the toolset available during subgraph execution.
 */
@OptIn(InternalAgentsApi::class)
public class AIAgentStrategy(
    override val name: String,
    public val nodeStart: StartAIAgentNodeBase<String>,
    public val nodeFinish: FinishAIAgentNodeBase<String>,
    toolSelectionStrategy: ToolSelectionStrategy
) : AIAgentSubgraph<String, String>(
    name, nodeStart, nodeFinish, toolSelectionStrategy
) {

    override suspend fun execute(context: AIAgentContextBase, input: String): String {
        return runCatchingCancellable {
            context.pipeline.onStrategyStarted(this)
            val result = super.execute(context, input)
            context.pipeline.onStrategyFinished(name, result)
            result
        }.onSuccess {
            context.environment.sendTermination(it)
        }.onFailure {
            context.environment.reportProblem(it)
        }.getOrThrow()
    }
}
