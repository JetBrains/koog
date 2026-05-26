package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.with
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentEdgeBuilderIntermediate
import ai.koog.agents.core.dsl.builder.EdgeTransformationDslMarker
import ai.koog.agents.core.utils.Some
import ai.koog.serialization.TypeToken
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlin.uuid.ExperimentalUuidApi

/**
 * Represents an abstract node in an AI agent strategy graph, responsible for executing a specific
 * operation and managing directed edges to other nodes.
 *
 * @param TInput The type of input data this node processes.
 * @param TOutput The type of output data this node produces.
 */
@OptIn(ExperimentalUuidApi::class)
public abstract class AIAgentNodeBase<in TInput, TOutput> internal constructor() {
    /**
     * The name of the AI agent node.
     * This property serves as a unique identifier for the node within the strategy graph
     * and is used to distinguish and reference nodes in the graph structure.
     */
    public abstract val name: String

    /**
     * The [TypeToken] of the [TInput]
     */
    public abstract val inputType: TypeToken

    /**
     * The [TypeToken] of the [TOutput]
     */
    public abstract val outputType: TypeToken

    /**
     * Represents the unique identifier of the AI agent node.
     */
    public val id: String get() = name

    /**
     * The directed edges connecting this node to other nodes in the AI agent strategy graph.
     * Each edge defines the flow and optional transformation of output data from this node to another.
     *
     * The list starts empty and can only be extended via [addEdge]. Edges are evaluated in order
     * during [resolveEdge]: the first edge that successfully processes the node output is selected.
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
     * Implementations may return `null` to indicate that no output is produced for the current invocation
     * (for example, when the execution was interrupted and is expected to be resumed later).
     * Plain node implementations such as [SimpleAIAgentNodeImpl] always return a non-null [TOutput].
     *
     * @param context The execution context that provides necessary runtime information and functionality.
     * @param input The input data required to perform the execution.
     * @return The result of the execution as [TOutput], or `null` if no output is produced.
     */
    public abstract suspend fun execute(context: AIAgentGraphContextBase, input: TInput): TOutput?

    /**
     * Executes the node operation using the provided execution context and input, bypassing static type checks.
     * This method internally performs an unchecked cast of [input] to [TInput] and delegates to [execute].
     * The caller is responsible for ensuring that [input] is actually assignable to [TInput];
     * otherwise a [ClassCastException] may be thrown at the call site or later during execution.
     *
     * @param context The execution context that provides runtime information and functionality.
     * @param input The input data to be processed by the node, which may be of any type.
     * @return The result of the execution, which may be of any type depending on the implementation.
     */
    @Suppress("UNCHECKED_CAST")
    public suspend fun executeUnsafe(context: AIAgentGraphContextBase, input: Any?): Any? =
        execute(context, input as TInput)

    /**
     * Creates a directed edge from this [AIAgentNodeBase] to another [AIAgentNodeBase], allowing
     * data to flow from the output of the current node to the input of the specified node.
     *
     * @param otherNode The destination [AIAgentNodeBase] to which the current node's output is forwarded.
     * @return An [AIAgentEdgeBuilderIntermediate] that allows further customization
     * of the edge's data transformation and conditions between the nodes.
     */
    @EdgeTransformationDslMarker
    public infix fun <OutgoingInput> forwardTo(
        otherNode: AIAgentNodeBase<OutgoingInput, *>
    ): AIAgentEdgeBuilderIntermediate<TOutput, TOutput, OutgoingInput> {
        return AIAgentEdgeBuilderIntermediate(
            fromNode = this,
            toNode = otherNode,
            forwardOutputComposition = { _, output -> Some(output) }
        )
    }
}

/**
 * Represents a simple implementation of an AI agent node within a graph-based structure.
 *
 * This class facilitates the execution of a computation node, receiving an input of type [TInput] and producing
 * an output of type [TOutput]. It is designed to be used within an AI agent graph context.
 *
 * @param TInput The type of the input data this node accepts.
 * @param TOutput The type of the output data this node produces.
 * @property name The name of the node, used for identification within the graph.
 * @property inputType The [TypeToken] representing the expected type of input for the node.
 * @property outputType The [TypeToken] representing the type of output produced by the node.
 * @property execute A suspendable lambda function that defines the execution logic of the node. It operates
 * in the context of an [AIAgentGraphContextBase], taking an input of type [TInput] and producing an output of type [TOutput].
 */
public open class SimpleAIAgentNodeImpl<TInput, TOutput> internal constructor(
    override val name: String,
    override val inputType: TypeToken,
    override val outputType: TypeToken,
    public val execute: suspend AIAgentGraphContextBase.(input: TInput) -> TOutput,
) : AIAgentNodeBase<TInput, TOutput>() {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    @InternalAgentsApi
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun execute(context: AIAgentGraphContextBase, input: TInput): TOutput =
        context.with(id) { executionInfo, eventId ->
            logger.debug { "Start executing node (name: $name)" }
            context.pipeline.onNodeExecutionStarting(
                eventId,
                executionInfo,
                context,
                this@SimpleAIAgentNodeImpl,
                input,
                inputType
            )

            val output =
                try {
                    val executeResult = context.execute(input)
                    logger.trace { "Finished executing node (name: $name) with output: $executeResult" }
                    executeResult
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Error executing node (name: $name): ${e.message}" }
                    context.pipeline.onNodeExecutionFailed(
                        eventId,
                        executionInfo,
                        context,
                        this@SimpleAIAgentNodeImpl,
                        input,
                        inputType,
                        e
                    )
                    throw e
                }

            context.pipeline.onNodeExecutionCompleted(
                eventId,
                executionInfo,
                context,
                this@SimpleAIAgentNodeImpl,
                input,
                inputType,
                output,
                outputType
            )
            output
        }
}

/**
 * Platform-specific `expect`/`actual` declaration of [SimpleAIAgentNodeImpl].
 *
 * @param TInput The type of input data this node processes.
 * @param TOutput The type of output data this node produces.
 * @property name The name of the node, used for identification and debugging.
 * @property execute A suspending function that defines the execution logic for the node. It
 * processes the provided input within the given execution context and produces an output.
 * @see SimpleAIAgentNodeImpl
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect open class AIAgentNode<TInput, TOutput> internal constructor(
    name: String,
    inputType: TypeToken,
    outputType: TypeToken,
    execute: suspend AIAgentGraphContextBase.(input: TInput) -> TOutput,
) : SimpleAIAgentNodeImpl<TInput, TOutput>

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
 * @param type [TypeToken] representing [TInput]
 */
public class StartNode<TInput> internal constructor(
    subgraphName: String? = null,
    type: TypeToken
) : AIAgentNode<TInput, TInput>(
    name = subgraphName?.let { "${AIAgentSubgraphBase.START_NODE_PREFIX}$it" } ?: AIAgentSubgraphBase.START_NODE_PREFIX,
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
 * @param type [TypeToken] representing [TOutput]
 */
public class FinishNode<TOutput> internal constructor(
    subgraphName: String? = null,
    type: TypeToken,
) : AIAgentNode<TOutput, TOutput>(
    name = subgraphName?.let { "${AIAgentSubgraphBase.FINISH_NODE_PREFIX}$it" }
        ?: AIAgentSubgraphBase.FINISH_NODE_PREFIX,
    inputType = type,
    outputType = type,
    execute = { input -> input }
) {
    override fun addEdge(edge: AIAgentEdge<TOutput, *>) {
        throw IllegalStateException("${this::class.simpleName} cannot have outgoing edges")
    }
}
