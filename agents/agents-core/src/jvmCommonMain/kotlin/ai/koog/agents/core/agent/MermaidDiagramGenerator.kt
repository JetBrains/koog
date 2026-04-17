package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy

private val MERMAID_ID_REGEX = Regex("[^a-zA-Z0-9_]")

/**
 * Sanitizes a string for use as a Mermaid state diagram identifier.
 */
private fun String.toMermaidId(): String = replace(MERMAID_ID_REGEX, "_")

/**
 * Extension function to convert GraphData to mermaid diagram string.
 */
private fun GraphData.toMermaidDiagram(): String =
    buildString {
        appendLine("---")
        appendLine("title: $title")
        appendLine("---")
        appendLine("stateDiagram")

        renderGraphContent(this, this@toMermaidDiagram, indent = "    ")
    }.trimEnd()

/**
 * Renders graph content (nodes, subgraphs, edges) at a given indentation level.
 * Used recursively for nested subgraphs.
 */
private fun renderGraphContent(
    sb: StringBuilder,
    graphData: GraphData,
    indent: String,
) {
    // Render regular nodes only. Start/Finish use [*] syntax in edges, and subgraphs
    // are rendered separately below as composite states with their own inner content.
    graphData.nodes.values
        .filter { it.type == NodeType.REGULAR }
        .forEach { node ->
            sb.appendLine("$indent${node.toMermaidNode()}")
        }

    // Render subgraphs as composite states
    graphData.subgraphs.forEach { subgraph ->
        val sgId = subgraph.id.toMermaidId()
        sb.appendLine("${indent}state \"${subgraph.name}\" as $sgId {")
        renderGraphContent(sb, subgraph.innerData, indent = "$indent    ")
        sb.appendLine("$indent}")
    }

    // Add blank line before edges if there are any
    if (graphData.edges.isNotEmpty()) {
        sb.appendLine()
        graphData.edges.forEach { edge ->
            sb.appendLine("$indent${edge.toMermaidEdge()}")
        }
    }
}

/**
 * Extension function to render an EdgeInfo as a mermaid edge string.
 */
private fun EdgeInfo.toMermaidEdge(): String {
    val fromId = fromNode.toMermaidNodeRef()
    val toId = toNode.toMermaidNodeRef()

    return if (!condition.isNullOrBlank()) {
        "$fromId --> $toId : $condition"
    } else {
        "$fromId --> $toId"
    }
}

/**
 * Extension function to render a NodeInfo as a mermaid node declaration string.
 */
private fun NodeInfo.toMermaidNode(): String =
    when (type) {
        NodeType.START, NodeType.FINISH -> "[*]"
        NodeType.REGULAR, NodeType.SUBGRAPH -> "state \"${name}\" as ${id.toMermaidId()}"
    }

/**
 * Converts a NodeInfo to its mermaid node reference representation.
 */
private fun NodeInfo.toMermaidNodeRef(): String =
    when (type) {
        NodeType.START, NodeType.FINISH -> "[*]"
        NodeType.REGULAR, NodeType.SUBGRAPH -> id.toMermaidId()
    }

/**
 * Extension function to generate a Mermaid diagram from an agent graph strategy.
 *
 * References: https://docs.koog.ai/complex-workflow-agents/
 */
public fun <I : Any, O : Any> AIAgentGraphStrategy<I, O>.asMermaidDiagram(): String =
    MermaidDiagramGenerator.generate(this)

public object MermaidDiagramGenerator : DiagramGenerator {

    public override fun generate(graph: AIAgentGraphStrategy<*, *>): String {
        try {
            val graphData = graph.collectGraphData()
            return graphData.toMermaidDiagram()
        } catch (e: Exception) {
            throw RuntimeException("Can't generate Mermaid diagram for graph '${graph.name}'", e)
        }
    }
}
