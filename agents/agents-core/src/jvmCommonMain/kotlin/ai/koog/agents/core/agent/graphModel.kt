package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentEdge
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraphBase
import ai.koog.agents.core.agent.entity.FinishNode
import ai.koog.agents.core.agent.entity.StartNode

/**
 * The type of a node in the diagram model.
 */
internal enum class NodeType { START, FINISH, REGULAR, SUBGRAPH }

/**
 * Lightweight representation of a graph node for diagram generation.
 * Decoupled from framework types so the data model and rendering are independent.
 */
internal data class NodeInfo(
    val id: String,
    val name: String,
    val type: NodeType,
)

/**
 * Data class representing collected graph information for diagram generation.
 */
internal data class GraphData(
    val title: String,
    val nodes: Map<String, NodeInfo>,
    val edges: List<EdgeInfo>,
    val subgraphs: List<SubgraphGraphData> = emptyList(),
)

/**
 * Data class representing a subgraph's collected graph information.
 */
internal data class SubgraphGraphData(
    val name: String,
    val id: String,
    val innerData: GraphData,
)

/**
 * Data class representing edge information with condition.
 */
internal data class EdgeInfo(
    val fromNode: NodeInfo,
    val toNode: NodeInfo,
    val condition: String?,
)

/**
 * Converts a framework node to its lightweight diagram representation.
 */
private fun AIAgentNodeBase<*, *>.toNodeInfo(): NodeInfo = NodeInfo(
    id = id,
    name = name,
    type = when (this) {
        is StartNode -> NodeType.START
        is FinishNode -> NodeType.FINISH
        else -> NodeType.REGULAR
    },
)

/**
 * Collects all graph data (nodes and edges) from the strategy.
 */
internal fun AIAgentGraphStrategy<*, *>.collectGraphData(): GraphData {
    return collectGraphLevel(this.name, this.nodeStart, this.nodeFinish)
}

/**
 * Collects graph data for a single level (strategy or subgraph) by traversing from the start node.
 * Subgraphs encountered during traversal are collected recursively.
 */
private fun collectGraphLevel(
    title: String,
    start: AIAgentNodeBase<*, *>,
    finish: AIAgentNodeBase<*, *>,
): GraphData {
    val nodes = mutableMapOf<String, NodeInfo>()
    val edges = mutableListOf<EdgeInfo>()
    val subgraphs = mutableListOf<SubgraphGraphData>()

    nodes[start.id] = start.toNodeInfo()
    nodes[finish.id] = finish.toNodeInfo()

    // BFS traversal from start
    val visited = mutableSetOf<String>()
    val queue = ArrayDeque<AIAgentNodeBase<*, *>>()
    queue.add(start)

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current.id in visited) continue
        visited.add(current.id)

        // Add node
        var currentInfo = nodes.getOrPut(current.id) { current.toNodeInfo() }

        // Collect inner structure for subgraph nodes. AIAgentGraphStrategy is excluded because
        // it represents the top-level strategy itself (which we are already traversing), not a
        // nested subgraph. Note: Planner strategies (AIAgentPlannerStrategy) are not graph-based
        // and don't extend AIAgentSubgraphBase, so they are not encountered during traversal.
        if (current is AIAgentSubgraphBase<*, *> && current !is AIAgentGraphStrategy<*, *>) {
            currentInfo = currentInfo.copy(type = NodeType.SUBGRAPH)
            nodes[current.id] = currentInfo
            subgraphs.add(
                SubgraphGraphData(
                    name = current.name,
                    id = current.id,
                    innerData = collectGraphLevel(current.name, current.start, current.finish),
                )
            )
        }

        // Collect edges from current node
        for (rawEdge in current.extractEdges()) {
            val toInfo = nodes.getOrPut(rawEdge.toNode.id) { rawEdge.toNode.toNodeInfo() }
            edges.add(EdgeInfo(currentInfo, toInfo, rawEdge.condition))
            if (rawEdge.toNode.id !in visited) {
                queue.add(rawEdge.toNode)
            }
        }
    }

    return GraphData(
        title = title,
        nodes = nodes.toMap(),
        edges = edges.toList(),
        subgraphs = subgraphs.toList(),
    )
}

/**
 * Raw edge info used internally during BFS collection.
 */
private data class RawEdgeInfo(
    val toNode: AIAgentNodeBase<*, *>,
    val condition: String?,
)

/**
 * Extension function to extract edges from a node using public API only.
 */
private fun AIAgentNodeBase<*, *>.extractEdges(): List<RawEdgeInfo> =
    this.edges.map { edge ->
        extractEdgeInfo(edge)
    }

/**
 * Extracts a condition label from a string by checking for known edge DSL keywords.
 */
private fun extractConditionLabel(str: String?): String? =
    when {
        str == null -> null
        str.contains("onCondition") -> "onCondition"
        str.contains("onToolCall") -> "onToolCall"
        str.contains("onAssistantMessage") -> "onAssistantMessage"
        str.contains("transformed") -> "transformed"
        str.contains("forwardTo") -> null
        else -> null
    }

/**
 * Extracts edge information from an AIAgentEdge.
 * Uses the public `toNode` property directly; reflection only for `forwardOutput` (internal).
 */
private fun extractEdgeInfo(edge: AIAgentEdge<*, *>): RawEdgeInfo {
    val toNode = edge.toNode

    val forwardOutput = edge::class.java.methods
        .firstOrNull { it.name == $$"getForwardOutput$agents_core" || it.name == "getForwardOutput" }
        ?.invoke(edge)

    return RawEdgeInfo(toNode, extractConditionFromForwardOutput(forwardOutput))
}

/**
 * Extracts condition information from the ForwardOutput function.
 */
private fun extractConditionFromForwardOutput(forwardOutput: Any?): String? {
    if (forwardOutput == null) return null

    // The ForwardOutput is a function, we need to examine its class name or toString
    val className = forwardOutput::class.java.name
    val toString = forwardOutput.toString()

    // Try to extract condition from class name or string representation
    return extractConditionLabel(className) ?: extractConditionLabel(toString)
}
