package ai.koog.agents.example.client

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.simpleapi.Switch
import ai.koog.agents.example.simpleapi.SwitchTools
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Runs a simple Koog agent against [DaoXE](https://daoxe.com) through the OpenAI-compatible
 * Chat Completions path (`https://daoxe.com/v1`).
 *
 * DaoXE is a multi-model, multi-protocol API gateway. Besides OpenAI-compatible Chat Completions
 * (and Responses where available), it also exposes Anthropic Messages (Claude protocol) and other
 * catalog endpoints. This sample uses only the OpenAI-compatible surface so existing
 * `OpenAILLMClient` code works by changing the base URL, API key, and model id.
 *
 * Service availability: DaoXE does **not** serve mainland China. Use a region where the service is offered.
 *
 * Required environment variables:
 * - `DAOXE_API_KEY` — API key from your DaoXE account
 * - `DAOXE_MODEL` (optional) — model id from your DaoXE account catalog / pricing page
 *   (availability and naming follow the live account list; do not hardcode a fixed vendor list)
 */
suspend fun main() {
    val switch = Switch()

    val toolRegistry = ToolRegistry {
        tools(SwitchTools(switch).asTools())
    }

    // OpenAI client defaults to baseUrl "https://api.openai.com" and path "v1/chat/completions".
    // Point baseUrl at DaoXE so requests go to https://daoxe.com/v1/chat/completions.
    val daoxeSettings = OpenAIClientSettings(
        baseUrl = "https://daoxe.com",
    )

    val daoxeClient = OpenAILLMClient(
        apiKey = ApiKeyService.daoxeApiKey,
        settings = daoxeSettings,
    )

    // Model ids are account/catalog-specific. Prefer DAOXE_MODEL from the environment.
    val modelId = ApiKeyService.daoxeModelId
    val daoxeModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = modelId,
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Completion,
        ),
    )

    MultiLLMPromptExecutor(LLMProvider.OpenAI to daoxeClient).use { executor ->
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = singleRunStrategy(parallelTools = false),
            llmModel = daoxeModel,
            systemPrompt = "You're responsible for running a Switch and perform operations on it by request",
            temperature = 0.0,
            toolRegistry = toolRegistry,
        )

        println("DaoXE OpenAI-compatible agent (base https://daoxe.com/v1)")
        println("Using model id: $modelId (from DAOXE_MODEL or default placeholder)")
        println("DaoXE is multi-model and multi-protocol; this example uses Chat Completions only.")
        println("Service is not available in mainland China.")
        println("You can ask me to turn the switch on/off or check its current state.")
        println("Type your request:")

        val input = readln()
        println(agent.run(input))
    }
}
