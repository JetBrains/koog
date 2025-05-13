package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.agent.entity.FinishNode
import ai.grazie.code.agents.core.agent.entity.AgentNode
import ai.grazie.code.agents.core.agent.entity.AgentSubgraph
import ai.grazie.code.agents.core.agent.entity.StartNode
import ai.grazie.code.agents.core.agent.entity.ToolSelectionStrategy
import ai.grazie.code.agents.core.agent.entity.stage.AgentStageContext
import ai.grazie.code.agents.core.tools.Tool
import kotlin.reflect.KProperty

abstract class AgentSubgraphBuilderBase<Input, Output> {
    abstract val nodeStart: StartNode<Input>
    abstract val nodeFinish: FinishNode<Output>

    /**
     * Defines a new node in the agent's stage, representing a unit of execution that takes an input and produces an output.
     *
     * @param name An optional name for the node. If not provided, the property name of the delegate will be used.
     * @param execute A suspendable function that defines the node's execution logic.
     */
    fun <Input, Output> node(
        name: String? = null,
        execute: suspend AgentStageContext.(input: Input) -> Output
    ): AgentNodeDelegate<Input, Output> {
        return AgentNodeDelegate(name, AgentNodeBuilder(execute))
    }

    fun <Input, Output> subgraph(
        name: String? = null,
        toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
        define: AgentSubgraphBuilder<Input, Output>.() -> Unit
    ): AgentSubgraphDelegate<Input, Output> {
        return AgentSubgraphBuilder<Input, Output>(name, toolSelectionStrategy).also { it.define() }.build()
    }

    fun <Input, Output> subgraph(
        name: String? = null,
        tools: List<Tool<*, *>>,
        define: AgentSubgraphBuilder<Input, Output>.() -> Unit
    ): AgentSubgraphDelegate<Input, Output> {
        return subgraph(name, ToolSelectionStrategy.Tools(tools.map { it.descriptor }), define)
    }

    fun <IncomingOutput, OutgoingInput> edge(
        edgeIntermediate: AgentEdgeBuilderIntermediate<IncomingOutput, OutgoingInput, OutgoingInput>
    ) {
        val edge = AgentEdgeBuilder(edgeIntermediate).build()
        edgeIntermediate.fromNode.addEdge(edge)
    }

    protected fun isFinishReachable(start: StartNode<Input>): Boolean {
        val visited = mutableSetOf<AgentNode<*, *>>()

        fun visit(node: AgentNode<*, *>): Boolean {
            if (node == nodeFinish) return true
            if (node in visited) return false
            visited.add(node)
            return node.edges.any { visit(it.toNode) }
        }

        return visit(start)
    }
}

class AgentSubgraphBuilder<Input, Output>(
    val name: String? = null,
    private val toolSelectionStrategy: ToolSelectionStrategy
) : AgentSubgraphBuilderBase<Input, Output>(),
    BaseBuilder<AgentSubgraphDelegate<Input, Output>> {
    override val nodeStart = StartNode<Input>()
    override val nodeFinish = FinishNode<Output>()

    override fun build(): AgentSubgraphDelegate<Input, Output> {
        require(isFinishReachable(nodeStart)) {
            "FinishSubgraphNode can't be reached from the StartNode of the agent's graph. Please, review how it was defined."
        }

        return AgentSubgraphDelegate(name, nodeStart, nodeFinish, toolSelectionStrategy)
    }

}

interface AgentSubgraphDelegateBase<Input, Output> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): AgentSubgraph<Input, Output>
}

open class AgentSubgraphDelegate<Input, Output> internal constructor(
    private val name: String?,
    val nodeStart: StartNode<Input>,
    val nodeFinish: FinishNode<Output>,
    private val toolSelectionStrategy: ToolSelectionStrategy
) : AgentSubgraphDelegateBase<Input, Output> {
    private var subgraph: AgentSubgraph<Input, Output>? = null

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): AgentSubgraph<Input, Output> {
        if (subgraph == null) {
            // if name is explicitly defined, use it, otherwise use property name as node name
            val nameOfSubgraph = this@AgentSubgraphDelegate.name ?: property.name

            subgraph = AgentSubgraph<Input, Output>(
                name = nameOfSubgraph,
                start = nodeStart.apply { subgraphName = nameOfSubgraph },
                finish = nodeFinish.apply { subgraphName = nameOfSubgraph },
                toolSelectionStrategy = toolSelectionStrategy,
            )
        }

        return subgraph!!
    }
}