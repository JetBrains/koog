package ai.koog.agents.example.chatmemory

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

/**
 * Demonstrates the ChatMemory feature which persists conversation history across
 * multiple agent runs.
 *
 * Each call to `agent.run()` is a separate interaction, but ChatMemory ensures
 * that previous messages are loaded before each run and saved after, so the LLM
 * sees the full conversation context.
 *
 * Type `/bye` to exit the chat loop.
 */
suspend fun main() {
    val sessionId = "my-conversation"
    val historyProvider = InMemoryChatHistoryProvider()

    simpleOpenAIExecutor(ApiKeyService.openAIApiKey).use { executor ->
        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a friendly assistant. Keep your answers concise.",
        ) {
            install(ChatMemory) {
                chatHistoryProvider = historyProvider
            }
        }

        println("Chat with memory started. The agent remembers previous messages.")
        println("Type /bye to quit.\n")

        while (true) {
            print("You: ")
            val input = readln().trim()
            if (input == "/bye") break
            if (input.isEmpty()) continue

            val reply = agent.run(input, sessionId)
            println("Assistant: $reply\n")
        }

        println("Goodbye!")
    }
}
