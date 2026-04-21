@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.AgentContextData
import ai.koog.agents.core.agent.context.getAgentContextData
import ai.koog.agents.core.agent.context.removeAgentContextData
import ai.koog.agents.core.agent.execution.DEFAULT_AGENT_PATH_SEPARATOR
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.serialization.JSONElement
import ai.koog.serialization.kotlinx.toKoogJSONElement
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Represents a strategy for managing and executing AI agent workflows built as subgraphs of interconnected nodes.
 *
 * @property name The unique identifier for the strategy.
 * @property nodeStart The starting node of the strategy, initiating the subgraph execution.
 * By default, the start node gets the agent input and returns
 * @property nodeFinish The finishing node of the strategy, marking the subgraph's endpoint.
 * @property toolSelectionStrategy The strategy responsible for determining the toolset available during subgraph execution.
 */
public expect class AIAgentGraphStrategy<TInput, TOutput>(
    name: String,
    nodeStart: StartNode<TInput>,
    nodeFinish: FinishNode<TOutput>,
    toolSelectionStrategy: ToolSelectionStrategy,
    serializer: Json = Json { prettyPrint = true }
) : AIAgentGraphStrategyBase<TInput, TOutput>

/**
 * Base class for [AIAgentStrategy].
 *
 * @property name The unique identifier for the strategy.
 * @property nodeStart The starting node of the strategy, initiating the subgraph execution.
 * By default, the start node gets the agent input and returns
 * @property nodeFinish The finishing node of the strategy, marking the subgraph's endpoint.
 * @property toolSelectionStrategy The strategy responsible for determining the toolset available during subgraph execution.
 */
