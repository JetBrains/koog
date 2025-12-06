@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.getAgentContextData
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.entity.FinishNode
import ai.koog.agents.core.agent.entity.StartNode
import ai.koog.agents.core.agent.entity.SubgraphMetadata
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

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
@AIAgentBuilderDslMarker
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
    public abstract val nodeStart: StartNode<Input>

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
    public abstract val nodeFinish: FinishNode<Output>

    /**
     * Defines a new node in the agent's stage, representing a unit of execution that takes an input and produces an output.
     *
     * @param name An optional name for the node. If not provided, the property name of the delegate will be used.
     * @param execute A suspendable function that defines the node's execution logic.
     */
    public inline fun <reified Input, reified Output> node(
        name: String? = null,
        noinline execute: suspend AIAgentGraphContextBase.(input: Input) -> Output
    ): AIAgentNodeDelegate<Input, Output> {
        return AIAgentNodeDelegate(
            name = name,
            inputType = typeOf<Input>(),
            outputType = typeOf<Output>(),
            execute = execute
        )
    }

    /**
     * Creates a subgraph with a specified tool selection strategy.
     * @param name Optional subgraph name
     * @param toolSelectionStrategy Strategy for tool selection
     * @param llmModel Initial LLM model used in this subgraph
     * @param llmParams Initial LLM prompt parameters used in this subgraph
     * @param responseProcessor Initial optional processor defining the post-processing of messages returned from the LLM.
     * @param define Subgraph definition function
     */
    public inline fun <reified Input, reified Output> subgraph(
        name: String? = null,
        toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        responseProcessor: ResponseProcessor? = null,
        define: AIAgentSubgraphBuilderBase<Input, Output>.() -> Unit
    ): AIAgentSubgraphDelegate<Input, Output> {
        return AIAgentSubgraphBuilder<Input, Output>(
            name,
            inputType = typeOf<Input>(),
            outputType = typeOf<Output>(),
            toolSelectionStrategy = toolSelectionStrategy,
            llmModel = llmModel,
            llmParams = llmParams,
            responseProcessor = responseProcessor,
        ).also { it.define() }.build()
    }

    /**
     * Creates a subgraph with specified tools.
     * @param name Optional subgraph name
     * @param tools List of tools available to the subgraph
     * @param llmModel Initial LLM model used in this subgraph
     * @param llmParams Initial LLM prompt parameters used in this subgraph
     * @param responseProcessor Initial optional processor defining the post-processing of messages returned from the LLM.
     * @param define Subgraph definition function
     */
    public inline fun <reified Input, reified Output> subgraph(
        name: String? = null,
        tools: List<Tool<*, *>>,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        responseProcessor: ResponseProcessor? = null,
        define: AIAgentSubgraphBuilderBase<Input, Output>.() -> Unit
    ): AIAgentSubgraphDelegate<Input, Output> {
        return subgraph(
            name = name,
            toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
            llmModel = llmModel,
            llmParams = llmParams,
            responseProcessor = responseProcessor,
            define = define
        )
    }

    /**
     * Connects the sequence of nodes with edges between them.
     * @param nextNode Node to connect to
     * @return The next node
     */
    public infix fun <IncomingOutput, OutgoingInput, OutgoingOutput> AIAgentNodeBase<IncomingOutput, OutgoingInput>.then(
        nextNode: AIAgentNodeBase<OutgoingInput, OutgoingOutput>
    ): AIAgentNodeBase<OutgoingInput, OutgoingOutput> {
        edge(this forwardTo nextNode)
        return nextNode
    }

    /**
     * Creates a node that executes multiple nodes in parallel.
     * @param nodes List of nodes to execute in parallel
     * @param dispatcher Coroutine dispatcher to use for parallel execution
     * @param name Optional node name
     * @param merge A suspendable lambda that defines how the outputs from the parallel nodes should be merged
     */
    public fun <Input, Output> parallel(
        vararg nodes: AIAgentNodeBase<Input, Output>,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        name: String? = null,
        merge: suspend AIAgentParallelNodesMergeContext<Input, Output>.() -> ParallelNodeExecutionResult<Output>,
    ): AIAgentNodeDelegate<Input, Output> {
        return AIAgentNodeDelegate(
            name,
            inputType = nodes.first().inputType,
            outputType = nodes.first().outputType,
            execute = { input ->
                val initialContext: AIAgentGraphContextBase = this

                // Execute all nodes in parallel using the provided dispatcher
                val nodeResults = supervisorScope {
                    nodes.map { node ->
                        async(dispatcher) {
                            val nodeContext = initialContext.fork()
                            val nodeOutput = node.execute(nodeContext, input)

                            if (nodeOutput == null && nodeContext.getAgentContextData() != null) {
                                throw IllegalStateException(
                                    "Checkpoints are not supported in parallel execution. Node: ${node.name}, Context: ${nodeContext.getAgentContextData()}"
                                )
                            }

                            @Suppress("UNCHECKED_CAST")
                            val executionResult = ParallelNodeExecutionResult(nodeOutput as Output, nodeContext)
                            ParallelResult(node.name, input, executionResult)
                        }
                    }.awaitAll()
                }

                // Merge parallel node results
                val mergeContext = AIAgentParallelNodesMergeContext(this, nodeResults)
                val result = with(mergeContext) { merge() }
                this.replace(result.context)
                result.output
            }
        )
    }

    /**
     * Creates an edge between nodes.
     * @param edgeIntermediate Intermediate edge builder
     */
    public fun <IncomingOutput, OutgoingInput, CompatibleOutput : OutgoingInput> edge(
        edgeIntermediate: AIAgentEdgeBuilderIntermediate<IncomingOutput, CompatibleOutput, OutgoingInput>
    ) {
        val edge = AIAgentEdgeBuilder(edgeIntermediate).build()
        edgeIntermediate.fromNode.addEdge(edge)
    }

    /**
     * Checks if the finish node is reachable from the start node.
     * @param start Starting node
     * @return True if the finish node is reachable
     */
    protected fun isFinishReachable(start: StartNode<Input>): Boolean {
        val visited = mutableSetOf<AIAgentNodeBase<*, *>>()

        fun visit(node: AIAgentNodeBase<*, *>): Boolean {
            if (node == nodeFinish) return true
            if (node in visited) return false
            visited.add(node)
            return node.edges.any { visit(it.toNode) }
        }

        return visit(start)
    }

    private fun getNodePath(node: AIAgentNodeBase<*, *>, parentPath: String): String {
        return "$parentPath:${node.id}"
    }

    internal fun buildSubgraphMetadata(
        start: StartNode<Input>,
        parentName: String,
        strategy: AIAgentGraphStrategy<Input, Output>
    ): SubgraphMetadata {
        val subgraphNodes = buildSubGraphNodesMap(start, parentName)
        subgraphNodes[parentName] = strategy

        // Check if the finish node is reachable from the start node
        if (!isFinishReachable(start)) {
            throw IllegalStateException(
                "Finish node is not reachable from the start node in the subgraph '$parentName'."
            )
        }

        // Validate that all nodes have unique names within the subgraph
        val names = subgraphNodes.keys.map { it.split(":").last() }
        val uniqueNames = names.toSet().size == names.size

        return SubgraphMetadata(
            nodesMap = subgraphNodes,
            uniqueNames = uniqueNames
        )
    }

    internal fun buildSubGraphNodesMap(
        start: StartNode<*>,
        parentName: String
    ): MutableMap<String, AIAgentNodeBase<*, *>> {
        val map = mutableMapOf<String, AIAgentNodeBase<*, *>>()

        fun visit(node: AIAgentNodeBase<*, *>) {
            if (node is FinishNode<*>) return
            if (getNodePath(node, parentName) in map) return
            if (node !is StartNode<*>) {
                if (node.name in map) {
                    throw IllegalStateException("Node with name '${node.name}' already exists in the subgraph.")
                }

                map[getNodePath(node, parentName)] = node
            }

            if (node is AIAgentSubgraph<*, *>) {
                val subgraphNodes = buildSubGraphNodesMap(node.start, getNodePath(node, parentName))
                map.putAll(subgraphNodes)
            }

            return node.edges.forEach { visit(it.toNode) }
        }

        visit(start)
        return map
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
 * @param llmModel Initial LLM model used in this subgraph
 * @param llmParams Initial LLM prompt parameters used in this subgraph
 * @param responseProcessor Initial optional processor defining the post-processing of messages returned from the LLM.
 */
public class AIAgentSubgraphBuilder<Input, Output>(
    public val name: String? = null,
    inputType: KType,
    outputType: KType,
    private val toolSelectionStrategy: ToolSelectionStrategy,
    private val llmModel: LLModel?,
    private val llmParams: LLMParams?,
    private val responseProcessor: ResponseProcessor? = null,
) : AIAgentSubgraphBuilderBase<Input, Output>(),
    BaseBuilder<AIAgentSubgraphDelegate<Input, Output>> {
    override val nodeStart: StartNode<Input> = StartNode(subgraphName = name, type = inputType)
    override val nodeFinish: FinishNode<Output> = FinishNode(subgraphName = name, type = outputType)

    override fun build(): AIAgentSubgraphDelegate<Input, Output> {
        require(isFinishReachable(nodeStart)) {
            "FinishSubgraphNode can't be reached from the StartNode of the agent's graph. Please, review how it was defined."
        }

        return AIAgentSubgraphDelegate(name, nodeStart, nodeFinish, toolSelectionStrategy, llmModel, llmParams, responseProcessor)
    }
}

/**
 * A delegate that provides dynamic access to an instance of [AIAgentSubgraph].
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
 * @property llmModel Initial LLM model used in this subgraph
 * @property llmParams Initial LLM prompt parameters used in this subgraph
 * @property responseProcessor Initial optional processor defining the post-processing of messages returned from the LLM.
 */
public open class AIAgentSubgraphDelegate<Input, Output> internal constructor(
    private val name: String?,
    public val nodeStart: StartNode<Input>,
    public val nodeFinish: FinishNode<Output>,
    private val toolSelectionStrategy: ToolSelectionStrategy,
    private val llmModel: LLModel?,
    private val llmParams: LLMParams?,
    private val responseProcessor: ResponseProcessor? = null,
) {
    private var subgraph: AIAgentSubgraph<Input, Output>? = null

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
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentSubgraph<Input, Output> {
        if (subgraph == null) {
            // if the name is explicitly defined, use it, otherwise use the property name as node name
            val nameOfSubgraph = this@AIAgentSubgraphDelegate.name ?: property.name

            subgraph = AIAgentSubgraph<Input, Output>(
                name = nameOfSubgraph,
                start = nodeStart,
                finish = nodeFinish,
                toolSelectionStrategy = toolSelectionStrategy,
                llmModel = llmModel,
                llmParams = llmParams,
                responseProcessor = responseProcessor,
            )
        }

        return subgraph!!
    }
}

/**
 * Represents the result of a parallel node execution, containing both the output value and the execution context.
 *
 * This class is used to capture the complete state of a node's execution, including both the
 * produced output value and the context in which it was executed. This allows for both the result
 * and any side effects or state changes to be preserved and utilized in subsequent operations.
 *
 * @param Output The type of the output value produced by the node execution.
 * @property output The output value produced by the node execution.
 * @property context The agent context in which the node was executed, containing any state changes.
 */
public data class ParallelNodeExecutionResult<Output>(val output: Output, val context: AIAgentContext)

/**
 * Represents the completed result of a parallel node execution.
 *
 * This class encapsulates the final state of a node that was executed as part of a parallel
 * execution strategy. It contains the node's name, the input that was provided to it, and the
 * final execution result including both output and context.
 *
 * @param Input The type of input that was provided to the node.
 * @param Output The type of output produced by the node.
 * @property nodeName The name of the node that was executed.
 * @property nodeInput The input value that was provided to the node.
 * @property nodeResult The final execution result containing both output and context.
 */
public data class ParallelResult<Input, Output>(
    val nodeName: String,
    val nodeInput: Input,
    val nodeResult: ParallelNodeExecutionResult<Output>
)
