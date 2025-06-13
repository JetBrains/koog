package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.*
import ai.koog.agents.core.tools.Tool
import kotlin.reflect.KProperty

/**
 * Abstract base class for building AI agent subgraphs.
 *
 * This class provides utilities for defining and connecting nodes within a subgraph,
 * constructing custom subgraphs with specified tools or tool selection strategies,
 * and managing the structural relationships between subgraph nodes.
 *
 * @param Input The input type expected by the starting node of the subgraph.
 * @param Output The output type produced by the finishing node of the subgraph.
 */
public abstract class AIAgentSubgraphBuilderBase<Input, Output> {
    /**
     * Represents the starting node of the subgraph in the AI agent's strategy graph.
     *
     * This property holds a reference to a `StartAIAgentNodeBase` instance, which acts as the
     * entry point for the subgraph. It is used to define the initial step in the processing
     * pipeline for input data and is integral to the construction of the subgraph.
     *
     * @param Input The type of input data that this starting node processes.
     */
    public abstract val nodeStart: StartAIAgentNodeBase<Input>
    /**
     * Represents the "finish" node in the AI agent's subgraph structure. This node indicates
     * the endpoint of the subgraph and acts as a terminal stage where the workflow stops.
     *
     * The `nodeFinish` property is an abstract member that subclasses must define. It is of type
     * `FinishAIAgentNodeBase`, which is a specialized node that directly passes its input to its
     * output without modification as part of an identity operation.
     *
     * This node does not allow outgoing edges and cannot be linked further in the graph.
     * It serves as the final node responsible for receiving and producing data of the defined
     * output type.
     *
     * @param Output The type of data processed and produced by this node.
     */
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
     * @return The next node
     */
    public infix fun <IncomingOutput, OutgoingInput, OutgoingOutput> AIAgentNodeBase<IncomingOutput, OutgoingInput>.then(nextNode: AIAgentNodeBase<OutgoingInput, OutgoingOutput>): AIAgentNodeBase<OutgoingInput, OutgoingOutput> {
        edge(this forwardTo nextNode)
        return nextNode
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

/**
 * Builder class for creating AI agent subgraphs with a defined tool selection strategy.
 *
 * This class facilitates the construction of customized subgraphs in an AI agent's
 * execution pipeline. It provides methods for defining start and finish nodes and ensuring
 * the connectivity between them. The subgraph can be configured with a tool selection strategy
 * to control the tools available during its execution.
 *
 * @param Input The input type expected by the starting node of the subgraph.
 * @param Output The output type produced by the finishing node of the subgraph.
 * @property name Optional name of the subgraph for identification.
 * @property toolSelectionStrategy The strategy that defines how tools are selected and used
 * within the subgraph.
 */
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

/**
 * AIAgentSubgraphDelegateBase defines a delegate interface for accessing an instance of [AIAgentSubgraph].
 * This interface allows dynamically providing subgraph instances based on context or property reference.
 *
 * @param Input The type of the input data that the subgraph is designed to handle.
 * @param Output The type of the output data that the subgraph emits after processing.
 */
public interface AIAgentSubgraphDelegateBase<Input, Output> {
    /**
     * Provides access to an instance of [AIAgentSubgraph] based on the specified property reference.
     *
     * This operator function acts as a delegate to dynamically retrieve and return an appropriate
     * instance of [AIAgentSubgraph] associated with the input and output types specified by the containing context.
     *
     * @param thisRef The reference to the object that contains the delegated property. Can be null if the property is a top-level or package-level property.
     * @param property The property metadata used to identify the property for which the subgraph instance is being accessed.
     * @return An [AIAgentSubgraph] instance that handles the specified input and output data types.
     */
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentSubgraph<Input, Output>
}

/**
 * A delegate implementation that provides dynamic access to an instance of [AIAgentSubgraph].
 * This class facilitates constructing and associating a subgraph with specific start and finish nodes
 * and a defined tool selection strategy upon access.
 *
 * @param Input The type of input data that the subgraph processes.
 * @param Output The type of output data that the subgraph produces.
 * @constructor Creates an instance of [AIAgentSubgraphDelegate] with the specified subgraph parameters.
 *
 * @property name An optional name for the subgraph. If not provided, the property name
 * associated with the delegate is used as the subgraph name.
 * @property nodeStart The starting node of the subgraph. This node marks the entry point
 * of the subgraph and executes the initial logic.
 * @property nodeFinish The finishing node of the subgraph. This node marks the endpoint
 * and produces the final output of the subgraph.
 * @property toolSelectionStrategy The strategy for selecting the set of tools available
 * to the subgraph during its execution.
 */
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
