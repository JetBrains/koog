package ai.grazie.code.agents.core.agent.entity

import ai.grazie.code.agents.core.annotation.InternalAgentsApi
import ai.grazie.code.agents.core.utils.runCatchingCancellable

/**
 * Implementation of an AI agent that processes user input through a sequence of stages.
 *
 * @property name The unique identifier for this agent.
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
        // TODO move up to the agent (AIAgentBase), make everything just a graph
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
