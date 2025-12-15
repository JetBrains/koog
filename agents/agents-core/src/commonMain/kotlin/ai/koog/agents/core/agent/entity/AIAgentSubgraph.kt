package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.agent.context.element.NodeInfoContextElement
import ai.koog.agents.core.agent.context.element.getNodeInfoElement
import ai.koog.agents.core.agent.context.getAgentContextData
import ai.koog.agents.core.agent.context.store
import ai.koog.agents.core.agent.context.with
import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.agents.core.agent.exception.AIAgentStuckInTheNodeException
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.core.prompt.Prompts.selectRelevantTools
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredRequest
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.json.JsonStructure
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * [AIAgentSubgraph] represents a structured subgraph within an AI agent workflow. It serves as a logical
 * segment containing a defined starting and ending point. The subgraph is responsible for executing tasks
 * in a step-by-step manner, managing iterations, and handling tool selection strategies.
 *
 * @param TInput The type of input data accepted by the subgraph.
 * @param TOutput The type of output data returned by the subgraph.
 * @param name The name of the subgraph.
 * @param start The starting node of the subgraph, which initiates the processing.
 * @param finish The finishing node of the subgraph, which concludes the processing.
 * @param toolSelectionStrategy Strategy determining which tools should be available during this subgraph's execution.
 * @param llmModel Optional [LLModel] override for the subgraph execution.
 * @param llmParams Optional [LLMParams] override for the prompt for the subgraph execution.
 * @param responseProcessor Optional [ResponseProcessor] override for the subgraph execution.
 */
