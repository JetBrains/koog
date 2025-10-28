package ai.koog.agents.example.simpleapi

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

/**
 * This example demonstrates how to create a basic single-run agent using the SimpleAPI.
 * The agent processes a single input and provides a response.
 */
suspend fun main() {
    var result: Any? = null
    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onAgentCompleted { eventContext -> result = eventContext.result }
    }

    simpleOpenAIExecutor(ApiKeyService.openAIApiKey).use { executor ->
        // Create a single-run agent with a system prompt
        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = OpenAIModels.CostOptimized.GPT4oMini,
            systemPrompt = "You are a code assistant. Provide concise code examples.",
        ) {
            install(EventHandler, eventHandlerConfig)
        }

        println("Single-run agent started. Enter your request:")

        // Run the agent with the user request
        agent.run(readln())

        println("Agent completed. Result: $result")
    }
}
