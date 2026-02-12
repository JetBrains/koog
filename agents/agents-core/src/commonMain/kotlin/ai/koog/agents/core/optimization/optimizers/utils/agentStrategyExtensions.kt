package ai.koog.agents.core.optimization.optimizers.utils

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.entity.FinishNode
import ai.koog.agents.core.agent.entity.StartNode
import ai.koog.agents.core.optimization.core.OptimizableNode

/**
 * Finds all [OptimizableNode] instances in a strategy.
 *
 * These are nodes created with the `optimizableNode` DSL that declare their input/output field
 * mappings for optimization. Only these nodes participate in prompt optimization.
 *
 * @return A list of all [OptimizableNode] instances in the strategy.
 */
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.findOptimizableNodes(): List<OptimizableNode<*, *>> {
    return findAllNodes().filterIsInstance<OptimizableNode<*, *>>()
}

/**
 * Creates a description of the strategy suitable for MIPRO instruction proposal.
 *
 * This generates a text description of the strategy structure including
 * - Strategy name
 * - List of optimizable nodes with their descriptions and instructions
 *
 * @return A text description of the strategy.
 */
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.describeForOptimization(): String {
    val nodes = findOptimizableNodes()

    return buildString {
        appendLine("Strategy: $name")
        appendLine()

        if (nodes.isEmpty()) {
            appendLine("No optimizable nodes found.")
        } else {
            appendLine("Optimizable Nodes (${nodes.size}):")
            for (node in nodes) {
                appendLine("  - ${node.name}")
                node.description?.let { desc ->
                    appendLine("    Description: $desc")
                }
                val instr = node.instruction
                appendLine("    Current Instruction: ${instr.take(100)}${if (instr.length > 100) "..." else ""}")
            }
        }
    }
}

/**
 * Gets all nodes in a strategy (optimizable or not), excluding start/finish nodes.
 *
 * Traverses the full graph including subgraph internals.
 *
 * @return A list of all [AIAgentNode] instances in the strategy.
 */
internal fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.findAllNodes(): List<AIAgentNode<*, *>> {
    val nodes = mutableListOf<AIAgentNode<*, *>>()
    val visited = mutableSetOf<AIAgentNodeBase<*, *>>()

    fun visit(node: AIAgentNodeBase<*, *>) {
        if (node in visited) return
        visited.add(node)

        if (node is AIAgentNode<*, *> && node !is StartNode<*> && node !is FinishNode<*>) {
            nodes.add(node)
        }

        if (node is AIAgentSubgraph<*, *>) {
            visit(node.start)
        }

        for (edge in node.edges) {
            visit(edge.toNode)
        }
    }

    visit(nodeStart)
    return nodes
}
