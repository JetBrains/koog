package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraphBase

/**
 * Data class representing collected graph information for diagram generation.
 */
internal data class GraphData(
    val title: String,
    val nodes: Map<String, AIAgentNodeBase<*, *>>,
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
    val fromNode: AIAgentNodeBase<*, *>,
    val toNode: AIAgentNodeBase<*, *>,
    val condition: String?,
)

/**
 * Data class representing raw edge information extracted from reflection.
 */
private data class RawEdgeInfo(
    val toNode: AIAgentNodeBase<*, *>?,
    val condition: String?,
)

/**
 * Collects all graph data (nodes and edges) from the strategy.
 */
internal fun AIAgentGraphStrategy<*, *>.collectGraphData(): GraphData {
    return collectGraphLevel(this.name, this.nodeStart, this.nodeFinish)
}

/**
 * Collects graph data for a single level (strategy or subgraph) by traversing from start node.
 * Subgraphs encountered during traversal are collected recursively.
 */
private fun collectGraphLevel(
    title: String,
    start: AIAgentNodeBase<*, *>,
    finish: AIAgentNodeBase<*, *>,
): GraphData {
    val nodes = mutableMapOf<String, AIAgentNodeBase<*, *>>()
    val edges = mutableListOf<EdgeInfo>()
    val subgraphs = mutableListOf<SubgraphGraphData>()

    nodes[start.id] = start
    nodes[finish.id] = finish

    // BFS traversal from start
    val visited = mutableSetOf<AIAgentNodeBase<*, *>>()
    val queue = ArrayDeque<AIAgentNodeBase<*, *>>()
    queue.add(start)

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current in visited) continue
        visited.add(current)

        // Add node
        nodes[current.id] = current

        // If this is a subgraph (but not the top-level strategy), collect its inner structure
        if (current is AIAgentSubgraphBase<*, *> && current !is AIAgentGraphStrategy<*, *>) {
            subgraphs.add(
                SubgraphGraphData(
                    name = current.name,
                    id = current.id,
                    innerData = collectGraphLevel(current.name, current.start, current.finish),
                )
            )
        }

        // Collect edges from current node
        try {
            val nodeEdges = current.extractEdges()
            nodeEdges.forEach { rawEdge ->
                rawEdge.toNode?.let { toNode ->
                    edges.add(EdgeInfo(current, toNode, rawEdge.condition))
                    if (toNode !in visited) {
                        queue.add(toNode)
                    }
                }
            }
        } catch (_: Exception) {
            // Skip nodes that don't have edges or can't be processed
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
 * Extension function to extract edges from a node using public API only.
 */
private fun AIAgentNodeBase<*, *>.extractEdges(): List<RawEdgeInfo> =
    try {
        this.edges.mapNotNull { edge ->
            extractEdgeInfo(edge)
        }
    } catch (_: Exception) {
        emptyList()
    }

/**
 * Extracts condition information from the ForwardOutput class name.
 */
private fun extractConditionFromClassName(className: String?): String? =
    when {
        className == null -> null

        className.contains("onCondition") -> "onCondition"

        className.contains("onToolCall") -> "onToolCall"

        className.contains("onAssistantMessage") -> "onAssistantMessage"

        className.contains("transformed") -> "transformed"

        className.contains("forwardTo") -> null

        // Simple forward, no condition
        else -> null
    }

/**
 * Extracts edge information from an AIAgentEdge using public API only.
 */
private fun extractEdgeInfo(edge: Any): RawEdgeInfo? {
    val toNode =
        runCatching {
            edge::class.java.getMethod("getToNode").invoke(edge) as? AIAgentNodeBase<*, *>
        }.getOrElse { return null }

    val forwardOutput =
        runCatching {
            edge::class.java.methods
                .firstOrNull { it.name == "getForwardOutput\$agents_core" || it.name == "getForwardOutput" }
                ?.invoke(edge)
        }.getOrNull()

    return RawEdgeInfo(toNode, extractConditionFromForwardOutput(forwardOutput))
}

/**
 * Extracts condition information from the ForwardOutput function.
 */
private fun extractConditionFromForwardOutput(forwardOutput: Any?): String? {
    return try {
        if (forwardOutput == null) return null

        // The ForwardOutput is a function, we need to examine its class name or toString
        val className = forwardOutput::class.java.name
        val toString = forwardOutput.toString()

        // Try to extract condition from class name or string representation
        extractConditionFromClassName(className) ?: extractConditionFromString(toString)
    } catch (_: Exception) {
        null
    }
}

/**
 * Extracts condition information from string representation.
 */
private fun extractConditionFromString(str: String): String? =
    when {
        str.contains("onCondition") -> "onCondition"
        str.contains("onToolCall") -> "onToolCall"
        str.contains("onAssistantMessage") -> "onAssistantMessage"
        str.contains("transformed") -> "transformed"
        else -> null
    }
