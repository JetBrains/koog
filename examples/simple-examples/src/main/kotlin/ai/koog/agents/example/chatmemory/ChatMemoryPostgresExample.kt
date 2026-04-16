package ai.koog.agents.example.chatmemory

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.features.chatmemory.sql.PostgresChatHistoryProvider
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import org.jetbrains.exposed.sql.Database

/**
 * Demonstrates the ChatMemory feature backed by a PostgreSQL database.
 *
 * Conversation history is persisted across process restarts, so the agent
 * remembers previous messages even after the application is stopped and restarted.
 *
 * Prerequisites:
 * - A PostgreSQL instance running on localhost:5432
 * - A database named "koog" (or change the URL below)
 * - Credentials: user "postgres", password "postgres" (or change below)
 *
 * Quick setup with Docker:
 * ```
 * docker run -d --name koog-pg \
 *   -e POSTGRES_DB=koog \
 *   -e POSTGRES_PASSWORD=postgres \
 *   -p 5432:5432 \
 *   postgres:16
 * ```
 *
 * Type `/bye` to exit the chat loop.
 */
// docker run -d --name koog-pg -e POSTGRES_DB=koog -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16
suspend fun main() {
    // 1. Connect to PostgreSQL
    val database = Database.connect(
        url = "jdbc:postgresql://localhost:5432/koog",
        driver = "org.postgresql.Driver",
        user = "postgres",
        password = "postgres"
    )

    // 2. Create a PostgreSQL-backed history provider
    val historyProvider = PostgresChatHistoryProvider.builder()
        .database(database)
        .tableName("chat_history")
        .ttlSeconds(86400) // optional: conversations expire after 24 hours
        .build()

    // 3. Run schema migration (creates table + indexes if they don't exist)
    historyProvider.migrate()

    // 4. Create the agent with ChatMemory installed
    simpleOpenAIExecutor(ApiKeyService.openAIApiKey).use { executor ->
        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a friendly assistant. Keep your answers concise.",
        ) {
            install(ChatMemory) {
                chatHistoryProvider = historyProvider
                windowSize(50) // keep last 50 messages to avoid unbounded prompt growth
            }
        }

        println("Chat with PostgreSQL-backed memory started.")
        println("History persists across restarts. Type /bye to quit.\n")

        val sessionId = "pg-conversation"

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
