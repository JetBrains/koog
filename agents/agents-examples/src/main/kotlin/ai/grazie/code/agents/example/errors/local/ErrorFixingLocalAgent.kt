package ai.grazie.code.agents.example.errors.local

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.example.errors.RunTestErrorFixingToolImpl
import ai.grazie.code.agents.example.errors.SearchReplaceToolImpl
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.dsl.builders.forwardTo
import ai.grazie.code.agents.local.dsl.builders.strategy
import ai.grazie.code.agents.local.dsl.extensions.*
import ai.grazie.code.agents.local.graph.ToolSelectionStrategy
import ai.grazie.code.agents.tools.registry.tools.ErrorFixingTools
import ai.grazie.utils.annotations.ExperimentalAPI
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor
import ai.jetbrains.code.prompt.llm.OllamaModels
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.io.path.writeText


@OptIn(ExperimentalAPI::class)
fun main() = runBlocking {
    val executor: CodePromptExecutor = null!!

    val code = """
        fun main() {
            file = "Hello, World!"
            println(file)
        }
    """.trimIndent()
    val directory = createTempDirectory(prefix = "error-fixing-example-")
    val file = directory.resolve("Main.kt").also { it.writeText(code) }
    val regex = Regex("va[rl]\\s+file")

    val toolRegistry = ToolRegistry {
        stage {
            tool(SearchReplaceToolImpl(file))
            tool(RunTestErrorFixingToolImpl(file, regex))
        }
    }

    val strategy = strategy("error-fixing") {
        stage {
            val fixError by subgraph(
                toolSelectionStrategy = ToolSelectionStrategy.Tools(
                    tools = listOfNotNull(toolRegistry.getTool<SearchReplaceToolImpl>().descriptor)
                )
            ) {
                val nodeSendInput by node<String, Message.Response> { input ->
                    llm.writeSession {
                        updatePrompt {
                            user(input)
                        }

                        requestLLM()
                    }
                }
                val nodeExecuteTool by nodeExecuteTool()
                val nodeSendToolResult by nodeLLMSendToolResult()

                edge(nodeStart forwardTo nodeSendInput transformed { it })
                edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true } transformed {})
                edge(nodeExecuteTool forwardTo nodeSendToolResult)
                edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
                edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true } transformed {})
            }

            val verifyCode by nodeExecuteSingleTool<ErrorFixingTools.RunTestTool.Args, ToolResult.Text>(
                tool = toolRegistry.getTool<RunTestErrorFixingToolImpl>(),
            )

            edge(nodeStart forwardTo fixError transformed { stageInput })
            edge(fixError forwardTo verifyCode transformed { ErrorFixingTools.RunTestTool.Args(file.name) })
            edge(verifyCode forwardTo fixError onSuccessful {
                it.text.contains("Failed!")
            } transformed { it.asSuccessful().result.text })
            edge(verifyCode forwardTo nodeFinish onSuccessful {
                it.text.contains("Passed!")
            } transformed { "Error was successfully fixed" })
            edge(
                verifyCode forwardTo nodeFinish onFailure { true }
                        transformed { "Problem happened when calling tool: ${it.message}" }
            )
        }
    }

    // Create event handler for logging
    val eventHandler = EventHandler {
        onToolCall { stage, tool, args ->
            println("Tool called: stage ${stage.name}, tool ${tool.name}, args $args")
        }

        handleError {
            println("An error occurred: ${it.message}\n${it.stackTraceToString()}")
            true
        }

        handleResult {
            println("Result: $it")
        }
    }

    val agentConfig = LocalAgentConfig(
        prompt = prompt(OllamaModels.Meta.LLAMA_3_2, "test") {
            system(
                """
                You are a senior software engineer with 10 years of experience in Kotlin.
                Find the errors in Kotlin code and fix them.
            """.trimIndent()
            )
        },
        maxAgentIterations = 50
    )

    // Create the runner
    val agent = KotlinAIAgent(
        toolRegistry = toolRegistry,
        strategy = strategy,
        eventHandler = eventHandler,
        promptExecutor = executor,
        agentConfig = agentConfig,
        cs = this
    )

    agent.run(code)
}
