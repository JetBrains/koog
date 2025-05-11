package ai.grazie.code.agents.core.api

import ai.grazie.code.agents.core.agent.AIAgentBase
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.tools.AskUser
import ai.grazie.code.agents.core.tools.tools.ExitTool
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.params.LLMParams
import kotlinx.coroutines.CoroutineScope

/**
 * Creates and configures a simple chat agent.
 *
 * @param apiToken The API token used for authentication with the LLM system.
 * @param cs The coroutine scope within which the agent will operate.
 * @param systemPrompt System-level instructions provided to the agent (default is an empty string).
 * @param llmModel The language model to be used by the agent (default is OpenAIModels.General.GPT4o).
 * @param temperature A value between 0.0 and 1.0 controlling the randomness of the responses (default is 1.0).
 * @param eventHandler Optional event handler for managing results, errors, and tool-specific events (default is null).
 * @param toolRegistry Optional registry of tools available to the agent (default includes basic tools such as AskUser and ExitTool).
 * @param maxIterations Maximum number of iterations the agent will perform in a single interaction loop (default is 50).
 * @param llmApi The Grazie environment to use for the LLM connection (default is GrazieEnvironment.Production).
 * @return A configured instance of KotlinAIAgent ready for use.
 */
fun simpleChatAgent(
    executor: PromptExecutor,
    cs: CoroutineScope,
    systemPrompt: String = "",
    llmModel: LLModel = OpenAIModels.General.GPT4o,
    temperature: Double = 1.0,
    eventHandler: EventHandler = EventHandler.NO_HANDLER,
    toolRegistry: ToolRegistry? = null,
    maxIterations: Int = 50,
): AIAgentBase {

    val agentConfig = LocalAgentConfig(
        prompt = prompt("chat", params = LLMParams(temperature = temperature)) {
            system(systemPrompt)
        },
        model = llmModel,
        maxAgentIterations = maxIterations,
    )

    val resultingToolRegistry = if (toolRegistry == null) ToolRegistry {
        stage {
            tool(AskUser)
            tool(ExitTool)
        }
    } else
        ToolRegistry {
            stage {
                tool(AskUser)
                tool(ExitTool)
            }
        } with toolRegistry

    return AIAgentBase(
        promptExecutor = executor,
        strategy = chatAgentStrategy(),
        cs = cs,
        agentConfig = agentConfig,
        toolRegistry = resultingToolRegistry,
        eventHandler = eventHandler
    )
}

/**
 * Creates and configures a `KotlinAIAgent` instance with a single-run strategy.
 *
 * @param executor The `PromptExecutor` responsible for executing the prompts.
 * @param cs The `CoroutineScope` used to manage coroutine operations.
 * @param systemPrompt The system-level prompt context for the agent. Default is an empty string.
 * @param llmModel The language model to be used by the agent. Default is `OpenAIModels.General.GPT4o`.
 * @param temperature The sampling temperature for the language model, controlling randomness. Default is 1.0.
 * @param eventHandler The `EventHandler` to handle events such as initialization, results, and errors. Default is `EventHandler.NO_HANDLER`.
 * @param toolRegistry The `ToolRegistry` containing tools available to the agent. Default is `ToolRegistry.EMPTY`.
 * @param maxIterations Maximum number of iterations for the agent's execution. Default is 50.
 * @param installFeatures A suspending lambda to install additional features for the agent's functionality. Default is an empty lambda.
 * @return A configured instance of `KotlinAIAgent` with a single-run execution strategy.
 */
fun simpleSingleRunAgent(
    executor: PromptExecutor,
    cs: CoroutineScope,
    systemPrompt: String = "",
    llmModel: LLModel = OpenAIModels.General.GPT4o,
    temperature: Double = 1.0,
    eventHandler: EventHandler = EventHandler.NO_HANDLER,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    maxIterations: Int = 50,
    installFeatures: suspend AIAgentBase.FeatureContext.() -> Unit = {}
): AIAgentBase {

    val agentConfig = LocalAgentConfig(
        prompt = prompt("chat", params = LLMParams(temperature = temperature)) {
            system(systemPrompt)
        },
        model = llmModel,
        maxAgentIterations = maxIterations,
    )

    return AIAgentBase(
        promptExecutor = executor,
        strategy = singleRunStrategy(),
        cs = cs,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
        eventHandler = eventHandler,
        installFeatures = installFeatures
    )
}
