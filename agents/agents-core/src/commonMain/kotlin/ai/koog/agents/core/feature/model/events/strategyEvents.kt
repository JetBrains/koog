package ai.koog.agents.core.feature.model.events

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Represents an event triggered at the start of an AI agent strategy execution.
 *
 * This event captures information about the strategy being initiated, allowing
 * for tracking and analyzing the lifecycle of AI agent strategies. It provides
 * details specific to the strategy itself, such as the name, while inheriting
 * shared properties from the [DefinedFeatureEvent] superclass.
 *
 * @property strategyName The name of the strategy being started.
 * @property eventId A string representing the event type.
 */
public abstract class AIAgentStrategyStartEvent : DefinedFeatureEvent() {

    /**
     * A unique identifier associated with a specific run or execution instance of
     * an AI agent strategy. This identifier is used across various related events
     * to track and correlate the lifecycle of a single execution instance, enabling
     * monitoring, debugging, and analysis of the strategy execution flow.
     */
    public abstract val runId: String

    /**
     * The name of the AI agent strategy being initiated.
     *
     * This property specifies the identifier or title of the strategy that is
     * starting execution. It is used to provide descriptive context about the
     * strategy being launched, enabling effective monitoring, debugging, and
     * analysis of AI agent execution processes.
     */
    public abstract val strategyName: String

    override val eventId: String = AIAgentStrategyStartEvent::class.simpleName!!
}

/**
 * Represents an event triggered at the start of an AI agent strategy execution that involves
 * the use of a graph-based operational model.
 *
 * This event extends the functionality of the `AIAgentStrategyStartEvent` class, providing additional
 * details specific to graph-based strategies. It captures the graph structure used by the strategy,
 * enabling effective representation and understanding of the execution flow and dependency relationships
 * between various processing nodes within the graph.
 *
 * The `AIAgentGraphStrategyStartEvent` is particularly useful for monitoring, debugging, and analyzing AI
 * agents that employ graph-like structures for their workflows.
 *
 * @property runId A unique identifier representing the specific run or instance of the strategy execution.
 * @property strategyName The name of the graph-based strategy being executed.
 * @property graph The graph structure representing the strategy's execution workflow, encompassing nodes
 *                 and their directed relationships;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AIAgentGraphStrategyStartEvent(
    override val runId: String,
    override val strategyName: String,
    val graph: AIAgentEventGraph,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : AIAgentStrategyStartEvent()

/**
 * Represents an event triggered at the start of executing a functional strategy by an AI agent.
 *
 * This event provides specific information about the initiation of a functional strategy,
 * including the unique identifier of the run and the strategy name. It is intended to
 * support monitoring, debugging, and tracking of functional strategy execution within
 * the lifecycle of an AI agent's processes. This class extends [AIAgentStrategyStartEvent],
 * inheriting shared properties and behavior for strategy execution events.
 *
 * @property runId A unique identifier representing the specific run or instance of the strategy execution;
 * @property strategyName The name of the functional-based strategy being executed;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AIAgentFunctionalStrategyStartEvent(
    override val runId: String,
    override val strategyName: String,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : AIAgentStrategyStartEvent()

/**
 * Event that represents the completion of an AI agent's strategy execution.
 *
 * This event captures information about the strategy that was executed and the result of its execution.
 * It is used to notify the system or consumers about the conclusion of a specific strategy.
 *
 * @property strategyName The name of the strategy that was executed;
 * @property result The result of the strategy execution, providing details such as success, failure,
 *           or other status descriptions;
 * @property eventId A string representing the event type;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AIAgentStrategyFinishedEvent(
    val runId: String,
    val strategyName: String,
    val result: String?,
    override val eventId: String = AIAgentStrategyFinishedEvent::class.simpleName!!,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent()

/**
 * Represents a graph structure used by an AI agent, consisting of a collection
 * of nodes and directed edges connecting these nodes.
 *
 * Each node encapsulates processing logic with specified input and output types.
 * The edges define directed relationships between nodes, indicating the flow of
 * data and execution order within the graph.
 *
 * This class is designed to model and manage the execution structure of an AI agent's
 * workflow, where each node represents a distinct computational or processing step,
 * and edges define dependencies between these steps.
 *
 * @property nodes A list of nodes in the graph where each node represents a specific
 *                 processing unit with defined input and output types;
 * @property edges A list of directed edges that define the relationships and data
 *                 flow between nodes in the graph.
 */
@Serializable
public data class AIAgentEventGraph(
    val nodes: List<AIAgentEventGraphNode>,
    val edges: List<AIAgentEventGraphEdge>
)

/**
 * Represents a node within an AI agent's processing graph.
 *
 * Each node in the graph is associated with an identifier, a name, an expected input type,
 * and an expected output type. These nodes are components that process or transform data
 * within an AI agent system, contributing to the overall workflow of the agent.
 *
 * @property id The unique identifier of the node within the graph;
 * @property name The descriptive name of the node.
 */
@Serializable
public data class AIAgentEventGraphNode(
    val id: String,
    val name: String
)

/**
 * Represents a directed edge in the AI agent graph.
 *
 * This class models the relationship or connection between two nodes in the graph structure
 * by specifying the source and target node identifiers. It is used to construct and represent
 * the flow or dependencies between various components or processes of an AI agent.
 *
 * @property sourceNode The unique identifier of the source node in the graph;
 * @property targetNode The unique identifier of the target node in the graph.
 */
@Serializable
public data class AIAgentEventGraphEdge(
    val sourceNode: AIAgentEventGraphNode,
    val targetNode: AIAgentEventGraphNode
)

/**
 * Constructs an instance of `AIAgentEventGraph` by converting the metadata information
 * of the current `AIAgentGraphStrategy` into its graph representation. The method creates
 * nodes and edges that define the structure and flow of execution for the underlying AI agent strategy.
 *
 * The nodes and edges are derived from the registered subgraph metadata, which contains information
 * about the connected components of the strategy.
 *
 * @return An instance of `AIAgentEventGraph` representing the strategy's node-to-node connections
 *         in a graph format.
 */
@InternalAgentsApi
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.startNodeToGraph(): AIAgentEventGraph {
    val nodes = this.metadata.nodesMap.values

    // Filter out the strategy node as it is not relevant for strategy graph nodes
    val nodesWithoutStrategyNode = nodes.filter { node -> node.id != this.id }

    val graphEdges = mutableListOf<AIAgentEventGraphEdge>()
    val graphNodes = mutableListOf<AIAgentEventGraphNode>()

    val startGraphNode = AIAgentEventGraphNode(id = "__start__", name = "__start__")
    val finishGraphNode = AIAgentEventGraphNode(id = "__finish__", name = "__finish__")

    // Starting node
    graphNodes.add(startGraphNode)

    nodesWithoutStrategyNode.forEach { node ->
        // Node
        val graphNode = AIAgentEventGraphNode(
            id = node.id,
            name = node.name
        )
        graphNodes.add(graphNode)

        // Edge
        node.edges.forEach { edge ->
            val targetNode = AIAgentEventGraphNode(
                id = edge.toNode.id,
                name = edge.toNode.name
            )

            graphEdges.add(
                AIAgentEventGraphEdge(
                    sourceNode = graphNode,
                    targetNode = targetNode
                )
            )
        }
    }

    // Closing node
    graphNodes.add(finishGraphNode)

    // Link initial node with start node
    graphEdges.add(
        index = 0,
        element = AIAgentEventGraphEdge(startGraphNode, graphNodes[1]) // Ignore the initial start node
    )

    // Graph
    val graph = AIAgentEventGraph(graphNodes, graphEdges)

    return graph
}
