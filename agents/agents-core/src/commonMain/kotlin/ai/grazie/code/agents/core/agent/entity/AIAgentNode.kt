package ai.grazie.code.agents.core.agent.entity

import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStageContextBase
import ai.grazie.code.agents.core.annotation.InternalAgentsApi

/**
 * Represents an abstract node in an AI agent strategy graph, responsible for executing a specific
 * operation and managing directed edges to other nodes.
 *
 * @param Input The type of input data this node processes.
 * @param Output The type of output data this node produces.
 */
public abstract class AIAgentNodeBase<Input, Output> internal constructor() {
    public abstract val name: String

    /**
     * Represents the directed edges connecting the current node in the AI agent strategy graph
     * to other nodes. Each edge defines the flow and transformation of output data
     * from this node to another.
     *
     * The list is initially empty and can only be modified internally by using the
     * [addEdge] function, which appends new edges to the existing list.
     *
     * @property edges A list of [AIAgentEdge] describing the connections from this node
     * to other nodes in the strategy graph.
     */
    public var edges: List<AIAgentEdge<Output, *>> = emptyList()
        private set

    /**
     * Adds a directed edge from the current node, enabling connections between this node
     * and other nodes in the AI agent strategy graph.
     *
     * @param edge The edge to be added, representing a connection from this node's output
     * to another node in the strategy graph.
     */
    public open fun addEdge(edge: AIAgentEdge<Output, *>) {
        edges = edges + edge
    }

    /**
     * Represents a resolved edge in the context of an AI agent strategy graph, combining an edge and
     * its corresponding resolved output.
     *
     * @property edge The directed edge that connects different nodes within the AI agent strategy graph.
     * This edge signifies a pathway for data flow between nodes.
     * @property output The resolved output associated with the provided edge. This represents
     * the data produced or passed along this edge during execution.
     */
    public data class ResolvedEdge(val edge: AIAgentEdge<*, *>, val output: Any?)

    /**
     * Resolves the edge associated with the provided node output and execution context.
     * Iterates through available edges and identifies the first edge that can successfully
     * process the given node output within the provided context. If a resolvable edge is found,
     * it returns a `ResolvedEdge` containing the edge and its output. Otherwise, returns null.
     *
     * @param context The execution context in which the edge is resolved.
     * @param nodeOutput The output of the current node used to resolve the edge.
     * @return A `ResolvedEdge` containing the matched edge and its output, or null if no edge matches.
     */
    public suspend fun resolveEdge(
        context: AIAgentStageContextBase,
        nodeOutput: Output
    ): ResolvedEdge? {
        for (currentEdge in edges) {
            val output = currentEdge.forwardOutputUnsafe(nodeOutput, context)

            if (!output.isEmpty) {
                return ResolvedEdge(currentEdge, output.value)
            }
        }

        return null
    }

    /**
     * @suppress
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    public suspend fun resolveEdgeUnsafe(context: AIAgentStageContextBase, nodeOutput: Any?): ResolvedEdge? =
        resolveEdge(context, nodeOutput as Output)

    /**
     * Executes a specific operation based on the given context and input.
     *
     * @param context The execution context that provides necessary runtime information and functionality.
     * @param input The input data required to perform the execution.
     * @return The result of the execution as an Output object.
     */
    public abstract suspend fun execute(context: AIAgentStageContextBase, input: Input): Output

    /**
     * @suppress
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    public suspend fun executeUnsafe(context: AIAgentStageContextBase, input: Any?): Any? {
        context.pipeline.onBeforeNode(this, context, input)
        val output = execute(context, input as Input)
        context.pipeline.onAfterNode(this, context, input, output)

        return output
    }
}

/**
 * Represents a simple implementation of an AI agent node, encapsulating a specific execution
 * logic that processes the input data and produces an output.
 *
 * @param Input The type of input data this node processes.
 * @param Output The type of output data this node produces.
 * @property name The name of the node, used for identification and debugging.
 * @property execute A suspending function that defines the execution logic for the node. It
 * processes the provided input within the given execution context and produces an output.
 */
internal class AIAgentNode<Input, Output> internal constructor(
    override val name: String,
    val execute: suspend AIAgentStageContextBase.(input: Input) -> Output
) : AIAgentNodeBase<Input, Output>() {
    override suspend fun execute(context: AIAgentStageContextBase, input: Input): Output = context.execute(input)
}

public open class StartAIAgentNodeBase<Input>() : AIAgentNodeBase<Input, Input>() {
    public var subgraphName: String? = null
        internal set

    override val name: String get() = subgraphName?.let { "__start__$it" } ?: "__start__"

    override suspend fun execute(context: AIAgentStageContextBase, input: Input): Input = input
}

public open class FinishAIAgentNodeBase<Output>() : AIAgentNodeBase<Output, Output>() {
    public var subgraphName: String? = null
        internal set

    override val name: String = subgraphName?.let { "__finish__$it" } ?: "__finish__"

    override fun addEdge(edge: AIAgentEdge<Output, *>) {
        throw IllegalStateException("FinishSubgraphNode cannot have outgoing edges")
    }

    override suspend fun execute(context: AIAgentStageContextBase, input: Output): Output = input
}


internal class StartAIAgentNode internal constructor() : StartAIAgentNodeBase<Unit>()

/**
 * A specialized implementation of `FinishNode` that finalizes the execution of a local agent subgraph.
 *
 * This object represents the terminal node within a subgraph structure that returns the final output.
 * It is parameterized to work with output data of type `String`.
 *
 * The `FinishAgentNode` enforces the following constraints:
 * - It cannot have outgoing edges, meaning no further nodes can follow it in the execution graph.
 * - It simply returns the input it receives as its output, ensuring no modification occurs at the end of execution.
 *
 * This node is critical to denote the completion of localized processing within a subgraph context.
 */
internal object FinishAIAgentNode : FinishAIAgentNodeBase<String>()

