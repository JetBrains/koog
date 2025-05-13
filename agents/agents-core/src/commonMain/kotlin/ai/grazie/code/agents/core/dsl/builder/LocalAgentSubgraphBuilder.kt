package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.agent.entity.FinishNode
import ai.grazie.code.agents.core.agent.entity.LocalAgentNode
import ai.grazie.code.agents.core.agent.entity.LocalAgentSubgraph
import ai.grazie.code.agents.core.agent.entity.StartNode
import ai.grazie.code.agents.core.agent.entity.ToolSelectionStrategy
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStageContext
import ai.grazie.code.agents.core.tools.Tool
import kotlin.reflect.KProperty

// TODO: rename *BuilderBase to *Builder and use specific prefixes (or suffixes) for subclasses
public abstract class LocalAgentSubgraphBuilderBase<Input, Output> {
    public abstract val nodeStart: StartNode<Input>
    public abstract val nodeFinish: FinishNode<Output>

    /**
     * Defines a new node in the agent's stage, representing a unit of execution that takes an input and produces an output.
     *
     * @param name An optional name for the node. If not provided, the property name of the delegate will be used.
     * @param execute A suspendable function that defines the node's execution logic.
     */
    public fun <Input, Output> node(
        name: String? = null,
        execute: suspend LocalAgentStageContext.(input: Input) -> Output
    ): LocalAgentNodeDelegateBase<Input, Output> {
        return LocalAgentNodeDelegate(name, LocalAgentNodeBuilder(execute))
    }

    public fun <Input, Output> subgraph(
        name: String? = null,
        toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
        define: LocalAgentSubgraphBuilderBase<Input, Output>.() -> Unit
    ): LocalAgentSubgraphDelegateBase<Input, Output> {
        return LocalAgentSubgraphBuilder<Input, Output>(name, toolSelectionStrategy).also { it.define() }.build()
    }

    public fun <Input, Output> subgraph(
        name: String? = null,
        tools: List<Tool<*, *>>,
        define: LocalAgentSubgraphBuilderBase<Input, Output>.() -> Unit
    ): LocalAgentSubgraphDelegateBase<Input, Output> {
        return subgraph(name, ToolSelectionStrategy.Tools(tools.map { it.descriptor }), define)
    }

    public fun <IncomingOutput, OutgoingInput> edge(
        edgeIntermediate: LocalAgentEdgeBuilderIntermediate<IncomingOutput, OutgoingInput, OutgoingInput>
    ) {
        val edge = LocalAgentEdgeBuilder(edgeIntermediate).build()
        edgeIntermediate.fromNode.addEdge(edge)
    }

    protected fun isFinishReachable(start: StartNode<Input>): Boolean {
        val visited = mutableSetOf<LocalAgentNode<*, *>>()

        fun visit(node: LocalAgentNode<*, *>): Boolean {
            if (node == nodeFinish) return true
            if (node in visited) return false
            visited.add(node)
            return node.edges.any { visit(it.toNode) }
        }

        return visit(start)
    }
}

internal class LocalAgentSubgraphBuilder<Input, Output>(
    public val name: String? = null,
    private val toolSelectionStrategy: ToolSelectionStrategy
) : LocalAgentSubgraphBuilderBase<Input, Output>(),
    BaseBuilder<LocalAgentSubgraphDelegate<Input, Output>> {
    override val nodeStart: StartNode<Input> = StartNode()
    override val nodeFinish: FinishNode<Output> = FinishNode()

    override fun build(): LocalAgentSubgraphDelegate<Input, Output> {
        require(isFinishReachable(nodeStart)) {
            "FinishSubgraphNode can't be reached from the StartNode of the agent's graph. Please, review how it was defined."
        }

        return LocalAgentSubgraphDelegate(name, nodeStart, nodeFinish, toolSelectionStrategy)
    }

}

public interface LocalAgentSubgraphDelegateBase<Input, Output> {
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): LocalAgentSubgraph<Input, Output>
}

internal open class LocalAgentSubgraphDelegate<Input, Output> internal constructor(
    private val name: String?,
    public val nodeStart: StartNode<Input>,
    public val nodeFinish: FinishNode<Output>,
    private val toolSelectionStrategy: ToolSelectionStrategy
) : LocalAgentSubgraphDelegateBase<Input, Output> {
    private var subgraph: LocalAgentSubgraph<Input, Output>? = null

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): LocalAgentSubgraph<Input, Output> {
        if (subgraph == null) {
            // if name is explicitly defined, use it, otherwise use property name as node name
            val nameOfSubgraph = this@LocalAgentSubgraphDelegate.name ?: property.name

            subgraph = LocalAgentSubgraph<Input, Output>(
                name = nameOfSubgraph,
                start = nodeStart.apply { subgraphName = nameOfSubgraph },
                finish = nodeFinish.apply { subgraphName = nameOfSubgraph },
                toolSelectionStrategy = toolSelectionStrategy,
            )
        }

        return subgraph!!
    }
}
