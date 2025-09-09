package ai.koog.ktor

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.utils.use
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import io.ktor.server.application.pluginOrNull
import io.ktor.server.routing.RoutingContext
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Retrieve the configured llm, or [PromptExecutor] instance from the underlying [Koog] plugin.
 */
public fun RoutingContext.llm(): PromptExecutor =
    requireNotNull(call.application.pluginOrNull(Koog)) { "Plugin $Koog is not configured" }.promptExecutor

/**
 * Creates an AI agent using the provided AI agent strategy within the specified route.
 *
 * @param Input The type of input data for the AI agent.
 * @param Output The type of output data for the AI agent.
 * @param strategy The AI agent strategy defining the workflow and execution logic of the agent.
 * @return An instance of `AIAgent` configured with the specified strategy and the route's resources.
 * @throws IllegalArgumentException If the agent configuration (`agentConfig`) is not set in the route.
 */
public suspend fun <Input, Output> RoutingContext.aiAgent(
    inputType: KType,
    outputType: KType,
    strategy: AIAgentGraphStrategy<Input, Output>,
    model: LLModel,
    tools: ToolRegistry = ToolRegistry.EMPTY,
): AIAgent<Input, Output> {
    val plugin = requireNotNull(call.application.pluginOrNull(Koog)) { "Plugin $Koog is not configured" }

    return GraphAIAgent(
        inputType = inputType,
        outputType = outputType,
        promptExecutor = plugin.promptExecutor,
        strategy = strategy,
        agentConfig = plugin.agentConfig(model),
        toolRegistry = plugin.agentConfig.toolRegistry + tools,
    )
}

/**
 * Creates an AI agent using the provided AI agent strategy within the specified route.
 *
 * @param Input The type of input data for the AI agent.
 * @param Output The type of output data for the AI agent.
 * @param strategy The AI agent strategy defining the workflow and execution logic of the agent.
 * @return An instance of `AIAgent` configured with the specified strategy and the route's resources.
 * @throws IllegalArgumentException If the agent configuration (`agentConfig`) is not set in the route.
 */
public suspend inline fun <reified Input, reified Output> RoutingContext.aiAgent(
    strategy: AIAgentGraphStrategy<Input, Output>,
    model: LLModel,
    tools: ToolRegistry = ToolRegistry.EMPTY,
): AIAgent<Input, Output> = aiAgent(typeOf<Input>(), typeOf<Output>(), strategy, model, tools)

/**
 * Creates an agent using [aiAgent], and immediately runs it given the [input].
 * When the agent is completed it provides the final [Output].
 */
public suspend inline fun <reified Input, reified Output> RoutingContext.aiAgent(
    strategy: AIAgentGraphStrategy<Input, Output>,
    model: LLModel,
    input: Input
): Output = aiAgent(strategy, model) { it.run(input) }

/**
 * Creates an AI agent using the provided AI agent strategy within the specified route.
 *
 * @param Input The type of input data for the AI agent.
 * @param Output The type of output data for the AI agent.
 * @param strategy The AI agent strategy defining the workflow and execution logic of the agent.
 * @return An instance of `AIAgent` configured with the specified strategy and the route's resources.
 * @throws IllegalArgumentException If the agent configuration (`agentConfig`) is not set in the route.
 */
public suspend inline fun <reified Input, reified Output, Result> RoutingContext.aiAgent(
    strategy: AIAgentGraphStrategy<Input, Output>,
    model: LLModel,
    block: suspend (agent: AIAgent<Input, Output>) -> Result
): Result = aiAgent(strategy, model).use(block)

/**
 * A default `aiAgent` is an agent that runs using [singleRunStrategy], by default, it relies on sequential [ToolCalls].
 * Inside the [block] lambda you can use the agent to perform tasks, and calculate a result, such as [GraphAIAgent.run].
 */
public suspend fun <Result> RoutingContext.aiAgent(
    runMode: ToolCalls = ToolCalls.SINGLE_RUN_SEQUENTIAL,
    model: LLModel,
    block: suspend (agent: AIAgent<String, String>) -> Result
): Result = aiAgent(singleRunStrategy(runMode), model).use(block)

/**
 * A default `aiAgent` is an agent that runs using [singleRunStrategy], by default, it relies on sequential [ToolCalls].
 * It takes an [input], and when the agent finishes running provides a final result [String].
 */
public suspend fun RoutingContext.aiAgent(
    input: String,
    model: LLModel,
    runMode: ToolCalls = ToolCalls.SINGLE_RUN_SEQUENTIAL,
): String = aiAgent(runMode, model) { it.run(input) }
