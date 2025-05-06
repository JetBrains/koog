package ai.grazie.code.agents.example.simpleapi

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.local.simpleApi.simpleSingleRunAgent
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * This example demonstrates how to create a basic single-run agent using the SimpleAPI.
 * The agent processes a single input and provides a response.
 */
fun main() = runBlocking {
    var result = ""
    val handler = EventHandler {
        handleResult { result = it!! }
    }
    // Create a single-run agent with a system prompt
    val agent = simpleSingleRunAgent(
        executor = simpleOpenAIExecutor(TokenService.openAIToken),
        cs = this,
        eventHandler = handler,
        systemPrompt = "You are a code assistant. Provide concise code examples."
    )

    println("Single-run agent started. Enter your request:")

    // Run the agent with the user request
    agent.run(readln())

    println("Agent completed. Result: $result")
}