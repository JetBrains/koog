package ai.koog.agents.example.chatmemory

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

/**
 * Demonstrates the ChatMemory feature with a sliding window that limits
 * the number of messages kept in the conversation history.
 *
 * Setting a window size prevents unbounded prompt growth in long conversations
 * by keeping only the N most recent messages when loading history into the
 * prompt and when persisting it after each run.
 *
 * Type `/bye` to exit the chat loop.
 */
suspend fun main() {
    val sessionId = "my-windowed-conversation"
    val historyProvider = InMemoryChatHistoryProvider()

    simpleOpenAIExecutor(ApiKeyService.openAIApiKey).use { executor ->
        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a friendly assistant. Keep your answers concise.",
        ) {
            install(ChatMemory) {
                chatHistoryProvider = historyProvider
                windowSize(20) // keep only the last 20 messages
            }
        }

        println("Chat with windowed memory started (window size = 20).")
        println("Only the most recent 20 messages are kept across runs.")
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
