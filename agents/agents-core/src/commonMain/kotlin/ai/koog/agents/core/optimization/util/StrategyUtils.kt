package ai.koog.agents.core.optimization.util

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.entity.FinishNode
import ai.koog.agents.core.agent.entity.StartNode
import ai.koog.agents.core.optimization.OptimizableNode
import ai.koog.agents.core.optimization.core.OptimizationConfig

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
 * mappings for optimization. Only these nodes participate in prompt optimization.
 *
 * @return A list of all [OptimizableNode] instances in the strategy.
 */
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.findOptimizableModules(): List<OptimizableNode<*, *>> {
    return findAllNodes().filterIsInstance<OptimizableNode<*, *>>()
}

/**
 * Gets the names of all optimizable nodes in a strategy.
 *
 * @return A set of node names for all [OptimizableNode] instances.
 */
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.getOptimizableNodeNames(): Set<String> {
    return findOptimizableModules().map { it.name }.toSet()
}

/**
 * Creates an [OptimizationConfig] from the current instruction values of all
 * optimizable nodes in the strategy.
 *
 * This is useful for capturing the current state of a strategy's optimization parameters.
 *
 * @return An [OptimizationConfig] containing the current values from optimizable nodes.
 */
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.extractOptimizationConfig(): OptimizationConfig {
    val modules = findOptimizableModules()

    val instructions = modules.associate { it.name to it.instruction }

    return OptimizationConfig(
        instructions = instructions,
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
                errors.add("Instruction specified for non-optimizable node: '$nodeName'")
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
                errors.add("Demonstrations specified for non-optimizable node: '$nodeName'")
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
 * - List of optimizable nodes with their descriptions and instructions
 *
 * @return A text description of the strategy.
 */
public fun <TInput, TOutput> AIAgentGraphStrategy<TInput, TOutput>.describeForOptimization(): String {
    val modules = findOptimizableModules()

    return buildString {
        appendLine("Strategy: $name")
        appendLine()

        if (modules.isEmpty()) {
            appendLine("No optimizable nodes found.")
        } else {
            appendLine("Optimizable Nodes (${modules.size}):")
            for (node in modules) {
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

// Note: Full implementation of withOptimizedConfig() / bake-in is deferred.
// The primary mechanism for using optimized configs is via coroutine context during
// optimization evaluation. For deployment, users can either:
// 1. Continue using coroutine context: withContext(optimizationConfig) { agent.run(input) }
// 2. Eventually: use a bake-in mechanism that returns an optimized agent/strategy
//    with instruction and demonstrations set as node defaults (no context wrapper needed).