public open class AIAgentSubgraph<TInput, TOutput>(
    override val name: String,
    public val start: StartNode<TInput>,
    public val finish: FinishNode<TOutput>,
    private val toolSelectionStrategy: ToolSelectionStrategy,
    private val llmModel: LLModel? = null,
    private val llmParams: LLMParams? = null,
    private val responseProcessor: ResponseProcessor? = null,
) : AIAgentNodeBase<TInput, TOutput>(), ExecutionPointNode {
    override val inputType: KType = start.inputType
    override val outputType: KType = finish.outputType

    /**
     * Companion object for the AIAgentSubgraph class.
     *
     * This companion object provides predefined constants used to denote
     * special nodes (start and finish) within the subgraph of an AI agent strategy.
     * It also includes utilities for internal logging.
     */
    public companion object {
        private val logger = KotlinLogging.logger { }

        /**
         * A constant string used as a prefix to identify the starting node in an AI agent's execution graph.
         * This prefix is used to ensure unique identification and separation of the start node
         * within the graph structure or during execution-related operations.
         */
        public const val START_NODE_PREFIX: String = "__start__"

        /**
         * A constant string used as a prefix to identify "finish" nodes within the AI agent subgraph.
         * Finish nodes typically signify the conclusion of a sequence or process in the graph,
         * and this prefix is used to mark such nodes for easier recognition and handling.
         */
        public const val FINISH_NODE_PREFIX: String = "__finish__"
    }

    private var forcedNode: AIAgentNodeBase<*, *>? = null
    private var forcedInput: Any? = null

    override fun getExecutionPoint(): ExecutionPoint? {
        val forcedNode = this.forcedNode
        return if (forcedNode != null) {
            ExecutionPoint(forcedNode, forcedInput)
        } else {
            null
        }
    }

    override fun resetExecutionPoint() {
        forcedNode = null
        forcedInput = null
    }

    override fun enforceExecutionPoint(
        node: AIAgentNodeBase<*, *>,
        input: Any?
    ) {
        if (forcedNode != null || forcedInput != null) {
            throw IllegalStateException("Forced node is already set to ${forcedNode!!.name}")
        }
        forcedNode = node
        forcedInput = input
    }

    @Serializable
    private data class SelectedTools(
        @property:LLMDescription("List of selected tools for the given subtask")
        val tools: List<String>
    )

    @OptIn(DetachedPromptExecutorAPI::class)
    private suspend fun selectTools(context: AIAgentContext) = when (toolSelectionStrategy) {
        is ToolSelectionStrategy.ALL -> context.llm.tools
        is ToolSelectionStrategy.NONE -> emptyList()
        is ToolSelectionStrategy.Tools -> toolSelectionStrategy.tools
        is ToolSelectionStrategy.AutoSelectForTask -> context.llm.writeSession {
            val initialPrompt = prompt

            replaceHistoryWithTLDR()

            appendPrompt {
                user {
                    selectRelevantTools(tools, toolSelectionStrategy.subtaskDescription)
                }
            }

            val selectedTools = this.requestLLMStructured(
                config = StructuredRequestConfig(
                    default = StructuredRequest.Manual(
                        JsonStructure.create<SelectedTools>(
                            schemaGenerator = StandardJsonSchemaGenerator,
                            examples = listOf(SelectedTools(listOf()), SelectedTools(tools.map { it.name }.take(3))),
                        ),
                    ),
                    fixingParser = toolSelectionStrategy.fixingParser,
                )
            ).getOrThrow()

            prompt = initialPrompt

            tools.filter { it.name in selectedTools.data.tools.toSet() }
        }
    }

    /**
     * Executes the desired operation based on the input and the provided context.
     * This function determines the execution strategy based on the tool selection strategy configured in the class.
     *
     * @param context The context of the AI agent which includes all necessary resources and metadata for execution.
     * @param input The input object representing the data to be processed by the AI agent.
     * @return The output of the AI agent execution, generated after processing the input.
     */
    @OptIn(InternalAgentsApi::class, DetachedPromptExecutorAPI::class, ExperimentalUuidApi::class)
    override suspend fun execute(context: AIAgentGraphContextBase, input: TInput): TOutput? =
        context.with { executionInfo, eventId ->
            withContext(NodeInfoContextElement(Uuid.random().toString(), getNodeInfoElement()?.id, name, input, inputType)) {
                val newTools = selectTools(context)

                // Copy inner context with new tools, model and LLM params.
                val initialLLMContext = context.llm

                context.replace(
                    context.copy(
                        llm = context.llm.copy(
                            tools = newTools,
                            model = llmModel ?: context.llm.model,
                            prompt = context.llm.prompt.copy(params = llmParams ?: context.llm.prompt.params),
                            responseProcessor = responseProcessor
                        ),
                    ),
                )

                runIfNotStrategy(context) {
                    pipeline.onSubgraphExecutionStarting(eventId, executionInfo, this@AIAgentSubgraph, context, input, inputType)
                }

                // Execute the subgraph with an inner context and get the result and updated prompt.
                val result = try {
                    executeWithInnerContext(context, input)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Exception during executing subgraph '$name': ${e.message}" }
                    runIfNotStrategy(context) {
                        pipeline.onSubgraphExecutionFailed(eventId, executionInfo, this@AIAgentSubgraph, context, input, inputType, e)
                    }
                    throw e
                }

                // Restore original LLM context with updated message history.
                context.replace(
                    context.copy(
                        llm = initialLLMContext.copy(
                            prompt = context.llm.prompt.copy(params = initialLLMContext.prompt.params)
                        ),
                    ),
                )

                val innerForcedData = context.getAgentContextData()

                if (innerForcedData != null) {
                    context.store(innerForcedData)
                }

                runIfNotStrategy(context) {
                    pipeline.onSubgraphExecutionCompleted(eventId, executionInfo, this@AIAgentSubgraph, context, input, inputType, result, outputType)
                }

                result
            }
        }

    @OptIn(InternalAgentsApi::class)
    private suspend fun executeWithInnerContext(context: AIAgentGraphContextBase, initialInput: TInput): TOutput? {
        logger.info { formatLog(context, "Executing subgraph '$name'") }

        var currentNode: AIAgentNodeBase<*, *> = start
        var currentInput: Any? = initialInput

        val executionPoint = getExecutionPoint()
        if (executionPoint != null) {
            currentNode = executionPoint.node
            currentInput = executionPoint.input

            logger.info { formatLog(context, "Enforcing execution point: ${currentNode.name}") }

            resetExecutionPoint()
        } else {
            logger.info { formatLog(context, "No enforced execution point, starting from ${currentNode.name}") }
        }

        while (true) {
            context.stateManager.withStateLock { state ->
                if (++state.iterations > context.config.maxAgentIterations) {
                    logger.error {
                        formatLog(
                            context,
                            "Max iterations limit (${context.config.maxAgentIterations}) reached"
                        )
                    }
                    throw AIAgentMaxNumberOfIterationsReachedException(context.config.maxAgentIterations)
                }
            }

            // run the current node and get its output
            logger.debug { formatLog(context, "Executing node '${currentNode.name}'") }
            val nodeOutput: Any? = currentNode.executeUnsafe(context, currentInput)
            logger.debug { formatLog(context, "Completed node '${currentNode.name}'") }

            // forced context data means that we've requested interruption due to jump to another node / rolling back to checkpoint
            if (context.getAgentContextData() != null) {
                return null
            }

            // find the suitable edge to move to the next node, get the transformed output
            val resolvedEdge = currentNode.resolveEdgeUnsafe(context, nodeOutput)
                ?: // In we are in the finish node, we need to exit, otherwise we stuck in the node
                if (currentNode == finish) {
                    currentInput = nodeOutput
                    break
                } else {
                    logger.error { formatLog(context, "Agent stuck in node ${currentNode.name}") }
                    throw AIAgentStuckInTheNodeException(currentNode, nodeOutput)
                }

            currentNode = resolvedEdge.edge.toNode
            currentInput = resolvedEdge.output
        }

        logger.debug { formatLog(context, "Completed subgraph $name") }
        @Suppress("UNCHECKED_CAST")
        val result = (currentInput as? TOutput) ?: run {
            logger.error {
                formatLog(
                    context,
                    "Invalid finish node output type: ${currentInput?.let { it::class.simpleName }}"
                )
            }
            throw IllegalStateException("${FinishNode::class.simpleName} should always return String")
        }
        return result
    }

    /**
     * Executes the specified action for a case when executing a subgraph logic and not
     * specifically for strategy.
     *
     * Strategy is a special case of subgraph execution. Strategy execution wraps the subgraph [execute] method
     * with its specific implementation. This method ensures that the action we run is only
     * executed the current subgraph is not a strategy.
     *
     * The method is used for reporting subgraph-only agent events in the [ai.koog.agents.core.feature.pipeline.AIAgentPipeline]
     */
    @OptIn(InternalAgentsApi::class)
    private inline fun runIfNotStrategy(
        context: AIAgentGraphContextBase,
        action: AIAgentGraphContextBase.() -> Unit
    ) {
        // Check the agent execution path to recognize a strategy.
        // Ignore the strategy as it is handled separately in the [AIAgentGraphStrategy] class.
        // Strategy execution path: Agent | Run | Strategy
        val isStrategy = id == context.strategyName

        if (isStrategy) {
            return
        }

        action(context)
    }

    /**
     * Use this special wrapper to execute a block of code with a modified execution context for cases when
     * performing a direct subgraph execution.
     *
     * Strategy is a special case of subgraph execution. Strategy execution wraps the subgraph [execute] method
     * with its specific implementation. This method ensures that the action we run is only
     * executed the current subgraph is not a strategy.
     */
    @OptIn(InternalAgentsApi::class)
    private inline fun <T> AIAgentContext.with(
        block: (executionInfo: AgentExecutionInfo, eventId: String) -> T
    ): T {
        // Check the agent execution path to recognize a strategy.
        // Ignore the strategy as it is handled separately in the [AIAgentGraphStrategy] class.
        // Strategy execution path: Agent | Run | Strategy
        val isStrategy = id == strategyName
        return if (isStrategy) {
            this.with(this.executionInfo, block)
        } else {
            this.with(id, block)
        }
    }

    private fun formatLog(context: AIAgentContext, message: String): String =
        "$message [$name, ${context.strategyName}, ${context.runId}]"
}

