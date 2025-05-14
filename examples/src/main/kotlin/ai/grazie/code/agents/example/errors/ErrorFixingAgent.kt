package ai.grazie.code.agents.example.errors

import ai.grazie.code.agents.core.agent.AIAgent
import ai.grazie.code.agents.core.agent.config.AIAgentConfig
import ai.grazie.code.agents.core.agent.entity.ToolSelectionStrategy
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.dsl.extension.*
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.tools.tools.ToolStage
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.features.eventHandler.feature.handleEvents
import ai.grazie.utils.annotations.ExperimentalAPI
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.io.path.writeText


@OptIn(ExperimentalAPI::class)
fun main() = runBlocking {
    val executor: PromptExecutor = simpleOpenAIExecutor(TokenService.openAIToken)

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
            } transformed { it.result.text })
            edge(verifyCode forwardTo nodeFinish onSuccessful {
                it.text.contains("Passed!")
            } transformed { "Error was successfully fixed" })
            edge(
                verifyCode forwardTo nodeFinish onFailure { true }
                        transformed { "Problem happened when calling tool: ${it.message}" }
            )
        }
    }

    val agentConfig = AIAgentConfig(
        prompt = prompt("test") {
            system(
                """
                You are a senior software engineer with 10 years of experience in Kotlin.
                Find the errors in Kotlin code and fix them.
            """.trimIndent()
            )
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 50
    )

    // Create the runner
    val agent = AIAgent(
        promptExecutor = executor,
        strategy = strategy,
        cs = this,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
    ) {
        handleEvents {
            onToolCall = { stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args ->
                println("Tool called: stage ${stage.name}, tool ${tool.name}, args $toolArgs")
            }

            onAgentRunError = { strategyName: String, throwable: Throwable ->
                println("An error occurred: ${throwable.message}\n${throwable.stackTraceToString()}")
            }

            onAgentFinished = { strategyName: String, result: String? ->
                println("Result: $result")
            }
        }
    }

    agent.run(code)
}
