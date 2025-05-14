package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.agent.entity.FinishAIAgentNodeBase
import ai.grazie.code.agents.core.agent.entity.AIAgentNodeBase
import ai.grazie.code.agents.core.agent.entity.AIAgentSubgraph
import ai.grazie.code.agents.core.agent.entity.StartAIAgentNodeBase
import ai.grazie.code.agents.core.agent.entity.ToolSelectionStrategy
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStageContextBase
import ai.grazie.code.agents.core.tools.Tool
import kotlin.reflect.KProperty

abstract class AIAgentSubgraphBuilderBase<Input, Output> {
    abstract val nodeStart: StartAIAgentNodeBase<Input>
    abstract val nodeFinish: FinishAIAgentNodeBase<Output>

    /**
     * Defines a new node in the agent's stage, representing a unit of execution that takes an input and produces an output.
     *
     * @param name An optional name for the node. If not provided, the property name of the delegate will be used.
     * @param execute A suspendable function that defines the node's execution logic.
     */
    fun <Input, Output> node(
        name: String? = null,
        execute: suspend AIAgentStageContextBase.(input: Input) -> Output
    ): LocalAgentNodeDelegate<Input, Output> {
        return LocalAgentNodeDelegate(name, AIAgentNodeBuilder(execute))
    }

    fun <Input, Output> subgraph(
        name: String? = null,
        toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
        define: AIAgentSubgraphBuilder<Input, Output>.() -> Unit
    ): LocalAgentSubgraphDelegate<Input, Output> {
        return AIAgentSubgraphBuilder<Input, Output>(name, toolSelectionStrategy).also { it.define() }.build()
    }

    fun <Input, Output> subgraph(
        name: String? = null,
        tools: List<Tool<*, *>>,
        define: AIAgentSubgraphBuilder<Input, Output>.() -> Unit
    ): LocalAgentSubgraphDelegate<Input, Output> {
        return subgraph(name, ToolSelectionStrategy.Tools(tools.map { it.descriptor }), define)
    }

    fun <IncomingOutput, OutgoingInput> edge(
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

class AIAgentSubgraphBuilder<Input, Output>(
    val name: String? = null,
    private val toolSelectionStrategy: ToolSelectionStrategy
) : AIAgentSubgraphBuilderBase<Input, Output>(),
    BaseBuilder<LocalAgentSubgraphDelegate<Input, Output>> {
    override val nodeStart = StartAIAgentNodeBase<Input>()
    override val nodeFinish = FinishAIAgentNodeBase<Output>()

    override fun build(): LocalAgentSubgraphDelegate<Input, Output> {
        require(isFinishReachable(nodeStart)) {
            "FinishSubgraphNode can't be reached from the StartNode of the agent's graph. Please, review how it was defined."
        }

        return LocalAgentSubgraphDelegate(name, nodeStart, nodeFinish, toolSelectionStrategy)
    }

}

interface LocalAgentSubgraphDelegateBase<Input, Output> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentSubgraph<Input, Output>
}

open class LocalAgentSubgraphDelegate<Input, Output> internal constructor(
    private val name: String?,
    val nodeStart: StartAIAgentNodeBase<Input>,
    val nodeFinish: FinishAIAgentNodeBase<Output>,
    private val toolSelectionStrategy: ToolSelectionStrategy
) : LocalAgentSubgraphDelegateBase<Input, Output> {
    private var subgraph: AIAgentSubgraph<Input, Output>? = null

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentSubgraph<Input, Output> {
        if (subgraph == null) {
            // if name is explicitly defined, use it, otherwise use property name as node name
            val nameOfSubgraph = this@LocalAgentSubgraphDelegate.name ?: property.name

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