/**
 * Represents a strategy to select a subset of tools to be used in a subgraph during its execution.
 *
 * This interface provides different configurations for tool selection, ranging from using all
 * available tools to a specific subset determined by the context or explicitly provided.
 */
public sealed interface ToolSelectionStrategy {
    /**
     * Represents the inclusion of all available tools in a given subgraph or process.
     *
     * This object signifies that no filtering or selection is applied to the set of tools
     * being used, and every tool is considered relevant for execution.
     *
     * Used in contexts where all tools should be provided or included without constraint,
     * such as within a `AIAgentSubgraph` or similar constructs.
     */
    public data object ALL : ToolSelectionStrategy

    /**
     * Represents a specific subset of tools used within a subgraph configuration where no tools are selected.
     *
     * This object, when used, implies that the subgraph should operate without any tools available. It can be
     * used in scenarios where tool functionality is not required or should be explicitly restricted.
     *
     * Part of the sealed interface `SubgraphToolSubset` which defines various tool subset configurations
     * for subgraph behaviors.
     */
    public data object NONE : ToolSelectionStrategy

    /**
     * Represents a subset of tools tailored to the specific requirements of a subtask.
     *
     * The purpose of this class is to dynamically select and include only the tools that are directly relevant to the
     * provided subtask description (based on LLM request).
     * This ensures that unnecessary tools are excluded, optimizing the toolset for the specific use case.
     *
     * @property subtaskDescription A description of the subtask for which the relevant tools should be selected.
     * @property fixingParser Optional [StructureFixingParser] to attempt fixes when malformed structured response with a tool list is received.
     */
    public data class AutoSelectForTask(
        val subtaskDescription: String,
        val fixingParser: StructureFixingParser? = null
    ) : ToolSelectionStrategy

    /**
     * Represents a subset of tools to be used within a subgraph or task.
     *
     * The Tools class allows for specifying a custom selection of tools that are relevant
     * to a specific operation or task. It forms a part of the `SubgraphToolSubset` interface
     * hierarchy for flexible and dynamic tool configurations.
     *
     * @property tools A collection of `ToolDescriptor` objects defining the tools to be used.
     */
    public data class Tools(val tools: List<ToolDescriptor>) : ToolSelectionStrategy
}
