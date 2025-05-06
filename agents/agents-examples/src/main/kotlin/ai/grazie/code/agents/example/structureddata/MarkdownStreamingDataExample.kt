package ai.grazie.code.agents.example.structureddata

import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.dsl.builders.forwardTo
import ai.grazie.code.agents.local.dsl.builders.simpleStrategy
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val executor: CodePromptExecutor = simpleOpenAIExecutor(TokenService.openAIToken)

    val agentStrategy = simpleStrategy("library-assistant") {
        val getMdOutput by node<Unit, String> { _ ->
            val books = mutableListOf<Book>()
            val mdDefinition = markdownBookDefinition()

            llm.writeSession {
                val markdownStream = requestLLMStreaming(mdDefinition)
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

    val token = System.getenv("GRAZIE_TOKEN") ?: error("Environment variable GRAZIE_TOKEN is not set")

    val agentConfig = LocalAgentConfig.withSystemPrompt(
        prompt = """
            You're AI library assistant. Please provide users with comprehensive and structured information about the books of the world.
        """.trimIndent()
    )

    val runner = KotlinAIAgent(
        promptExecutor = executor,
        toolRegistry = ToolRegistry.EMPTY, // no tools needed for this example
        strategy = agentStrategy,
        agentConfig = agentConfig,
        cs = this,
    )

    val res = runner.runAndGetResult("Please provide a list of the top 10 books in the world.")
    println("Final Result:\n$res")
}
