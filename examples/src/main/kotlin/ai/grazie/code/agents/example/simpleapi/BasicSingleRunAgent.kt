package ai.grazie.code.agents.example.simpleapi

import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.core.api.simpleSingleRunAgent
import ai.grazie.code.agents.local.features.eventHandler.feature.EventHandler
import ai.grazie.code.agents.local.features.eventHandler.feature.EventHandlerConfig
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * This example demonstrates how to create a basic single-run agent using the SimpleAPI.
 * The agent processes a single input and provides a response.
 */
fun main() = runBlocking {
    var result: String? = null
    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onAgentFinished = { _, agentResult -> result = agentResult }
    }
    // Create a single-run agent with a system prompt
    val agent = simpleSingleRunAgent(
        executor = simpleOpenAIExecutor(TokenService.openAIToken),
        systemPrompt = "You are a code assistant. Provide concise code examples.",
        installFeatures = { install(EventHandler, eventHandlerConfig) }
    )

    println("Single-run agent started. Enter your request:")

    // Run the agent with the user request
    agent.run(readln())

    println("Agent completed. Result: $result")
}
