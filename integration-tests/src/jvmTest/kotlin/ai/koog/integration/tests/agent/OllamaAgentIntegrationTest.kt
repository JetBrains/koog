package ai.koog.integration.tests.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.agentInput
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.integration.tests.InjectOllamaTestFixture
import ai.koog.integration.tests.OllamaTestFixture
import ai.koog.integration.tests.OllamaTestFixtureExtension
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.annotations.Retry
import ai.koog.integration.tests.utils.annotations.RetryExtension
import ai.koog.integration.tests.utils.tools.AnswerVerificationTool
import ai.koog.integration.tests.utils.tools.FileOperationsTools
import ai.koog.integration.tests.utils.tools.GenericParameterTool
import ai.koog.integration.tests.utils.tools.GeographyQueryTool
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.LLMBasedToolCallFixProcessor
import ai.koog.prompt.processor.ResponseProcessor
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.BeforeTest
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@ExtendWith(OllamaTestFixtureExtension::class)
@ExtendWith(RetryExtension::class)
class OllamaAgentIntegrationTest : AIAgentTestBase() {
    companion object {
        @field:InjectOllamaTestFixture
        private lateinit var fixture: OllamaTestFixture
        private val executor get() = fixture.executor
        private val model get() = fixture.model
        private val modelsWithHallucinations get() = fixture.modelsWithHallucinations

        @JvmStatic
        private fun modelsWithHallucinations(): Stream<LLModel> =
            Stream.of(*modelsWithHallucinations.toTypedArray())
    }

    @Serializable
    private data class Summary(val summary: String)

    @BeforeTest
    fun clearToolCalls() {
        toolCalls.clear()
    }

    private val toolCalls = mutableListOf<String>()

    private fun createTestStrategy() = strategy<String, String>("test-ollama") {
        val askCapitalSubgraph by subgraph<String, String>("ask-capital") {
            val definePrompt by node<Unit, Unit> {
                llm.writeSession {
                    model = OllamaModels.Meta.LLAMA_3_2
                    rewritePrompt {
                        prompt("test-ollama") {
                            system(
                                """
                                        You are a top-tier geographical assistant. " +
                                            ALWAYS communicate to user via tools!!!
                                            ALWAYS use tools you've been provided.
                                            ALWAYS generate valid JSON responses.
                                            ALWAYS call tool correctly, with valid arguments.
                                            NEVER provide tool call in result body.

                                            Example tool call:
                                            {
                                                "id":"ollama_tool_call_3743609160",
                                                "tool":"geography_query_tool",
                                                "content":{"query":"capital of France"}
                                            }
                                """.trimIndent()
                            )
                        }
                    }
                }
            }

            val callLLM by nodeLLMRequest(allowToolCalls = true)
            val callTool by nodeExecuteTool()
            val sendToolResult by nodeLLMSendToolResult()

            edge(nodeStart forwardTo definePrompt transformed {})
            edge(definePrompt forwardTo callLLM transformed { agentInput<String>() })
            edge(callLLM forwardTo callTool onToolCall { true })
            edge(callTool forwardTo sendToolResult)
            edge(sendToolResult forwardTo callTool onToolCall { true })
            edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
        }

        val askVerifyAnswer by subgraph<String, String>("verify-answer") {
            val definePrompt by node<Unit, Unit> {
                llm.writeSession {
                    model = OllamaModels.Meta.LLAMA_3_2
                    appendPrompt {
                        system(
                            """"
                                    You are a top-tier assistant.
                                    ALWAYS communicate to user via tools!!!
                                    ALWAYS use tools you've been provided.
                                    ALWAYS generate valid JSON responses.
                                    ALWAYS call tool correctly, with valid arguments.
                                    NEVER provide tool call in result body.
                                  
                                    Example tool call:
                                    {
                                        "id":"ollama_tool_call_3743609160",
                                        "tool":"answer_verification_tool",
                                        "content":{"answer":"Paris"}
                                    }.
                            """.trimIndent()
                        )
                    }
                }
            }

            val callLLM by nodeLLMRequest(allowToolCalls = true)
            val callTool by nodeExecuteTool()
            val sendToolResult by nodeLLMSendToolResult()

            edge(nodeStart forwardTo definePrompt transformed {})
            edge(definePrompt forwardTo callLLM transformed { agentInput<String>() })
            edge(callLLM forwardTo callTool onToolCall { true })
            edge(callTool forwardTo sendToolResult)
            edge(sendToolResult forwardTo callTool onToolCall { true })
            edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
        }

        nodeStart then askCapitalSubgraph then askVerifyAnswer then nodeFinish
    }

