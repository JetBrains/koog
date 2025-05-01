package ai.grazie.code.agents.example.simpleapi

import ai.grazie.code.agents.local.simpleApi.simpleChatAgent
import kotlinx.coroutines.runBlocking

/**
 * This example demonstrates how to create a basic chat agent using the SimpleAPI.
 * The agent maintains a conversation with the user until explicitly terminated.
 */
fun main() = runBlocking {
    // Get the API token from environment variable
    val apiToken = System.getenv("GRAZIE_TOKEN") ?: error("Environment variable GRAZIE_TOKEN is not set")

    // Create a chat agent with a system prompt
    val agent = simpleChatAgent(
        apiToken = apiToken,
        cs = this,
        systemPrompt = "You are a helpful assistant. Answer user questions concisely."
    )

    println("Chat started. Type your message and press Enter.")
    println("The agent will respond and continue the conversation until you type 'exit'.")

    // Start the conversation with an initial user message
    val initialMessage = readln()

    // Run the agent in a separate coroutine
    agent.run(initialMessage)
}