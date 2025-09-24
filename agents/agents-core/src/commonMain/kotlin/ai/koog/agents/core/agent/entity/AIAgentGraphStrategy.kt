package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.AgentContextData
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.agent.context.getAgentContextData
import ai.koog.agents.core.agent.context.removeAgentContextData
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.runCatchingCancellable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

/**
 * Represents a strategy for managing and executing AI agent workflows built as subgraphs of interconnected nodes.
 *
 * @property name The unique identifier for the strategy.
 * @property nodeStart The starting node of the strategy, initiating the subgraph execution.
 * By default, the start node gets the agent input and returns
 * @property nodeFinish The finishing node of the strategy, marking the subgraph's endpoint.
 * @property toolSelectionStrategy The strategy responsible for determining the toolset available during subgraph execution.
 */
public class AIAgentGraphStrategy<TInput, TOutput>(
    override val name: String,
    public val nodeStart: StartNode<TInput>,
    public val nodeFinish: FinishNode<TOutput>,
    toolSelectionStrategy: ToolSelectionStrategy,
    private val serializer: Json = Json { prettyPrint = true }
) : AIAgentStrategy<TInput, TOutput, AIAgentGraphContextBase>, AIAgentSubgraph<TInput, TOutput>(
    name,
    nodeStart,
    nodeFinish,
    toolSelectionStrategy
) {
    /**
     * Represents the metadata of the subgraph associated with the AI agent strategy.
     *
     * This variable holds essential information about the structure and properties of the
     * subgraph, such as the mapping of node names to their associated implementations and
     * the uniqueness of node names within the subgraph.
     *
     * This property can only be set internally, and an attempt to access it before initialization
     * will result in an `IllegalStateException`.
     */
    public lateinit var metadata: SubgraphMetadata

    @OptIn(InternalAgentsApi::class)
    override suspend fun execute(context: AIAgentGraphContextBase, input: TInput): TOutput? {
        return runCatchingCancellable {
            context.pipeline.onStrategyStarted(this, context)
            restoreStateIfNeeded(context)

            var result: TOutput? = super.execute(context = context, input = input)

            while (result == null && context.getAgentContextData() != null) {
                restoreStateIfNeeded(context)
                result = super.execute(context = context, input = input)
            }

            context.pipeline.onStrategyFinished(this, context, result, outputType)
            result
        }.onFailure {
            context.environment.reportProblem(it)
        }.getOrThrow()
    }

    @OptIn(InternalAgentsApi::class)
    private suspend fun restoreStateIfNeeded(
        agentContext: AIAgentGraphContextBase
    ) {
        val additionalContextData: AgentContextData = agentContext.getAgentContextData() ?: return

        when (additionalContextData.rollbackStrategy) {
            RollbackStrategy.Default -> restoreDefault(agentContext, additionalContextData)
            RollbackStrategy.MessageHistoryOnly -> restoreMessageOnly(agentContext, additionalContextData)
        }
        agentContext.removeAgentContextData()
    }

    @OptIn(InternalAgentsApi::class)
    private suspend fun restoreMessageOnly(agentContext: AIAgentGraphContextBase, data: AgentContextData) {
        agentContext.llm.withPrompt {
            this.withMessages { (data.messageHistory) }
        }
    }

    @OptIn(InternalAgentsApi::class)
    private suspend fun restoreDefault(agentContext: AIAgentGraphContextBase, data: AgentContextData) {
        val nodeId = data.nodeId
        setExecutionPoint(nodeId, data.lastInput)

        agentContext.llm.withPrompt {
            this.withMessages { (data.messageHistory) }
        }
    }

    /**
     * Finds and sets the node for the strategy based on the provided context.
     */
    public fun setExecutionPoint(nodeId: String, input: JsonElement) {
        val fullPath = metadata.nodesMap.keys.firstOrNull {
            val segments = it.split(":")
            segments.last() == nodeId
        } ?: throw IllegalArgumentException("Node $nodeId not found")

        val segments = fullPath.split(":")
        if (segments.isEmpty()) {
            throw IllegalArgumentException("Invalid node path: $fullPath")
        }

        val strategyName = segments.firstOrNull() ?: return

        // getting the very first segment (it should be a root strategy node)
        var currentNode: AIAgentNodeBase<*, *>? = metadata.nodesMap[strategyName]
        var currentPath = strategyName

        // restoring the current node for each subgraph including strategy
        val segmentsInbetween = segments.drop(1).dropLast(1)
        for (segment in segmentsInbetween) {
            currentNode as? ExecutionPointNode
                ?: throw IllegalStateException("Node ${currentNode?.name} does not have subnodes")

            currentPath = "$currentPath:$segment"
            val nextNode = metadata.nodesMap[currentPath]
            if (nextNode is ExecutionPointNode) {
                currentNode.enforceExecutionPoint(nextNode, input)
                currentNode = nextNode
            }
        }

        // forcing the very last segment to the latest pre-leaf node to complete the chain
        val leaf = metadata.nodesMap[fullPath] ?: throw IllegalStateException("Node ${segments.last()} not found")
        val inputType = leaf.inputType

        val actualInput = serializer.decodeFromJsonElement(serializer.serializersModule.serializer(inputType), input)
        leaf.let {
            currentNode as? ExecutionPointNode
                ?: throw IllegalStateException("Node ${currentNode?.name} does not have subnodes")
            currentNode.enforceExecutionPoint(it, actualInput)
        }
    }
}
