package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import kotlin.reflect.KFunction

/**
 * Creates a subgraph within an AI agent graph using a defined task, a tool selection strategy,
 * and a finish tool function. This method provides the capability to integrate task-specific logic
 * and tool selection into the AI agent's subgraph.
 *
 * @param Input The input type to the task defined in the subgraph.
 * @param Output The output type produced by the task and the finish tool.
 * @param toolSelectionStrategy The strategy to be used for selecting tools within the subgraph.
 * @param finishToolFunction The function to finalize the task execution, providing the result as Output.
 * @param llmModel The language model to be used within the subgraph, if specified.
 * @param llmParams Parameters to configure the language model's behavior, if specified.
 * @param defineTask A suspend function defining the task logic for the subgraph.
 * @return An AIAgentSubgraphDelegate representing the constructed subgraph with the specified configuration.
 */
@OptIn(InternalAgentToolsApi::class)
@AIAgentBuilderDslMarker
public inline fun <reified Input, reified Output> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    toolSelectionStrategy: ToolSelectionStrategy,
    finishToolFunction: KFunction<Output>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, Output> = subgraphWithTask(
    toolSelectionStrategy = toolSelectionStrategy,
    finishTool = finishToolFunction.asTool(),
    llmModel = llmModel,
    llmParams = llmParams,
    defineTask = defineTask
)

/**
 * Defines a subgraph with a specific task in an AI agent graph, enabling the coordination of
 * tools, task definitions, and execution logic.
 *
 * @param Input The input type expected for the subgraph task.
 * @param Output The output type expected from the subgraph task.
 * @param tools A list of tools available for the subgraph task to utilize.
 * @param finishToolFunction The function that represents the tool used to finish the task, producing the output.
 * @param llmModel An optional LLModel to use within the subgraph for task execution. Defaults to null.
 * @param llmParams Optional parameters for configuring the behavior of the LLModel. Defaults to null.
 * @param defineTask A suspend lambda function that defines the task, taking an input of type Input and returning a task description as a String.
 * @return An instance of AIAgentSubgraphDelegate that represents the defined subgraph with input and output types.
 */
@OptIn(InternalAgentToolsApi::class)
@AIAgentBuilderDslMarker
public inline fun <reified Input, reified Output> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    tools: List<Tool<*, *>>,
    finishToolFunction: KFunction<Output>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, Output> = subgraphWithTask(
    tools = tools,
    finishTool = finishToolFunction.asTool(),
    llmModel = llmModel,
    llmParams = llmParams,
    defineTask = defineTask
)
