package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.element.NodeInfoContextElement
import ai.koog.agents.core.agent.context.element.getNodeInfoElement
import ai.koog.agents.core.agent.context.with
import ai.koog.agents.core.annotation.InternalAgentsApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.reflect.KType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents an abstract node in an AI agent strategy graph, responsible for executing a specific
 * operation and managing directed edges to other nodes.
 *
 * @param TInput The type of input data this node processes.
 * @param TOutput The type of output data this node produces.
 */
@OptIn(ExperimentalUuidApi::class)
public abstract class AIAgentNodeBase<TInput, TOutput> internal constructor() {
    /**
     * The name of the AI agent node.
     * This property serves as a unique identifier for the node within the strategy graph
     * and is used to distinguish and reference nodes in the graph structure.
     */
    public abstract val name: String

    /**
     * The [KType] of the [TInput]
     */
    public abstract val inputType: KType

    /**
     * The [KType] of the [TOutput]
     */
    public abstract val outputType: KType

    /**
     * Represents the unique identifier of the AI agent node.
     */
    public val id: String get() = name

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
    public var edges: List<AIAgentEdge<TOutput, *>> = emptyList()
        private set

    /**
     * Adds a directed edge from the current node, enabling connections between this node
     * and other nodes in the AI agent strategy graph.
     *
     * @param edge The edge to be added, representing a connection from this node's output
     * to another node in the strategy graph.
     */
    public open fun addEdge(edge: AIAgentEdge<TOutput, *>) {
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
        context: AIAgentGraphContextBase,
        nodeOutput: TOutput
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
    public suspend fun resolveEdgeUnsafe(context: AIAgentGraphContextBase, nodeOutput: Any?): ResolvedEdge? =
        resolveEdge(context, nodeOutput as TOutput)

    /**
     * Executes a specific operation based on the given context and input.
     *
     * @param context The execution context that provides necessary runtime information and functionality.
     * @param input The input data required to perform the execution.
     * @return The result of the execution as [TOutput] object.
     */
    public abstract suspend fun execute(context: AIAgentGraphContextBase, input: TInput): TOutput?

    /**
     * Executes the node operation using the provided execution context and input, bypassing type safety checks.
     * This method internally calls the type-safe `execute` method after casting the input.
     *
     * @param context The execution context that provides runtime information and functionality.
     * @param input The input data to be processed by the node, which may be of any type.
     * @return The result of the execution, which may be of any type depending on the implementation.
     */
    @Suppress("UNCHECKED_CAST")
    public suspend fun executeUnsafe(context: AIAgentGraphContextBase, input: Any?): Any? =
        execute(context, input as TInput)
}

/**
 * Represents a simple implementation of an AI agent node, encapsulating a specific execution
 * logic that processes the input data and produces an output.
 *
 * @param TInput The type of input data this node processes.
 * @param TOutput The type of output data this node produces.
 * @property name The name of the node, used for identification and debugging.
 * @property execute A suspending function that defines the execution logic for the node. It
 * processes the provided input within the given execution context and produces an output.
 */
public open class AIAgentNode<TInput, TOutput> internal constructor(
    override val name: String,
    override val inputType: KType,
    override val outputType: KType,
    public val execute: suspend AIAgentGraphContextBase.(input: TInput) -> TOutput,
) : AIAgentNodeBase<TInput, TOutput>() {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    @InternalAgentsApi
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun execute(context: AIAgentGraphContextBase, input: TInput): TOutput =
        context.with(id) { executionInfo ->
            withContext(NodeInfoContextElement(Uuid.random().toString(), getNodeInfoElement()?.id, name, input, inputType)) {
                logger.debug { "Start executing node (name: $name)" }
                context.pipeline.onNodeExecutionStarting(executionInfo, this@AIAgentNode, context, input, inputType)

                val output =
                    try {
                        val executeResult = context.execute(input)
                        logger.trace { "Finished executing node (name: $name) with output: $executeResult" }
                        executeResult
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error(e) { "Error executing node (name: $name): ${e.message}" }
                        context.pipeline.onNodeExecutionFailed(executionInfo, this@AIAgentNode, context, input, inputType, e)
                        throw e
                    }

                context.pipeline.onNodeExecutionCompleted(executionInfo, this@AIAgentNode, context, input, inputType, output, outputType)
                output
            }
        }
}

/**
 * Represents the base node for starting a subgraph in an AI agent strategy graph.
 * This node acts as an entry point for executing subgraphs.
 *
 * This node effectively passes its input as-is to the next node in the execution
 * pipeline, allowing downstream nodes to transform or handle the data further.
 *
 * The `name` property of the node reflects a uniquely identifiable pattern using
 * the prefix "__start__" and the optional subgraph name, enabling traceability of
 * execution flow in multi-subgraph setups.
 *
 * @param TInput The type of input data this node processes and produces as output.
 * @param subgraphName The name of the related subgraph
 * @param type [KType] representing [TInput]
 */
public class StartNode<TInput> internal constructor(
    subgraphName: String? = null,
    type: KType,
) : AIAgentNode<TInput, TInput>(
    name = subgraphName?.let { "${AIAgentSubgraph.START_NODE_PREFIX}$it" } ?: AIAgentSubgraph.START_NODE_PREFIX,
    inputType = type,
    outputType = type,
    execute = { input -> input }
)

/**
 * Represents a specialized node within an AI agent strategy graph that marks the endpoint of a subgraph.
 * This node serves as a "finish" node and directly passes its input to its output without modification.
 *
 * This node enforces the following constraints:
 * - It cannot have outgoing edges, meaning no further nodes can follow it in the execution graph.
 * - It simply returns the input it receives as its output, ensuring no modification occurs at the end of execution.
 *
 * The `name` property of the node reflects a uniquely identifiable pattern using
 * the prefix "__finish__" and the optional subgraph name, enabling traceability of
 * execution flow in multi-subgraph setups.
 *
 * @param TOutput The type of data this node processes and produces.
 * @param subgraphName The name of the related subgraph
 * @param type [KType] representing [TOutput]
 */
public class FinishNode<TOutput> internal constructor(
    subgraphName: String? = null,
    type: KType,
) : AIAgentNode<TOutput, TOutput>(
    name = subgraphName?.let { "${AIAgentSubgraph.FINISH_NODE_PREFIX}$it" } ?: AIAgentSubgraph.FINISH_NODE_PREFIX,
    inputType = type,
    outputType = type,
    execute = { input -> input }
) {
    override fun addEdge(edge: AIAgentEdge<TOutput, *>) {
        throw IllegalStateException("${this::class.simpleName} cannot have outgoing edges")
    }
}
