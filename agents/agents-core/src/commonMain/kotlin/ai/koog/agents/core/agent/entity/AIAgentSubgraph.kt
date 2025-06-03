package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.agents.core.agent.AIAgentStuckInTheNodeException
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.core.prompt.Prompts.selectRelevantTools
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.structure.json.JsonSchemaGenerator
import ai.koog.prompt.structure.json.JsonStructuredData
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi

/**
 * [AIAgentSubgraph] represents a structured subgraph within an AI agent workflow. It serves as a logical
 * segment containing a defined starting and ending point. The subgraph is responsible for executing tasks
 * in a step-by-step manner, managing iterations, and handling tool selection strategies.
 *
 * @param Input The type of input data accepted by the subgraph.
 * @param Output The type of output data returned by the subgraph.
 * @param name The name of the subgraph.
 * @param start The starting node of the subgraph, which initiates the processing.
 * @param finish The finishing node of the subgraph, which concludes the processing.
 * @param toolSelectionStrategy Strategy determining which tools should be available during this subgraph's execution.
 */
public open class AIAgentSubgraph<Input, Output>(
    override val name: String,
    public val start: StartAIAgentNodeBase<Input>,
    public val finish: FinishAIAgentNodeBase<Output>,
    private val toolSelectionStrategy: ToolSelectionStrategy,
) : AIAgentNodeBase<Input, Output>() {
    private companion object {
        private val logger = KotlinLogging.logger("ai.koog.agents.core.agent.entity.${AIAgentSubgraph::class.simpleName}")
    }

    /**
     * Executes the desired operation based on the input and the provided context.
     * This function determines the execution strategy based on the tool selection strategy configured in the class.
     *
     * @param context The context of the AI agent which includes all necessary resources and metadata for execution.
     * @param input The input object representing the data to be processed by the AI agent.
     * @return The output of the AI agent execution, generated after processing the input.
     */
    override suspend fun execute(context: AIAgentContextBase, input: Input): Output {
        if (toolSelectionStrategy == ToolSelectionStrategy.ALL) return doExecute(context, input)

        return doExecuteWithCustomTools(context, input)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun formatLog(context: AIAgentContextBase, message: String): String =
        "$message [$name, ${context.strategyId}, ${context.sessionUuid}]"

    @OptIn(InternalAgentsApi::class)
    protected suspend fun doExecute(context: AIAgentContextBase, initialInput: Input): Output {
        logger.info { formatLog(context, "Executing subgraph $name") }
        var currentNode: AIAgentNodeBase<*, *> = start
        var currentInput: Any? = initialInput

        while (currentNode != finish) {
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
            logger.info { formatLog(context, "Executing node ${currentNode.name}") }
            val nodeOutput = currentNode.executeUnsafe(context, currentInput)
            logger.info { formatLog(context, "Completed node ${currentNode.name}") }

            // find the suitable edge to move to the next node, get the transformed output
            val resolvedEdge = currentNode.resolveEdgeUnsafe(context, nodeOutput)

            if (resolvedEdge == null) {
                logger.error { formatLog(context, "Agent stuck in node ${currentNode.name}") }
                throw AIAgentStuckInTheNodeException(currentNode, nodeOutput)
            }

            currentNode = resolvedEdge.edge.toNode
            currentInput = resolvedEdge.output
        }

        logger.info { formatLog(context, "Completed subgraph $name") }
        @Suppress("UNCHECKED_CAST")
        return (currentInput as? Output) ?: run {
            logger.error {
                formatLog(
                    context,
                    "Invalid finish node output type: ${currentInput?.let { it::class.simpleName }}"
                )
            }
            throw IllegalStateException("${FinishAIAgentNodeBase::class.simpleName} should always return String")
        }
    }

    @Serializable
    private data class SelectedTools(
        @property:LLMDescription("List of selected tools for the given subtask")
        val tools: List<String>
    )

    private suspend fun doExecuteWithCustomTools(context: AIAgentContextBase, input: Input): Output {
        @OptIn(InternalAgentsApi::class)
        val innerContext = when (toolSelectionStrategy) {
            ToolSelectionStrategy.ALL -> context
            ToolSelectionStrategy.NONE -> context.copyWithTools(emptyList())
            is ToolSelectionStrategy.Tools -> context.copyWithTools(toolSelectionStrategy.tools)
            is ToolSelectionStrategy.AutoSelectForTask -> {
                val newTools = context.llm.writeSession {
                    val initialPrompt = prompt

                    replaceHistoryWithTLDR()
                    updatePrompt {
                        user {
                            selectRelevantTools(tools, toolSelectionStrategy.subtaskDescription)
                        }
                    }

                    val selectedTools = this.requestLLMStructured(
                        structure = JsonStructuredData.createJsonStructure<SelectedTools>(
                            schemaFormat = JsonSchemaGenerator.SchemaFormat.JsonSchema,
                            examples = listOf(SelectedTools(listOf()), SelectedTools(tools.map { it.name }.take(3))),
                        ),
                        retries = toolSelectionStrategy.maxRetries,
                    ).getOrThrow()

                    rewritePrompt { initialPrompt }

                    tools.filter { it.name in selectedTools.structure.tools.toSet() }
                }
                context.copyWithTools(newTools)
            }
        }

        val subgraphResult = doExecute(innerContext, input)
        val newPrompt = innerContext.llm.readSession { prompt }
        context.llm.writeSession {
            rewritePrompt {
                newPrompt
            }
        }
        return subgraphResult
    }
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
     * utilized in scenarios where tool functionality is not required or should be explicitly restricted.
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
     */
    public data class AutoSelectForTask(val subtaskDescription: String, val maxRetries: Int = 3) : ToolSelectionStrategy

    /**
     * Represents a subset of tools to be utilized within a subgraph or task.
     *
     * The Tools class allows for specifying a custom selection of tools that are relevant
     * to a specific operation or task. It forms a part of the `SubgraphToolSubset` interface
     * hierarchy for flexible and dynamic tool configurations.
     *
     * @property tools A collection of `ToolDescriptor` objects defining the tools to be used.
     */
    public data class Tools(val tools: List<ToolDescriptor>) : ToolSelectionStrategy
}
