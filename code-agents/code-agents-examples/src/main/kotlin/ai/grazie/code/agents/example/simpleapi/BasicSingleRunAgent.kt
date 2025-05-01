package ai.grazie.code.agents.example.simpleapi

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.local.simpleApi.simpleSingleRunAgent
import kotlinx.coroutines.runBlocking

/**
 * This example demonstrates how to create a basic single-run agent using the SimpleAPI.
 * The agent processes a single input and provides a response.
 */
fun main() = runBlocking {
    // Get the API token from environment variable
    val apiToken = System.getenv("GRAZIE_TOKEN") ?: error("Environment variable GRAZIE_TOKEN is not set")

    var result = ""
    val handler = EventHandler {
        handleResult { result = it!! }
    }
    // Create a single-run agent with a system prompt
    val agent = simpleSingleRunAgent(
        apiToken = apiToken,
        cs = this,
        eventHandler = handler,
        systemPrompt = "You are a code assistant. Provide concise code examples."
    )

    println("Single-run agent started. Enter your request:")

    // Run the agent with the user request
    agent.run(readln())

    println("Agent completed. Result: $result")
}