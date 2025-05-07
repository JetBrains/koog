package ai.grazie.code.agents.example.structureddata

import ai.grazie.code.agents.core.tools.SimpleToolRegistry
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.dsl.builders.forwardTo
import ai.grazie.code.agents.local.dsl.builders.simpleStrategy
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking


fun main() = runBlocking {
    val executor: PromptExecutor = simpleOpenAIExecutor(TokenService.openAIToken)

    val agentStrategy = simpleStrategy("library-assistant") {
        val getMdOutput by node<Unit, String> { _ ->
            val mdDefinition = markdownBookDefinition()

            llm.writeSession {
                val markdownStream = requestLLMStreaming(mdDefinition)

                parseMarkdownStreamToBooks(markdownStream).collect { book ->
                    callToolRaw(BookTool.NAME, book)
                    /* Other possible options:
                        callTool(BookTool::class, book)
                        callTool<BookTool>(book)
                        findTool(BookTool::class).execute(book)
                    */
                }

//              Also we can spawn parallel tool calls and not wait on a fly !!!
                parseMarkdownStreamToBooks(markdownStream).toParallelToolCallsRaw(BookTool::class).collect()
//                parseMarkdownStreamToBooks(markdownStream).toParallelToolCalls(BookTool::class).filter {
//                    it is SafeTool.Result.Success<ToolResult.Text>
//                }.toList()
            }
            ""
        }

        edge(nodeStart forwardTo getMdOutput)
        edge(getMdOutput forwardTo nodeFinish)
    }

    val token = System.getenv("GRAZIE_TOKEN") ?: error("Environment variable GRAZIE_TOKEN is not set")

    val toolRegistry = SimpleToolRegistry {
        tool(BookTool())
    }

    val agentConfig = LocalAgentConfig.withSystemPrompt(
        prompt = """
            You're AI library assistant. Please provide users with comprehensive and structured information about the books of the world.
        """.trimIndent()
    )

    val runner = KotlinAIAgent(
        promptExecutor = executor,
        toolRegistry = toolRegistry, // no tools needed for this example
        strategy = agentStrategy,
        agentConfig = agentConfig,
        cs = this,
    )

    runner.run("Please provide a list of the top 10 books in the world.")
}
