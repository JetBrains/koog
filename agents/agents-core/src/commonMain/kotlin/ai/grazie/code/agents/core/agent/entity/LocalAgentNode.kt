package ai.grazie.code.agents.core.agent.entity

import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStageContext
import ai.grazie.code.agents.core.annotation.InternalAgentsApi

/**
 * Represents an abstract node in an AI agent strategy graph, responsible for executing a specific
 * operation and managing directed edges to other nodes.
 *
 * @param Input The type of input data this node processes.
 * @param Output The type of output data this node produces.
 */
abstract class LocalAgentNode<Input, Output> internal constructor() {
    abstract val name: String

    /**
     * Represents the directed edges connecting the current node in the AI agent strategy graph
     * to other nodes. Each edge defines the flow and transformation of output data
     * from this node to another.
     *
     * The list is initially empty and can only be modified internally by using the
     * [addEdge] function, which appends new edges to the existing list.
     *
     * @property edges A list of [LocalAgentEdge] describing the connections from this node
     * to other nodes in the strategy graph.
     */
    var edges: List<LocalAgentEdge<Output, *>> = emptyList()
        private set

    /**
     * Adds a directed edge from the current node, enabling connections between this node
     * and other nodes in the AI agent strategy graph.
     *
     * @param edge The edge to be added, representing a connection from this node's output
     * to another node in the strategy graph.
     */
    open fun addEdge(edge: LocalAgentEdge<Output, *>) {
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
    data class ResolvedEdge(val edge: LocalAgentEdge<*, *>, val output: Any?)
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
    suspend fun resolveEdge(
        context: LocalAgentStageContext,
        nodeOutput: Output
    ): ResolvedEdge? {
        var edge: LocalAgentEdge<*, *>? = null

        for (currentEdge in edges) {
            val output = currentEdge.forwardOutputUnsafe(nodeOutput, context)

            if (!output.isEmpty) {
                edge = currentEdge
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
    suspend fun resolveEdgeUnsafe(context: LocalAgentStageContext, nodeOutput: Any?) =
        resolveEdge(context, nodeOutput as Output)

    /**
     * Executes a specific operation based on the given context and input.
     *
     * @param context The execution context that provides necessary runtime information and functionality.
     * @param input The input data required to perform the execution.
     * @return The result of the execution as an Output object.
     */
    abstract suspend fun execute(context: LocalAgentStageContext, input: Input): Output

    /**
     * @suppress
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    suspend fun executeUnsafe(context: LocalAgentStageContext, input: Any?): Any? {
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
class SimpleLocalAgentNode<Input, Output> internal constructor(
    override val name: String,
    val execute: suspend LocalAgentStageContext.(input: Input) -> Output
) : LocalAgentNode<Input, Output>() {
    override suspend fun execute(context: LocalAgentStageContext, input: Input): Output = context.execute(input)
}

class StartAgentNode internal constructor() : StartNode<Unit>()

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
object FinishAgentNode : FinishNode<String>()