public open class AIAgentGraphStrategyBase<TInput, TOutput>(
    override val name: String,
    public val nodeStart: StartNode<TInput>,
    public val nodeFinish: FinishNode<TOutput>,
    toolSelectionStrategy: ToolSelectionStrategy,
    private val serializer: Json = Json { prettyPrint = true }
) : AIAgentStrategy<TInput, TOutput, AIAgentGraphContextBase>, AIAgentSubgraphBase<TInput, TOutput>(
    name,
    nodeStart,
    nodeFinish,
    toolSelectionStrategy
) {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

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
        restoreStateIfNeeded(context)

        var result: TOutput? = super.execute(context = context, input = input)

        while (result == null && context.getAgentContextData() != null) {
            restoreStateIfNeeded(context)
            result = super.execute(context = context, input = input)
        }

        return result
    }

    @OptIn(InternalAgentsApi::class)
    private suspend fun restoreStateIfNeeded(
        agentContext: AIAgentGraphContextBase
    ) {
        val additionalContextData: AgentContextData = agentContext.getAgentContextData() ?: return

        restoreDefault(agentContext, additionalContextData)
        agentContext.removeAgentContextData()
    }

    @OptIn(InternalAgentsApi::class)
    private suspend fun restoreMessageOnly(agentContext: AIAgentContext, data: AgentContextData) {
        agentContext.llm.withPrompt {
            this.withMessages { (data.messageHistory) }
        }
    }

    @OptIn(InternalAgentsApi::class)
    private suspend fun restoreDefault(agentContext: AIAgentGraphContextBase, data: AgentContextData) {
        val nodePath = data.nodePath

        // Perform additional cleanup (ex: rollback tools):
        data.additionalRollbackActions(agentContext)

        // Set current graph node:
        @Suppress("DEPRECATION")
        when {
            data.lastInput != null -> setExecutionPoint(nodePath, data.lastInput, agentContext)
            data.lastOutput != null -> setExecutionPointAfterNode(nodePath, data.lastOutput, agentContext)

            // Unexpected state, either input (before 0.6.1) or output (since 0.6.1) should be saved in checkpoints:
            else -> {}
        }

        // Reset the message history:
        agentContext.llm.withPrompt {
            this.withMessages { (data.messageHistory) }
        }
    }

    private fun setExecutionPointImpl(pathSegments: List<String>, node: AIAgentNodeBase<*, *>, input: Any?) {
        val strategyName = pathSegments.firstOrNull() ?: return

        // getting the very first segment (it should be a root strategy node)
        var currentNode: AIAgentNodeBase<*, *>? = metadata.nodesMap[strategyName]
        var currentPath = strategyName

        // restoring the current node for each subgraph including strategy
        val segmentsInbetween = pathSegments.drop(1).dropLast(1)
        for (segment in segmentsInbetween) {
            val currNode = currentNode as? ExecutionPointNode
                ?: throw IllegalStateException(
                    "Restore for path " +
                        "${pathSegments.joinToString(DEFAULT_AGENT_PATH_SEPARATOR)} failed: " +
                        "one of middle segments is not a subgraph"
                )

            currentPath = "$currentPath${DEFAULT_AGENT_PATH_SEPARATOR}$segment"
            val nextNode = metadata.nodesMap[currentPath]
            if (nextNode is ExecutionPointNode) {
                currNode.enforceExecutionPoint(nextNode, input)
                currentNode = nextNode
            }
        }

        val leaf = node
        node.let {
            currentNode as? ExecutionPointNode
                ?: throw IllegalStateException("Node ${currentNode?.name} is not a valid leaf node")
            currentNode.enforceExecutionPoint(it, input)
        }
    }

    /**
     * Finds and sets the node for the strategy based on the provided context.
     */
    @Deprecated("Use setExecutionPointAfterNode instead, setExecutionPoint will be removed in future versions")
    public suspend fun setExecutionPoint(
        nodePath: String,
        input: JSONElement,
        agentContext: AIAgentGraphContextBase,
    ) {
        // we drop first because it's agent's id, we don't need it here
        val segments = nodePath.split(DEFAULT_AGENT_PATH_SEPARATOR).drop(1)

        if (segments.isEmpty()) {
            throw IllegalArgumentException("Invalid node path: $nodePath")
        }

        val actualPath = segments.joinToString(DEFAULT_AGENT_PATH_SEPARATOR)

        val completedNode = metadata.nodesMap[actualPath] ?: throw IllegalStateException("Node $actualPath not found")

        val actualInput = agentContext.config.serializer
            .decodeFromJSONElement<Any?>(input, completedNode.inputType)

        // Note: completed node will be re-executed because the output wasn't saved in checkpoints
        // (this was the original behavior before 0.6.1)
        setExecutionPointImpl(segments, completedNode, actualInput)
    }

    /**
     * Finds and sets the node for the strategy based on the provided context.
     */
    @Deprecated("Use setExecutionPointAfterNode with output: JSONElement instead")
    public suspend fun setExecutionPointAfterNode(
        nodePath: String,
        output: JsonElement,
        agentContext: AIAgentGraphContextBase
    ) {
        setExecutionPointAfterNode(nodePath, output.toKoogJSONElement(), agentContext)
    }

    /**
     * Finds and sets the node for the strategy based on the provided context.
     */
    public suspend fun setExecutionPointAfterNode(
        nodePath: String,
        output: JSONElement,
        agentContext: AIAgentGraphContextBase
    ) {
        // we drop first because it's agent's id, we don't need it here
        val segments = nodePath.split(DEFAULT_AGENT_PATH_SEPARATOR).drop(1)

        if (segments.isEmpty()) {
            throw IllegalArgumentException("Invalid node path: $nodePath")
        }

        val actualPath = segments.joinToString(DEFAULT_AGENT_PATH_SEPARATOR)

        val completedNode = metadata.nodesMap[actualPath] ?: throw IllegalStateException("Node $actualPath not found")

        val actualOutput = agentContext.config.serializer
            .decodeFromJSONElement<Any?>(output, completedNode.outputType)

        if (completedNode is FinishNode<*>) {
            // finish node (of some subgraph) doesn't have next edges, and it's input equals output, so it's safe to re-start it:
            setExecutionPointImpl(
                pathSegments = segments,
                node = completedNode,
                input = actualOutput
            )
        } else {
            val resolvedEdge = completedNode.resolveEdgeUnsafe(agentContext, actualOutput)
            val nextNode = resolvedEdge?.edge?.toNode ?: throw IllegalStateException("Node $nodePath not found")
            val nextNodeInput = resolvedEdge.output

            setExecutionPointImpl(
                pathSegments = segments.dropLast(1) + nextNode.name,
                node = nextNode,
                input = nextNodeInput
            )
        }
    }
}
