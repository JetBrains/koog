package ai.koog.ktor

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.utils.use
import ai.koog.prompt.executor.model.PromptExecutor
import io.ktor.server.application.Application
import io.ktor.server.application.pluginOrNull
import io.ktor.server.routing.RoutingContext

private val Application.koogPlugin: Koog
    get() = requireNotNull(pluginOrNull(Koog)) { "Plugin $Koog is not configured" }

private val RoutingContext.koogPlugin: Koog
    get() = call.application.koogPlugin

/**
 * Retrieve the configured llm, or [PromptExecutor] instance from the underlying [Koog] plugin.
 */
public fun RoutingContext.llm(): PromptExecutor =
    koogPlugin.promptExecutor

/**
 * Creates an AI agent using the provided AI agent strategy within the specified route.
 *
 * @param Input The type of input data for the AI agent.
 * @param Output The type of output data for the AI agent.
 * @param strategy The AI agent strategy defining the workflow and execution logic of the agent.
 * @return An instance of `AIAgent` configured with the specified strategy and the route's resources.
 * @throws IllegalArgumentException If the agent configuration (`agentConfig`) is not set in the route.
 */
public fun <Input, Output> RoutingContext.aiAgent(strategy: AIAgentStrategy<Input, Output>): AIAgent<Input, Output> =
    AIAgent(
        promptExecutor = koogPlugin.promptExecutor,
        strategy = strategy,
        agentConfig = koogPlugin.agentConfig,
        toolRegistry = koogPlugin.tools,
    )

/**
 * Creates an agent using [aiAgent], and immediately runs it given the [input].
 * When the agent is completed it provides the final [Output].
 */
public suspend fun <Input, Output> RoutingContext.aiAgent(
    strategy: AIAgentStrategy<Input, Output>,
    input: Input
): Output = aiAgent(strategy) { it.run(input) }

/**
 * Creates an AI agent using the provided AI agent strategy within the specified route.
 *
 * @param Input The type of input data for the AI agent.
 * @param Output The type of output data for the AI agent.
 * @param strategy The AI agent strategy defining the workflow and execution logic of the agent.
 * @return An instance of `AIAgent` configured with the specified strategy and the route's resources.
 * @throws IllegalArgumentException If the agent configuration (`agentConfig`) is not set in the route.
 */
public suspend fun <Input, Output, Result> RoutingContext.aiAgent(
    strategy: AIAgentStrategy<Input, Output>,
    block: suspend (agent: AIAgent<Input, Output>) -> Result
): Result =
    AIAgent(
        promptExecutor = koogPlugin.promptExecutor,
        strategy = strategy,
        agentConfig = koogPlugin.agentConfig,
        toolRegistry = koogPlugin.tools,
    ).use(block)

/**
 * A `simpleRungAgent` is an agent that runs using [singleRunStrategy], by default, it relies on sequential [ToolCalls].
 * Inside the [block] lambda you can use the agent to perform tasks, and calculate a result, such as [AIAgent.run].
 */
public suspend fun <Result> RoutingContext.singleRunAgent(
    runMode: ToolCalls = ToolCalls.SINGLE_RUN_SEQUENTIAL,
    block: suspend (agent: AIAgent<String, String>) -> Result
): Result =
    AIAgent(
        promptExecutor = koogPlugin.promptExecutor,
        strategy = singleRunStrategy(runMode),
        agentConfig = koogPlugin.agentConfig,
        toolRegistry = koogPlugin.tools,
    ).use(block)

/**
 * A `simpleRungAgent` is an agent that runs using [singleRunStrategy], by default, it relies on sequential [ToolCalls].
 * It takes an [input], and when the agent finishes running provides a final result [String].
 */
public suspend fun RoutingContext.singleRunAgent(
    input: String,
    runMode: ToolCalls = ToolCalls.SINGLE_RUN_SEQUENTIAL,
): String = singleRunAgent(runMode) { it.run(input) }
