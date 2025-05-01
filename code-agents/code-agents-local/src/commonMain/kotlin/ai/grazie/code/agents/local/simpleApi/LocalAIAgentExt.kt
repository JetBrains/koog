package ai.grazie.code.agents.local.simpleApi

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.grazie.GrazieEnvironment
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.llm.OllamaModels
import ai.jetbrains.code.prompt.params.LLMParams
import kotlinx.coroutines.CoroutineScope

/**
 * Creates and configures a simple chat agent.
 *
 * @param apiToken The API token used for authentication with the LLM system.
 * @param cs The coroutine scope within which the agent will operate.
 * @param systemPrompt System-level instructions provided to the agent (default is an empty string).
 * @param llmModel The language model to be used by the agent (default is JetBrainsAIModels.OpenAI.GPT4oMini).
 * @param temperature A value between 0.0 and 1.0 controlling the randomness of the responses (default is 1.0).
 * @param eventHandler Optional event handler for managing results, errors, and tool-specific events (default is null).
 * @param toolRegistry Optional registry of tools available to the agent (default includes basic tools such as AskUser and ExitTool).
 * @param maxIterations Maximum number of iterations the agent will perform in a single interaction loop (default is 50).
 * @param llmApi The Grazie environment to use for the LLM connection (default is GrazieEnvironment.Production).
 * @return A configured instance of KotlinAIAgent ready for use.
 */
fun simpleChatAgent(
    executor: CodePromptExecutor,
    cs: CoroutineScope,
    systemPrompt: String = "",
    llmModel: LLModel = OllamaModels.Meta.LLAMA_3_2,
    temperature: Double = 1.0,
    eventHandler: EventHandler = EventHandler.NO_HANDLER,
    toolRegistry: ToolRegistry? = null,
    maxIterations: Int = 50,
    ): KotlinAIAgent {

    val agentConfig = LocalAgentConfig(
        prompt = prompt(llmModel, "chat", params = LLMParams(temperature = temperature)) {
            system(systemPrompt)
        },
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

    return KotlinAIAgent(
        toolRegistry = resultingToolRegistry,
        strategy = chatAgentStrategy(),
        eventHandler = eventHandler,
        agentConfig = agentConfig,
        promptExecutor = executor,
        cs = cs
    )
}

/**
 * Creates and configures a single-run AI agent instance.
 *
 * @param apiToken A string representing the API token for authentication.
 * @param cs The CoroutineScope instance used for managing coroutines.
 * @param systemPrompt An optional system prompt to guide the agent's behavior. Defaults to an empty string.
 * @param llmModel The language model to be used by the agent. Defaults to JetBrainsAIModels.OpenAI.GPT4oMini.
 * @param temperature A double value denoting the randomness of the model's responses. Defaults to 1.0.
 * @param eventHandler An optional EventHandler to manage and delegate specific events. Defaults to null.
 * @param toolRegistry An optional ToolRegistry for managing tools associated with the agent. Defaults to null.
 * @param maxIterations The maximum number of iterations the agent is allowed to perform. Defaults to 50.
 * @param llmApi The GrazieEnvironment specifying the environment for the LLM API. Defaults to GrazieEnvironment.Production.
 * @return An instance of KotlinAIAgent configured according to the specified parameters.
 */
fun simpleSingleRunAgent(
    executor: CodePromptExecutor,
    cs: CoroutineScope,
    systemPrompt: String = "",
    llmModel: LLModel = OllamaModels.Meta.LLAMA_3_2,
    temperature: Double = 1.0,
    eventHandler: EventHandler = EventHandler.NO_HANDLER,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    maxIterations: Int = 50,
    installFeatures: suspend KotlinAIAgent.FeatureContext.() -> Unit= {}
): KotlinAIAgent {

    val agentConfig = LocalAgentConfig(
        prompt = prompt(llmModel, "chat", params = LLMParams(temperature = temperature)) {
            system(systemPrompt)
        },
        maxAgentIterations = maxIterations,
    )

    return KotlinAIAgent(
        toolRegistry = toolRegistry,
        strategy = singleRunStrategy(),
        eventHandler = eventHandler,
        agentConfig = agentConfig,
        promptExecutor = executor,
        cs = cs,
        installFeatures = installFeatures
    )
}
