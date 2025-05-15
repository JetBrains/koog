package ai.grazie.code.agents.example.simpleapi

import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.core.api.simpleChatAgent
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * This example demonstrates how to create a basic chat agent using the SimpleAPI.
 * The agent maintains a conversation with the user until explicitly terminated.
 */
fun main() = runBlocking {
    // Create a chat agent with a system prompt
    val agent = simpleChatAgent(
        executor = simpleOpenAIExecutor(TokenService.openAIToken),
        systemPrompt = "You are a helpful assistant. Answer user questions concisely."
    )

    println("Chat started. Type your message and press Enter.")
    println("The agent will respond and continue the conversation until you type 'exit'.")

    // Start the conversation with an initial user message
    val initialMessage = readln()

    // Run the agent in a separate coroutine
    agent.run(initialMessage)
}