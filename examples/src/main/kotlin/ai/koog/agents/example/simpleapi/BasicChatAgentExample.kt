package ai.koog.agents.example.simpleapi

import ai.koog.agents.example.TokenService
import ai.koog.agents.ext.agent.simpleChatAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * This example demonstrates how to create a basic chat agent using the SimpleAPI.
 * The agent maintains a conversation with the user until explicitly terminated.
 */
fun main() = runBlocking {
    // Create a chat agent with a system prompt
    val agent = simpleChatAgent(
        executor = simpleOpenAIExecutor(TokenService.openAIToken),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = "You are a helpful assistant. Answer user questions concisely."
    )

    println("Chat started. Type your message and press Enter.")
    println("The agent will respond and continue the conversation until you type 'exit'.")

    // Start the conversation with an initial user message
    val initialMessage = readln()

    // Run the agent in a separate coroutine
    agent.run(initialMessage)
}