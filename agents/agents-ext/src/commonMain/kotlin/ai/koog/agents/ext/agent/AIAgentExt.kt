package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams

/**
 * Creates and configures a simple chat agent.
 *
 * @param executor [PromptExecutor] that would be executing all prompts (usually, using one or more LLM clients)
 * @param systemPrompt System-level instructions provided to the agent (default is an empty string).
 * @param llmModel The language model to be used by the agent (default is `OpenAIModels.Chat.GPT4o`).
 * @param temperature A value between 0.0 and 1.0 controlling the randomness of the responses (default is 1.0).
 * @param toolRegistry Optional registry of tools available to the agent (default includes basic tools such as AskUser and ExitTool).
 * @param maxIterations Maximum number of iterations the agent will perform in a single interaction loop (default is 50).
 * @param installFeatures A suspending lambda to install additional features for the agent's functionality (default is an empty lambda).
 * @return A configured instance of AIAgent ready for use.
 */
public fun simpleChatAgent(
    executor: PromptExecutor,
    systemPrompt: String = "",
    llmModel: LLModel,
    temperature: Double = 1.0,
    toolRegistry: ToolRegistry? = null,
    maxIterations: Int = 50,
    installFeatures: AIAgent.FeatureContext.() -> Unit = {}
): AIAgent {

    val agentConfig = AIAgentConfig(
        prompt = prompt("chat", params = LLMParams(temperature = temperature)) {
            system(systemPrompt)
        },
        model = llmModel,
        maxAgentIterations = maxIterations,
    )

    toolRegistry

    val resultingToolRegistry = toolRegistry ?: ToolRegistry { }
    resultingToolRegistry.addAll(AskUser, ExitTool)

    return AIAgent(
        promptExecutor = executor,
        strategy = chatAgentStrategy(),
        agentConfig = agentConfig,
        toolRegistry = resultingToolRegistry,
        installFeatures = installFeatures
    )
}

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
public fun simpleSingleRunAgent(
    executor: PromptExecutor,
    systemPrompt: String = "",
    llmModel: LLModel,
    temperature: Double = 1.0,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    maxIterations: Int = 50,
    installFeatures: AIAgent.FeatureContext.() -> Unit = {}
): AIAgent {

    val agentConfig = AIAgentConfig(
        prompt = prompt("chat", params = LLMParams(temperature = temperature)) {
            system(systemPrompt)
        },
        model = llmModel,
        maxAgentIterations = maxIterations,
    )

    return AIAgent(
        promptExecutor = executor,
        strategy = singleRunStrategy(),
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
        installFeatures = installFeatures
    )
}
