package ai.grazie.code.agents.example.structureddata

import ai.grazie.code.agents.core.agent.AIAgent
import ai.grazie.code.agents.core.agent.config.AIAgentConfig
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.example.TokenService
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking


fun main() = runBlocking {
    val executor: PromptExecutor = simpleOpenAIExecutor(TokenService.openAIToken)

    val agentStrategy = strategy("library-assistant") {
        val getMdOutput by node<String, String> { input ->
            val mdDefinition = markdownBookDefinition()

            llm.writeSession {
                updatePrompt { user(input) }
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

    val toolRegistry = ToolRegistry {
        tool(BookTool())
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
        toolRegistry = toolRegistry,
    )

    runner.run("Please provide a list of the top 10 books in the world.")
}
