package ai.koog.agents.core.optimization.optimizers.utils

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.entity.FinishNode
import ai.koog.agents.core.agent.entity.StartNode
import ai.koog.agents.core.optimization.core.OptimizableNode
import ai.koog.agents.core.optimization.core.extractFieldDescriptions
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KType

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
                val inputDescs = extractFieldDescriptionsFromType(node.inputType)
                if (inputDescs.isNotEmpty()) {
                    appendLine("    Input fields:")
                    inputDescs.forEach { (name, desc) ->
                        appendLine("      - $name: $desc")
                    }
                }
                val outputDescs = extractFieldDescriptionsFromType(node.outputType)
                if (outputDescs.isNotEmpty()) {
                    appendLine("    Output fields:")
                    outputDescs.forEach { (name, desc) ->
                        appendLine("      - $name: $desc")
                    }
                }
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

private val json = Json { prettyPrint = false; isLenient = true; ignoreUnknownKeys = true }

/**
 * Extracts [@LLMDescription] field descriptions from a [KType] via runtime serializer lookup.
 * Returns an empty list if the type is not serializable or has no described fields.
 */
internal fun extractFieldDescriptionsFromType(type: KType): List<Pair<String, String>> {
    return try {
        val descriptor = json.serializersModule.serializer(type).descriptor
        extractFieldDescriptions(descriptor)
    } catch (_: SerializationException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }
}
