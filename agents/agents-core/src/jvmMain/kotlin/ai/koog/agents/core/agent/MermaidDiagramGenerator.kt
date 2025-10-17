package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.FinishNode
import ai.koog.agents.core.agent.entity.StartNode

/**
 * Extension function to convert GraphData to mermaid diagram string.
 */
private fun GraphData.toMermaidDiagram(): String =
    buildString {
        appendLine("---")
        appendLine("title: $title")
        appendLine("---")
        appendLine("stateDiagram")

        // Render nodes
        nodes.values
            .filterNot { it is StartNode }
            .filterNot { it is FinishNode }
            .forEach { node ->
                appendLine("    ${node.toMermaidNode()}")
            }

        // Add blank line before edges if there are any
        if (edges.isNotEmpty()) {
            appendLine()
            // Render edges
            edges.forEach { edge ->
                appendLine("    ${edge.toMermaidEdge()}")
            }
        }
    }.trimEnd()

/**
 * Extension function to render an EdgeInfo as a mermaid edge string.
 */
private fun EdgeInfo.toMermaidEdge(): String {
    val fromId = fromNode.toMermaidNodeRef()
    val toId = toNode.toMermaidNodeRef()

    return if (condition != null && condition.isNotBlank()) {
        "$fromId --> $toId : $condition"
    } else {
        "$fromId --> $toId"
    }
}

/**
 * Extension function to render an AIAgentNode as a mermaid node string.
 */
private fun AIAgentNode<*, *>.toMermaidNode(): String =
    when (this) {
        is StartNode -> {
            "[*]"
        }

        is FinishNode -> {
            "[*]"
        }

        else -> {
            "state \"${name}\" as ${id.replace("-", "_")}"
        }
    }

/**
 * Converts an AIAgentNode to its mermaid node reference representation.
 *
 * @return A string representing the mermaid node reference. For StartNode and FinishNode,
 * it returns "[*]". For other nodes, it converts the node ID by replacing dashes with underscores.
 */
private fun AIAgentNode<*, *>.toMermaidNodeRef(): String =
    when (this) {
        is StartNode -> {
            "[*]"
        }

        is FinishNode -> {
            "[*]"
        }

        else -> {
            id.replace("-", "_")
        }
    }

/**
 * AIAgentGraphStrategy utility for generating Mermaid diagrams from agent strategies.
 *
 * References: https://docs.koog.ai/complex-workflow-agents/
 */
public fun <I : Any, O : Any> AIAgentGraphStrategy<I, O>.asMermaidDiagram(): String =
    try {
        val graphData = collectGraphData()
        graphData.toMermaidDiagram()
    } catch (e: Exception) {
        throw RuntimeException("Can't generate Mermaid diagram for graph", e)
    }

public object MermaidDiagramGenerator : DiagramGenerator {

    public override fun generate(graph: AIAgentGraphStrategy<*, *>): String {
        try {
            val graphData = graph.collectGraphData()
            return graphData.toMermaidDiagram()
        } catch (e: Exception) {
            throw RuntimeException("Can't generate Mermaid diagram for graph", e)
        }
    }
}
