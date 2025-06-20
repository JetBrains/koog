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
public class AIAgentStrategy(
    override val name: String,
    public val nodeStart: StartAIAgentNodeBase<String>,
    public val nodeFinish: FinishAIAgentNodeBase<String>,
    public val nodeMap: Map<String, AIAgentNodeBase<*, *>>,
    toolSelectionStrategy: ToolSelectionStrategy
) : AIAgentSubgraph<String, String>(
    name, nodeStart, nodeFinish, toolSelectionStrategy
) {
    @OptIn(InternalAgentsApi::class)
    override suspend fun execute(context: AIAgentContextBase, input: String): NodeExecutionResult<String> {
        return runCatchingCancellable {
            context.pipeline.onStrategyStarted(this, context)
            val result = super.execute(context, input)
            if (result !is NodeExecutionSuccess<String>) {
                return result
            }
            context.pipeline.onStrategyFinished(this, context, result.result)
            result
        }.onSuccess {
            context.environment.sendTermination(it.result)
        }.onFailure {
            context.environment.reportProblem(it)
        }.getOrThrow()
    }

    /**
     * Finds and sets the node for the strategy based on the provided context.
     */
    public fun findAndSetNode(nodeId: String) {
        val fullPath = nodeMap.keys.firstOrNull { it.endsWith(nodeId) } ?: return
        val segments = fullPath.split(":")
        if (segments.isEmpty())
            throw IllegalArgumentException("Invalid node path: $fullPath")

        val strategyName = segments.firstOrNull() ?: return
        var currentNode: AIAgentNodeBase<*, *>? = nodeMap[strategyName]

        for (segment in segments.drop(1)) {
            currentNode as? HasSubnodes ?: throw IllegalStateException("Node ${currentNode?.name} does not have subnodes")
            val nextNode = currentNode.edges.firstOrNull { it.toNode.name == segment }?.toNode
            if (nextNode is HasSubnodes) {
                currentNode.enforceNode(nextNode)
                currentNode = nextNode
            }
        }
    }
}
