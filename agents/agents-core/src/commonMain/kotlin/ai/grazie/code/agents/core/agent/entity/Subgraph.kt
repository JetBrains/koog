package ai.grazie.code.agents.core.agent.entity

import ai.grazie.code.agents.core.agent.AgentMaxNumberOfIterationsReachedException
import ai.grazie.code.agents.core.agent.AgentStuckInTheNodeException
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStage
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStageContext
import ai.grazie.code.agents.core.annotation.InternalAgentsApi
import ai.grazie.code.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.grazie.code.agents.core.prompt.Prompts.selectRelevantTools
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.annotations.LLMDescription
import ai.grazie.code.prompt.structure.json.JsonSchemaGenerator
import ai.grazie.code.prompt.structure.json.JsonStructuredData
import ai.grazie.utils.mpp.LoggerFactory
import kotlinx.serialization.Serializable

open class StartNode<Input>() : LocalAgentNode<Input, Input>() {
    var subgraphName: String? = null
        internal set

    override val name: String get() = subgraphName?.let { "__start__$it" } ?: "__start__"

    override suspend fun execute(context: LocalAgentStageContext, input: Input) = input
}

open class FinishNode<Output>() : LocalAgentNode<Output, Output>() {
    var subgraphName: String? = null
        internal set

    override val name: String = subgraphName?.let { "__finish__$it" } ?: "__finish__"

    override fun addEdge(edge: LocalAgentEdge<Output, *>) {
        throw IllegalStateException("FinishSubgraphNode cannot have outgoing edges")
    }

    override suspend fun execute(context: LocalAgentStageContext, input: Output) = input
}

open class LocalAgentSubgraph<Input, Output>(
    override val name: String,
    val start: StartNode<Input>,
    val finish: FinishNode<Output>,
    private val toolSelectionStrategy: ToolSelectionStrategy,
) : LocalAgentNode<Input, Output>() {
    companion object {
        private val logger =
            LoggerFactory.create("ai.grazie.code.agents.local.agent.stage.${LocalAgentStage::class.simpleName}")
    }

    override suspend fun execute(context: LocalAgentStageContext, input: Input): Output {
        if (toolSelectionStrategy == ToolSelectionStrategy.ALL) return doExecute(context, input)

        return doExecuteWithCustomTools(context, input)
    }

    private fun formatLog(context: LocalAgentStageContext, message: String): String =
        "$message [$name, ${context.strategyId}, ${context.sessionUuid.text}]"

    @OptIn(InternalAgentsApi::class)
    protected suspend fun doExecute(context: LocalAgentStageContext, initialInput: Input): Output {
        logger.info { formatLog(context, "Starting stage($name) execution") }
        var currentNode: LocalAgentNode<*, *> = start
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
                    throw AgentMaxNumberOfIterationsReachedException(context.config.maxAgentIterations)
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
                throw AgentStuckInTheNodeException(currentNode, nodeOutput)
            }

            currentNode = resolvedEdge.edge.toNode
            currentInput = resolvedEdge.output
        }

        logger.info { formatLog(context, "Stage(${name}) execution completed successfully") }
        @Suppress("UNCHECKED_CAST")
        return (currentInput as? Output) ?: run {
            logger.error {
                formatLog(
                    context,
                    "Invalid finish node output type: ${currentInput?.let { it::class.simpleName }}"
                )
            }
            throw IllegalStateException("${FinishNode::class.simpleName} should always return String")
        }
    }

    @Serializable
    private data class SelectedTools(
        @property:LLMDescription("List of selected tools for the given subtask")
        val tools: List<String>
    )

    private suspend fun doExecuteWithCustomTools(context: LocalAgentStageContext, input: Input): Output {
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
sealed interface ToolSelectionStrategy {
    /**
     * Represents the inclusion of all available tools in a given subgraph or process.
     *
     * This object signifies that no filtering or selection is applied to the set of tools
     * being used, and every tool is considered relevant for execution.
     *
     * Used in contexts where all tools should be provided or included without constraint,
     * such as within a `LocalAgentSubgraph` or similar constructs.
     */
    data object ALL : ToolSelectionStrategy

    /**
     * Represents a specific subset of tools used within a subgraph configuration where no tools are selected.
     *
     * This object, when used, implies that the subgraph should operate without any tools available. It can be
     * utilized in scenarios where tool functionality is not required or should be explicitly restricted.
     *
     * Part of the sealed interface `SubgraphToolSubset` which defines various tool subset configurations
     * for subgraph behaviors.
     */
    data object NONE : ToolSelectionStrategy

    /**
     * Represents a subset of tools tailored to the specific requirements of a subtask.
     *
     * The purpose of this class is to dynamically select and include only the tools that are directly relevant to the
     * provided subtask description (based on LLM request).
     * This ensures that unnecessary tools are excluded, optimizing the toolset for the specific use case.
     *
     * @property subtaskDescription A description of the subtask for which the relevant tools should be selected.
     */
    data class AutoSelectForTask(val subtaskDescription: String, val maxRetries: Int = 3) : ToolSelectionStrategy

    /**
     * Represents a subset of tools to be utilized within a subgraph or task.
     *
     * The Tools class allows for specifying a custom selection of tools that are relevant
     * to a specific operation or task. It forms a part of the `SubgraphToolSubset` interface
     * hierarchy for flexible and dynamic tool configurations.
     *
     * @property tools A collection of `ToolDescriptor` objects defining the tools to be used.
     */
    data class Tools(val tools: List<ToolDescriptor>) : ToolSelectionStrategy
}
