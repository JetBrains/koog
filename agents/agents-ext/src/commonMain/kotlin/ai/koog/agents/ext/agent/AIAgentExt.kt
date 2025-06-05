package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams

/**
 * Creates and configures a `AIAgent` instance with a single-run strategy.
 *
 * @param executor The `PromptExecutor` responsible for executing the prompts.
 * @param systemPrompt The system-level prompt context for the agent. Default is an empty string.
 * @param llmModel The language model to be used by the agent. Default is `OpenAIModels.Chat.GPT4o`.
 * @param temperature The sampling temperature for the language model, controlling randomness. Default is 1.0.
 * @param toolRegistry The `ToolRegistry` containing tools available to the agent. Default is `ToolRegistry.EMPTY`.
 * @param maxIterations Maximum number of iterations for the agent's execution. Default is 50.
 * @param installFeatures A suspending lambda to install additional features for the agent's functionality. Default is an empty lambda.
 * @return A configured instance of `AIAgent` with a single-run execution strategy.
 */
@Deprecated("Use AIAgent constructor instead", ReplaceWith("AIAgent(...)"))
public fun simpleSingleRunAgent(
    executor: PromptExecutor,
    systemPrompt: String = "",
    llmModel: LLModel,
    temperature: Double = 1.0,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    maxIterations: Int = 50,
    installFeatures: AIAgent.FeatureContext.() -> Unit = {}
): AIAgent = AIAgent(
    systemPrompt = systemPrompt,
    llmModel = llmModel,
    temperature = temperature,
    toolRegistry = toolRegistry,
    maxIterations = maxIterations,
    installFeatures = installFeatures,
    strategy = singleRunStrategy(),
    executor = executor
)