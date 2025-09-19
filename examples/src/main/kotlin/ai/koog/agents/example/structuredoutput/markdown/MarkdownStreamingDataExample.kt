package ai.koog.agents.example.structuredoutput.markdown

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.streaming.filterTextOnly
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val executor: PromptExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey)

    val agentStrategy = strategy<String, String>("library-assistant") {
        val getMdOutput by node<String, String> { input ->
            val books = mutableListOf<Book>()
            val mdDefinition = markdownBookDefinition()

            llm.writeSession {
                updatePrompt { user(input) }
                val markdownStream = requestLLMStreaming(mdDefinition).filterTextOnly()
                parseMarkdownStreamToBooks(markdownStream).collect { book ->
                    books.add(book)
                    println("Parsed Book: ${book.bookName} by ${book.author}")
                }
            }

            formatOutput(books)
        }

        edge(nodeStart forwardTo getMdOutput)
        edge(getMdOutput forwardTo nodeFinish)
    }

    val agentConfig = AIAgentConfig.withSystemPrompt(
        prompt = """
            You're AI library assistant. Please provide users with comprehensive and structured information about the books of the world.
        """.trimIndent()
    )

    val runner = AIAgent(
        promptExecutor = executor,
        strategy = agentStrategy, // no tools needed for this example
        agentConfig = agentConfig,
    )

    val res = runner.run("Please provide a list of the top 10 books in the world.")
    println("Final Result:\n$res")
}
