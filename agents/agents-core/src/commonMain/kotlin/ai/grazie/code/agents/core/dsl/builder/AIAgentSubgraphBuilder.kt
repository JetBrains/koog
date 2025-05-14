package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.agent.entity.*
import ai.grazie.code.agents.core.tools.Tool
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

    public fun <Input, Output> subgraph(
        name: String? = null,
        toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
        define: AIAgentSubgraphBuilderBase<Input, Output>.() -> Unit
    ): AIAgentSubgraphDelegateBase<Input, Output> {
        return AIAgentSubgraphBuilder<Input, Output>(name, toolSelectionStrategy).also { it.define() }.build()
    }

    public fun <Input, Output> subgraph(
        name: String? = null,
        tools: List<Tool<*, *>>,
        define: AIAgentSubgraphBuilderBase<Input, Output>.() -> Unit
    ): AIAgentSubgraphDelegateBase<Input, Output> {
        return subgraph(name, ToolSelectionStrategy.Tools(tools.map { it.descriptor }), define)
    }

    public infix fun <IncomingOutput, OutgoingInput, OutgoingOutput> AIAgentNodeBase<IncomingOutput, OutgoingInput>.then(nextNode: AIAgentNodeBase<OutgoingInput, OutgoingOutput>): AIAgentNodeBase<OutgoingInput, OutgoingOutput> {
        edge(this forwardTo nextNode)
        return nextNode
    }

    public fun <IncomingOutput, OutgoingInput> edge(
        edgeIntermediate: AIAgentEdgeBuilderIntermediate<IncomingOutput, OutgoingInput, OutgoingInput>
    ) {
        val edge = AIAgentEdgeBuilder(edgeIntermediate).build()
        edgeIntermediate.fromNode.addEdge(edge)
    }

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

internal class AIAgentSubgraphBuilder<Input, Output>(
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

internal open class AIAgentSubgraphDelegate<Input, Output> internal constructor(
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
