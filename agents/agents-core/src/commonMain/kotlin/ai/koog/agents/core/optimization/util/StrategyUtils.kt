package ai.koog.agents.core.optimization.util

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.entity.FinishNode
import ai.koog.agents.core.agent.entity.StartNode
import ai.koog.agents.core.optimization.OptimizableNode
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.OptimizationConfig

/**
 * Finds all optimizable nodes in a strategy.
 *
 * A node is considered optimizable if it:
 * - Is an [AIAgentNode] (not a start/finish node or subgraph)
 * - Has a non-null [AIAgentNode.instruction]
 *
 * @return A list of all optimizable nodes in the strategy, discovered by traversing from the start node.
 */
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.findOptimizableNodes(): List<AIAgentNode<*, *>> {
    val optimizableNodes = mutableListOf<AIAgentNode<*, *>>()
    val visited = mutableSetOf<AIAgentNodeBase<*, *>>()

    fun visit(node: AIAgentNodeBase<*, *>) {
        if (node in visited) return
        visited.add(node)

        // Check if this is an optimizable AIAgentNode
        if (node is AIAgentNode<*, *> && node !is StartNode<*> && node !is FinishNode<*>) {
            if (node.instruction != null) {
                optimizableNodes.add(node)
            }
        }

        // Recurse into subgraphs
        if (node is AIAgentSubgraph<*, *>) {
            visit(node.start)
        }

        // Visit all connected nodes via edges
        for (edge in node.edges) {
            visit(edge.toNode)
        }
    }

    visit(nodeStart)
    return optimizableNodes
}

/**
 * Gets all nodes in a strategy (optimizable or not).
 *
 * @return A list of all [AIAgentNode] instances in the strategy (excluding start/finish nodes).
 */
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.findAllNodes(): List<AIAgentNode<*, *>> {
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

/**
 * Finds all [OptimizableNode] instances in a strategy.
 *
 * These are nodes created with the `optimizableNode` DSL that declare their input/output field
 * mappings for optimization. This is a subset of [findOptimizableNodes] — only nodes that are
 * specifically [OptimizableNode] (not regular nodes with instruction).
 *
 * @return A list of all [OptimizableNode] instances in the strategy.
 */
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.findOptimizableModules(): List<OptimizableNode<*, *>> {
    return findOptimizableNodes().filterIsInstance<OptimizableNode<*, *>>()
}

/**
 * Gets the names of all optimizable nodes in a strategy.
 *
 * @return A set of node names for all optimizable nodes.
 */
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.getOptimizableNodeNames(): Set<String> {
    return findOptimizableNodes().map { it.name }.toSet()
}

/**
 * Creates an [OptimizationConfig] from the current instruction and demonstration values of all
 * optimizable nodes in the strategy.
 *
 * This is useful for capturing the current state of a strategy's optimization parameters.
 *
 * @return An [OptimizationConfig] containing the current values from optimizable nodes.
 */
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.extractOptimizationConfig(): OptimizationConfig {
    val optimizableNodes = findOptimizableNodes()

    val instructions = optimizableNodes
        .filter { it.instruction != null }
        .associate { it.name to it.instruction!! }

    val demonstrations = optimizableNodes
        .filter { it.demonstrations.isNotEmpty() }
        .associate { it.name to it.demonstrations as List<Demonstration<*, *>> }

    return OptimizationConfig(
        instructions = instructions,
        demonstrations = demonstrations,
    )
}

/**
 * Validates that an [OptimizationConfig] is compatible with this strategy.
 *
 * Checks that:
 * - All node names in the config exist in the strategy
 * - All node names in the config correspond to optimizable nodes
 *
 * @param config The configuration to validate.
 * @return A list of validation errors, or an empty list if the config is valid.
 */
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.validateOptimizationConfig(
    config: OptimizationConfig
): List<String> {
    val errors = mutableListOf<String>()
    val optimizableNodeNames = getOptimizableNodeNames()
    val allNodeNames = findAllNodes().map { it.name }.toSet()

    // Check instructions
    for (nodeName in config.instructions.keys) {
        when {
            nodeName !in allNodeNames -> {
                errors.add("Instruction specified for unknown node: '$nodeName'")
            }
            nodeName !in optimizableNodeNames -> {
                errors.add("Instruction specified for non-optimizable node: '$nodeName' (node has no base instruction)")
            }
        }
    }

    // Check demonstrations
    for (nodeName in config.demonstrations.keys) {
        when {
            nodeName !in allNodeNames -> {
                errors.add("Demonstrations specified for unknown node: '$nodeName'")
            }
            nodeName !in optimizableNodeNames -> {
                errors.add("Demonstrations specified for non-optimizable node: '$nodeName' (node has no base instruction)")
            }
        }
    }

    return errors
}

/**
 * Creates a description of the strategy suitable for MIPRO instruction proposal.
 *
 * This generates a text description of the strategy structure including:
 * - Strategy name
 * - List of optimizable nodes with their descriptions
 * - Overall flow structure
 *
 * @return A text description of the strategy.
 */
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.describeForOptimization(): String {
    val optimizableNodes = findOptimizableNodes()

    return buildString {
        appendLine("Strategy: $name")
        appendLine()

        if (optimizableNodes.isEmpty()) {
            appendLine("No optimizable nodes found.")
        } else {
            appendLine("Optimizable Nodes (${optimizableNodes.size}):")
            for (node in optimizableNodes) {
                appendLine("  - ${node.name}")
                node.description?.let { desc ->
                    appendLine("    Description: $desc")
                }
                node.instruction?.let { instr ->
                    appendLine("    Current Instruction: ${instr.take(100)}${if (instr.length > 100) "..." else ""}")
                }
                if (node.demonstrations.isNotEmpty()) {
                    appendLine("    Demonstrations: ${node.demonstrations.size}")
                }
            }
        }
    }
}

// Note: Full implementation of withOptimizedConfig() is deferred.
// The primary mechanism for using optimized configs is via coroutine context during
// optimization evaluation. For deployment, users can either:
// 1. Continue using coroutine context (recommended for flexibility)
// 2. Manually construct a new strategy with optimized nodes using the strategy DSL
//
// Future enhancement: Provide a strategy cloning mechanism that can rebuild the graph
// with new node instances. This requires deeper integration with the strategy builder.
