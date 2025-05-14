package ai.grazie.code.agents.example.redcode

import ai.grazie.code.agents.core.agent.AIAgentBase
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.agent.entity.ToolSelectionStrategy
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.dsl.extension.nodeExecuteTool
import ai.grazie.code.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.grazie.code.agents.core.dsl.extension.onAssistantMessage
import ai.grazie.code.agents.core.dsl.extension.onToolCall
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.tools.tools.ToolStage
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.local.features.eventHandler.feature.EventHandler
import ai.grazie.code.prompt.structure.json.JsonSchemaGenerator
import ai.grazie.code.prompt.structure.json.JsonStructuredData
import ai.grazie.utils.annotations.ExperimentalAPI
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.jetbrains.code.prompt.llm.OllamaModels
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A simple agent that uses the SimpleKotlinRedCodeFixingTools to fix compilation errors in Kotlin code.
 */
@OptIn(ExperimentalAPI::class)
fun main() = runBlocking {
    val executor = simpleOpenAIExecutor(TokenService.openAIToken)

    // Define the project root path
    val projectRootPath = System.getProperty("user.dir")

    // Create tool registry with all the red code fixing tools
    val toolRegistry = ToolRegistry {
        stage {
            // List files with errors
            tool(SimpleKotlinListFilesWithErrorsTool(projectRootPath))

            // Find errors in a specific file
            tool(SimpleKotlinFindErrorsInFileTool(projectRootPath))

            // Read file text
            tool(SimpleKotlinReadFileTextTool(projectRootPath))

            // Edit file text
            tool(SimpleKotlinEditFileTextTool(projectRootPath))

            // Import fixing tools
            tool(SimpleKotlinListImportsInFileTool(projectRootPath))
            tool(SimpleKotlinAddImportsToFileTool(projectRootPath))
        }
    }

    // Define the strategy for the agent
    val strategy = strategy("red-code-fixing") {
        stage {
            // Main subgraph for fixing errors
            val fixErrors by subgraph(
                toolSelectionStrategy = ToolSelectionStrategy.Tools(
                    tools = listOfNotNull(
                        toolRegistry.getTool<SimpleKotlinListFilesWithErrorsTool>().descriptor,
                        toolRegistry.getTool<SimpleKotlinFindErrorsInFileTool>().descriptor,
                        toolRegistry.getTool<SimpleKotlinReadFileTextTool>().descriptor,
                        toolRegistry.getTool<SimpleKotlinEditFileTextTool>().descriptor,
                        toolRegistry.getTool<SimpleKotlinListImportsInFileTool>().descriptor,
                        toolRegistry.getTool<SimpleKotlinAddImportsToFileTool>().descriptor
                    )
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

            // Structured LLM call for summary
            val summarizeFixes by node<String, FixingResultWithMessage>("summarize_fixes") { task ->
                val structuredResponse = llm.writeSession {
                    updatePrompt {
                        user(task)
                    }

                    this.requestLLMStructured(
                        structure = JsonStructuredData.createJsonStructure<FixingResultWithMessage>(
                            schemaFormat = JsonSchemaGenerator.SchemaFormat.JsonSchema,
                            schemaType = JsonStructuredData.JsonSchemaType.SIMPLE
                        ),
                        fixingModel = OllamaModels.Meta.LLAMA_3_2
                    ).getOrThrow()
                }
                structuredResponse.structure
            }

            // Connect the nodes
            edge(nodeStart forwardTo fixErrors transformed {
                "Please help me fix compilation errors in this project. " +
                        "First, list all files with errors using the list_files_with_errors tool, " +
                        "then analyze and fix each error one by one."
            })

            edge(
                fixErrors forwardTo summarizeFixes
                        transformed { "Please provide a summary of the fixes you made to resolve the compilation errors." }
            )

            edge(
                summarizeFixes forwardTo nodeFinish
                        transformed { "Red code fixing completed: ${it.message}" }
            )
        }
    }

    val agentConfig = LocalAgentConfig(
        prompt = prompt("red-code-fixing") {
            system(
                """
                You are a senior software engineer with expertise in fixing compilation errors in Kotlin code.
                Your task is to identify and fix "red code" - code that doesn't compile due to syntax errors, 
                missing imports, undefined variables, type mismatches, or other compilation issues.
                
                Follow these steps:
                1. List all files with errors using the list_files_with_errors tool
                2. For each file with errors, find the specific errors using the find_errors_in_file tool
                3. Read the relevant parts of the file using the read_file_text tool
                4. Determine the appropriate fix for each error
                5. Apply the fix using the edit_file_text tool or add_imports_to_file tool
                6. Verify that the error has been resolved
                7. Continue until all errors are fixed
                
                Provide clear explanations of the errors and your fixes.
                """.trimIndent()
            )
        },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 100
    )

    // Create the agent
    val agent = AIAgentBase(
        promptExecutor = executor,
        strategy = strategy,
        cs = this,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
    ) {
        install(EventHandler) {
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

    // Run the agent
    val result = agent.run("Fix all compilation errors in the project")
    println("Agent completed with result: $result")
}

@Serializable
@SerialName("FixingResult")
data class FixingResultWithMessage(
    @SerialName("result")
    val result: Boolean,
    @SerialName("message")
    val message: String
) : ToolResult {
    override fun toStringDefault(): String {
        return Json.encodeToString(serializer(), this)
    }
}