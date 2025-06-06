package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.*
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy.NoCompression
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.utils.Some
import kotlin.reflect.KProperty

// TODO: rename *BuilderBase to *Builder and use specific prefixes (or suffixes) for subclasses
public abstract class AIAgentSubgraphBuilderBase<Input, Output> {
    public abstract val nodeStart: StartAIAgentNodeBase<Input>
    public abstract val nodeFinish: FinishAIAgentNodeBase<Output>

    /**
     * Defines a new node in the agent's stage, representing a unit of execution that takes an input and produces an output.
     *
     * @param name An optional name for the node. If not provided, the property name of the delegate will be used.
     * @param execute A suspendable function that defines the node's execution logic.
     */
    public fun <Input, Output> node(
        name: String? = null,
        execute: suspend AIAgentContextBase.(input: Input) -> Output
    ): AIAgentNodeDelegateBase<Input, Output> {
        return AIAgentNodeDelegate(name, AIAgentNodeBuilder(execute))
    }

    /**
     * Creates a subgraph with specified tool selection strategy.
     * @param name Optional subgraph name
     * @param toolSelectionStrategy Strategy for tool selection
     * @param define Subgraph definition function
     */
    public fun <Input, Output> subgraph(
        name: String? = null,
        toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
        define: AIAgentSubgraphBuilderBase<Input, Output>.() -> Unit
    ): AIAgentSubgraphDelegateBase<Input, Output> {
        return AIAgentSubgraphBuilder<Input, Output>(name, toolSelectionStrategy).also { it.define() }.build()
    }

    /**
     * Creates a subgraph with specified tools.
     * @param name Optional subgraph name
     * @param tools List of tools available to the subgraph
     * @param define Subgraph definition function
     */
    public fun <Input, Output> subgraph(
        name: String? = null,
        tools: List<Tool<*, *>>,
        define: AIAgentSubgraphBuilderBase<Input, Output>.() -> Unit
    ): AIAgentSubgraphDelegateBase<Input, Output> {
        return subgraph(name, ToolSelectionStrategy.Tools(tools.map { it.descriptor }), define)
    }

    /**
     * Connects the sequence of nodes with edges between them.
     * @param nextNode Node to connect to
     * @return The [LinearStepsIntermediate] object that can be then passed to [linearSteps]
     */
    public infix fun <IncomingInput, IncomingOutput, OutgoingOutput> AIAgentNodeBase<IncomingInput, IncomingOutput>.then(
        nextNode: AIAgentNodeBase<IncomingOutput, OutgoingOutput>
    ): LinearStepsIntermediate<IncomingInput, OutgoingOutput> {
        edge(this forwardTo nextNode)
        return LinearStepsIntermediate(firstNode = this, lastNode = nextNode, allNodes = listOf(this, nextNode))
    }

    /**
     * Connects the last node of the current linear strategy intermediate to a new node in the sequence
     * and returns a new linear strategy intermediate representing the updated sequence.
     *
     * @param nextNode The next node to which the last node of the current intermediate will be connected.
     * @return A new instance of [LinearStepsIntermediate] with the updated sequence of nodes,
     *  starting from the original first node and ending at the provided next node.
     */
    public infix fun <IncomingInput, IncomingOutput, OutgoingOutput> LinearStepsIntermediate<IncomingInput, IncomingOutput>.then(
        nextNode: AIAgentNodeBase<IncomingOutput, OutgoingOutput>
    ): LinearStepsIntermediate<IncomingInput, OutgoingOutput> {
        return LinearStepsIntermediate(
            firstNode = this.firstNode,
            lastNode = nextNode,
            allNodes = this.allNodes + nextNode
        )
    }