    private fun createToolRegistry(): ToolRegistry {
        return ToolRegistry {
            tool(GeographyQueryTool)
            tool(AnswerVerificationTool)
            tool(GenericParameterTool)
        }
    }

    private fun createAgent(
        executor: PromptExecutor,
        strategy: AIAgentGraphStrategy<String, String>,
        toolRegistry: ToolRegistry,
        llmModel: LLModel = model,
        prompt: Prompt = prompt("test-ollama", LLMParams(temperature = 0.0)) {},
        responseProcessor: ResponseProcessor? = null
    ): AIAgent<String, String> {
        val promptsAndResponses = mutableListOf<String>()

        return AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = AIAgentConfig(
                prompt,
                llmModel,
                20,
                responseProcessor = responseProcessor,
            ),
            toolRegistry = toolRegistry
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext ->
                    toolCalls.add(eventContext.toolName)
                }

                onLLMCallStarting { eventContext ->
                    val promptText = eventContext.prompt.messages.joinToString { "${it.role.name}: ${it.content}" }
                    promptsAndResponses.add("PROMPT_WITH_TOOLS: $promptText")
                }

                onLLMCallCompleted { eventContext ->
                    val responseText = "[${eventContext.responses.joinToString { "${it.role.name}: ${it.content}" }}]"
                    promptsAndResponses.add("RESPONSE: $responseText")
                }
            }
        }
    }

    @Retry
    @Test
    fun ollama_testAgentClearContext() = runTest(timeout = 600.seconds) {
        createAgent(executor, createTestStrategy(), createToolRegistry())
            .run("What is the capital of France?")
            .shouldNotBeBlank()
            .shouldContain("Paris")
    }

    @ParameterizedTest
    @MethodSource("modelsWithHallucinations")
    fun ollama_testFixToolCallLLMBased(llmModel: LLModel) = runTest(timeout = 600.seconds) {
        withRetry(5) {
            val fileTools = FileOperationsTools()
            fileTools.createNewFileWithText(
                pathInProject = "scores.txt",
                text = """
                name,age,score
                Alice,25,85
                Bob,30,92
                Charlie,22,78
                """.trimIndent()
            )
            val toolRegistry = ToolRegistry {
                tool(fileTools.readFileContentTool)
                tool(fileTools.createNewFileWithTextTool)
            }

            val responseProcessor = LLMBasedToolCallFixProcessor(toolRegistry)

            val prompt = prompt("test-file-operations", LLMParams(temperature = 0.5)) {
                system {
                    markdown {
                        +"You are a helpful assistant that can work with files using tools."
                        +"Perform all actions using tools."
                        +"When you completed the task, answer with a single word: \"Done!\"."
                        +"Do not include any summary in the final message."
                    }
                }
            }

            val agent = createAgent(executor, singleRunStrategy(), toolRegistry, llmModel, prompt, responseProcessor)

            val request = """
            I have created a file named "scores.txt" in the project directory.
            The file contains the data about the students.
            
            Your task:
            Read the data to understand the format of the file.
            Create a "compute_scores.py" file to compute the average score.
            Do not summarize results in the end.

            Note:
            Make sure that all paths are relative to the project directory, e.g. "scores.csv", "compute_scores.py".
            """.trimIndent()

            withRetry {
                agent.run(request)

                assertContains(
                    toolCalls,
                    fileTools.readFileContentTool.name,
                    "${fileTools.readFileContentTool.name} tool should be called"
                )
                assertContains(
                    toolCalls,
                    fileTools.createNewFileWithTextTool.name,
                    "${fileTools.createNewFileWithTextTool.name} tool should be called"
                )
                assertEquals(2, fileTools.fileContentsByPath.size, "A script with average score should be created")
            }
        }
    }

    @Retry
    @Test
    fun ollama_testSubgraphWithTask() = runTest(timeout = 600.seconds) {
        val fileTools = FileOperationsTools()
        val toolRegistry = ToolRegistry {
            tool(fileTools.createNewFileWithTextTool)
        }

        val strategy = strategy<String, String>("ollama-subgraph-with-task") {
            val task by subgraphWithTask<String, Summary>(
                runMode = ToolCalls.SINGLE_RUN_SEQUENTIAL
            ) { it }

            nodeStart then task
            edge(task forwardTo nodeFinish transformed { it.summary })
        }
        val prompt = prompt("ollama-subgraph-with-task", LLMParams(temperature = 0.1)) {
            system(systemPrompt)
        }
        val responseProcessor = LLMBasedToolCallFixProcessor(toolRegistry)

        val agent = createAgent(executor, strategy, toolRegistry, model, prompt, responseProcessor)

        agent.run("Create a file \"hello_world.py\"")

        toolCalls.shouldContain(fileTools.createNewFileWithTextTool.name)
    }
}
