package ai.koog.agents.core.feature.model.events

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Represents an event triggered at the start of an AI agent strategy execution.
 *
 * @property strategyName The name of the strategy being started.
 */
public abstract class StrategyStartingEvent : DefinedFeatureEvent() {

    /**
     * A unique identifier associated with a specific run.
     */
    public abstract val runId: String

    /**
     * The name of the AI agent strategy being initiated.
     */
    public abstract val strategyName: String
}

/**
 * Represents an event triggered at the start of an AI agent strategy execution that involves
 * the use of a graph-based operational model.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property runId A unique identifier representing the specific run or instance of the strategy execution;
 * @property strategyName The name of the graph-based strategy being executed;
 * @property graph The graph structure representing the strategy's execution workflow, encompassing nodes and their directed relationships;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class GraphStrategyStartingEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val runId: String,
    override val strategyName: String,
    val graph: StrategyEventGraph,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : StrategyStartingEvent() {

    /**
     * @deprecated Use constructor with [executionInfo] parameter
     */
    @Deprecated(
        message = "Use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("GraphStrategyStartingEvent(executionInfo, runId, strategyName, graph, timestamp)")
    )
    public constructor(
        runId: String,
        strategyName: String,
        graph: StrategyEventGraph,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(
        eventId = GraphStrategyStartingEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = GraphStrategyStartingEvent::class.simpleName.toString(),
        ),
        runId = runId,
        strategyName = strategyName,
        graph = graph,
        timestamp = timestamp
    )
}

/**
 * Represents an event triggered at the start of executing a functional strategy by an AI agent.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property runId A unique identifier representing the specific run or instance of the strategy execution;
 * @property strategyName The name of the functional-based strategy being executed;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class FunctionalStrategyStartingEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val runId: String,
    override val strategyName: String,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : StrategyStartingEvent() {

    /**
     * @deprecated Use constructor with [executionInfo] parameter
     */
    @Deprecated(
        message = "Use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("FunctionalStrategyStartingEvent(executionInfo, runId, strategyName, timestamp)")
    )
    public constructor(
        runId: String,
        strategyName: String,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(
        eventId = FunctionalStrategyStartingEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = FunctionalStrategyStartingEvent::class.simpleName.toString(),
        ),
        runId = runId,
        strategyName = strategyName,
        timestamp = timestamp
    )
}

/**
 * Event that represents the completion of an AI agent's strategy execution.
 *
 * This event captures information about the strategy that was executed and the result of its execution.
 * It is used to notify the system or consumers about the conclusion of a specific strategy.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property runId A unique identifier representing the specific run or instance of the strategy execution;
 * @property strategyName The name of the strategy that was executed;
 * @property result The result of the strategy execution, providing details such as success, failure, or other status descriptions;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class StrategyCompletedEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val strategyName: String,
    val result: String?,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated Use constructor with [executionInfo] parameter
     */
    @Deprecated(
        message = "Use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("StrategyCompletedEvent(executionInfo, runId, strategyName, result, timestamp)")
    )
    public constructor(
        runId: String,
        strategyName: String,
        result: String?,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(
        eventId = StrategyCompletedEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = StrategyCompletedEvent::class.simpleName.toString(),
        ),
        runId = runId,
        strategyName = strategyName,
        result = result,
        timestamp = timestamp
    )
}

/**
 * Represents a graph structure used by an AI agent, consisting of a collection
 * of nodes and directed edges connecting these nodes.
 *
 * Each node encapsulates processing logic with specified input and output types.
 * The edges define directed relationships between nodes, indicating the flow of
 * data and execution order within the graph.
 *
 * @property nodes A list of nodes in the graph where each node represents a specific
 *                 processing unit with defined input and output types;
 * @property edges A list of directed edges that define the relationships and data
 *                 flow between nodes in the graph.
 */
@Serializable
public data class StrategyEventGraph(
    val nodes: List<StrategyEventGraphNode>,
    val edges: List<StrategyEventGraphEdge>
)

/**
 * Represents a node within an AI agent's processing graph.
 *
 * @property id The unique identifier of the node within the graph;
 * @property name The descriptive name of the node.
 */
@Serializable
public data class StrategyEventGraphNode(
    val id: String,
    val name: String
)

/**
 * Represents a directed edge in the AI agent graph.
 *
 * @property sourceNode The unique identifier of the source node in the graph;
 * @property targetNode The unique identifier of the target node in the graph.
 */
@Serializable
public data class StrategyEventGraphEdge(
    val sourceNode: StrategyEventGraphNode,
    val targetNode: StrategyEventGraphNode
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
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.startNodeToGraph(): StrategyEventGraph {
    val nodes = this.metadata.nodesMap.values

    // Filter out the strategy node as it is not relevant for strategy graph nodes
    val nodesWithoutStrategyNode = nodes.filter { node -> node.id != this.id }

    val graphEdges = mutableListOf<StrategyEventGraphEdge>()
    val graphNodes = mutableListOf<StrategyEventGraphNode>()

    val startGraphNode = StrategyEventGraphNode(id = "__start__", name = "__start__")
    val finishGraphNode = StrategyEventGraphNode(id = "__finish__", name = "__finish__")

    // Starting node
    graphNodes.add(startGraphNode)

    nodesWithoutStrategyNode.forEach { node ->
        // Node
        val graphNode = StrategyEventGraphNode(
            id = node.id,
            name = node.name
        )
        graphNodes.add(graphNode)

        // Edge
        node.edges.forEach { edge ->
            val targetNode = StrategyEventGraphNode(
                id = edge.toNode.id,
                name = edge.toNode.name
            )

            graphEdges.add(
                StrategyEventGraphEdge(
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
        element = StrategyEventGraphEdge(startGraphNode, graphNodes[1]) // Ignore the initial start node
    )

    // Graph
    val graph = StrategyEventGraph(graphNodes, graphEdges)

    return graph
}

//region Deprecated

@Deprecated(
    message = "Use StrategyStartingEvent instead or one of particular methods like GraphStrategyStartingEvent or FunctionalStrategyStartingEvent",
    replaceWith = ReplaceWith("StrategyStartingEvent")
)
public typealias AIAgentStrategyStartEvent = StrategyStartingEvent

@Deprecated(
    message = "Use StrategyCompletedEvent instead",
    replaceWith = ReplaceWith("StrategyCompletedEvent")
)
public typealias AIAgentStrategyFinishedEvent = StrategyCompletedEvent

//endregion Deprecated