    /**
     * Applies a defined linear strategy by connecting a sequence of nodes in a predefined order
     * and optionally applying a history compression strategy between each pair of nodes.
     *
     * @param steps The linear strategy consisting of interconnected nodes, represented by an instance of [LinearStepsIntermediate].
     *              It defines the sequence of nodes to be connected in the agent's graph.
     * @param historyCompressionBetweenSteps A strategy for compressing history between each step in the linear strategy. Defaults to [HistoryCompressionStrategy.NoCompression].
     */
    @Suppress("UNCHECKED_CAST")
    public fun linearSteps(
        steps: LinearStepsIntermediate<Input, Output>,
        historyCompressionBetweenSteps: HistoryCompressionStrategy = NoCompression
    ) {
        check(!steps.allNodes.contains(nodeStart)) {
            "`nodeStart` can't be one of the steps of the linear strategy. It will be already included automatically"
        }
        check(!steps.allNodes.contains(nodeFinish)) {
            "`nodeFinish` can't be one of the steps of the linear strategy. It will be already included automatically"
        }

        fun addEdge(from: AIAgentNodeBase<Any?, Any?>, to: AIAgentNodeBase<Any?, Any?>) {
            from.addEdge(AIAgentEdge(to, { _, output -> Some(output) }))
        }

        var currentNode: AIAgentNodeBase<Any?, Any?> = nodeStart as AIAgentNodeBase<Any?, Any?>

        steps.allNodes.forEachIndexed { index, node ->
            // We only compress history if it's needed AND if it's not between the nodeStart and the first node
            //   (doesn't make sense to compress in this case as nodeStart doesn't perform any additional actions)
            if (historyCompressionBetweenSteps !is NoCompression && index > 0) {
                val compressHistory by nodeLLMCompressHistory<Any?>(
                    name = "compressHistory_before_${node.name}",
                    strategy = historyCompressionBetweenSteps
                )
                currentNode.addEdge(AIAgentEdge(compressHistory, { _, output -> Some(output) }))
                currentNode = compressHistory
            }

            currentNode.addEdge(AIAgentEdge(node as AIAgentNodeBase<Any?, Any?>, { _, output -> Some(output) }))

            currentNode = node
        }
        addEdge(currentNode, nodeFinish as AIAgentNodeBase<Any?, Any?>)
    }

    /**
     * Creates an edge between nodes.
     * @param edgeIntermediate Intermediate edge builder
     */
    public fun <IncomingOutput, OutgoingInput> edge(
        edgeIntermediate: AIAgentEdgeBuilderIntermediate<IncomingOutput, OutgoingInput, OutgoingInput>
    ) {
        val edge = AIAgentEdgeBuilder(edgeIntermediate).build()
        edgeIntermediate.fromNode.addEdge(edge)
    }

    /**
     * Checks if finish node is reachable from start node.
     * @param start Starting node
     * @return True if finish node is reachable
     */
    protected fun isFinishReachable(start: StartAIAgentNodeBase<Input>): Boolean {
        val visited = mutableSetOf<AIAgentNodeBase<*, *>>()

        fun visit(node: AIAgentNodeBase<*, *>): Boolean {
            if (node == nodeFinish) return true
            if (node in visited) return false
            visited.add(node)
            return node.edges.any { visit(it.toNode) }
        }

        return visit(start)
    }
}

public class AIAgentSubgraphBuilder<Input, Output>(
    public val name: String? = null,
    private val toolSelectionStrategy: ToolSelectionStrategy
) : AIAgentSubgraphBuilderBase<Input, Output>(),
    BaseBuilder<AIAgentSubgraphDelegate<Input, Output>> {
    override val nodeStart: StartAIAgentNodeBase<Input> = StartAIAgentNodeBase()
    override val nodeFinish: FinishAIAgentNodeBase<Output> = FinishAIAgentNodeBase()

    override fun build(): AIAgentSubgraphDelegate<Input, Output> {
        require(isFinishReachable(nodeStart)) {
            "FinishSubgraphNode can't be reached from the StartNode of the agent's graph. Please, review how it was defined."
        }

        return AIAgentSubgraphDelegate(name, nodeStart, nodeFinish, toolSelectionStrategy)
    }

}

public interface AIAgentSubgraphDelegateBase<Input, Output> {
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentSubgraph<Input, Output>
}

public open class AIAgentSubgraphDelegate<Input, Output> internal constructor(
    private val name: String?,
    public val nodeStart: StartAIAgentNodeBase<Input>,
    public val nodeFinish: FinishAIAgentNodeBase<Output>,
    private val toolSelectionStrategy: ToolSelectionStrategy
) : AIAgentSubgraphDelegateBase<Input, Output> {
    private var subgraph: AIAgentSubgraph<Input, Output>? = null

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentSubgraph<Input, Output> {
        if (subgraph == null) {
            // if name is explicitly defined, use it, otherwise use property name as node name
            val nameOfSubgraph = this@AIAgentSubgraphDelegate.name ?: property.name

            subgraph = AIAgentSubgraph<Input, Output>(
                name = nameOfSubgraph,
                start = nodeStart.apply { subgraphName = nameOfSubgraph },
                finish = nodeFinish.apply { subgraphName = nameOfSubgraph },
                toolSelectionStrategy = toolSelectionStrategy,
            )
        }

        return subgraph!!
    }
}
