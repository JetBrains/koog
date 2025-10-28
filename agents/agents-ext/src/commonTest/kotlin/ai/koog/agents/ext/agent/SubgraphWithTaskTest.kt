package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.mock.MockTools
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.utils.io.use
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class SubgraphWithTaskTest {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    //region Model With tool_choice Support

    @Test
    @JsName("testSubgraphWithTaskToolChoiceSupportSuccess")
    fun `test subgraphWithTask tool_choice support success`() = runTest {
        val blankTool = MockTools.BlankTool()
        val finishTool = MockTools.FinishTool

        val toolRegistry = ToolRegistry {
            tool(blankTool)
        }

        val model = OpenAIModels.Chat.GPT4o

        val inputRequest = "Test input"
        val blankToolResult = "I'm done"

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(blankTool, MockTools.BlankTool.Args(blankToolResult)) onRequestEquals inputRequest
            mockLLMToolCall(finishTool, MockTools.FinishTool.Args()) onRequestContains blankToolResult
        }

        val actualToolCalls = mutableListOf<String>()

        val expectedToolCalls = listOf(
            blankTool.name,
        )

        createAgent(
            model = model,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            finishTool = finishTool,
            installFeatures = {
                install(EventHandler) {
                    onToolCallStarting {
                        actualToolCalls += it.tool.name
                    }
                }
            }
        ).use { agent ->
            val agentResult = agent.run(inputRequest)
            logger.info { "Agent is finished with result: $agentResult" }

            assertEquals(expectedToolCalls.size, actualToolCalls.size)
            assertContentEquals(expectedToolCalls, actualToolCalls)
        }
    }

    @Test
    @JsName("testSubgraphWithTaskToolChoiceSupportReceiveAssistantMessage")
    fun `test subgraphWithTask tool_choice support receive assistant message`() = runTest {
        val model = OpenAIModels.Chat.GPT4o

        val inputRequest = "Test input"
        val testAssistantResponse = "Test assistant response"

        val mockExecutor = getMockExecutor {
            mockLLMAnswer(testAssistantResponse) onRequestEquals inputRequest
        }

        createAgent(
            model = model,
            executor = mockExecutor,
        ).use { agent ->
            val throwable = assertFails { agent.run(inputRequest) }

            val expectedMessage =
                "Subgraph with task must always call tools, but no ${Message.Tool.Call::class.simpleName} was generated, " +
                    "got instead: ${Message.Assistant::class.simpleName}"

            assertEquals(expectedMessage, throwable.message)
        }
    }

    //endregion Model With tool_choice Support

    //region Model Without tool_choice Support

    @Test
    @JsName("testSubgraphWithTaskNoToolChoiceSupportSuccess")
    fun `test subgraphWithTask no tool_choice support success`() = runTest {
        val blankTool = MockTools.BlankTool()
        val finishTool = MockTools.FinishTool

        val toolRegistry = ToolRegistry {
            tool(blankTool)
        }

        val model = OllamaModels.Meta.LLAMA_3_2

        val inputRequest = "Test input"
        val blankToolResult = "I'm done"

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(blankTool, MockTools.BlankTool.Args(blankToolResult)) onRequestEquals inputRequest
            mockLLMToolCall(finishTool, MockTools.FinishTool.Args()) onRequestContains blankToolResult
        }

        val actualLLMCalls = mutableListOf<String>()
        val actualToolCalls = mutableListOf<String>()

        val expectedLLMCalls = listOf(
            inputRequest,
            Json.encodeToString(blankToolResult)
        )

        val expectedToolCalls = listOf(
            blankTool.name,
        )

        createAgent(
            model = model,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            finishTool = finishTool,
            installFeatures = {
                install(EventHandler) {
                    onToolCallStarting {
                        actualToolCalls += it.tool.name
                    }

                    onLLMCallStarting {
                        actualLLMCalls += it.prompt.messages.last().content
                    }
                }
            }
        ).use { agent ->
            val agentResult = agent.run(inputRequest)
            logger.info { "Agent is finished with result: $agentResult" }

            assertEquals(expectedToolCalls.size, actualToolCalls.size)
            assertContentEquals(expectedToolCalls, actualToolCalls)

            assertEquals(expectedLLMCalls.size, actualLLMCalls.size)
            assertContentEquals(expectedLLMCalls, actualLLMCalls)
        }
    }

    @Test
    @JsName("testSubgraphWithTaskNoToolChoiceSupportReceiveAssistantMessageSuccess")
    fun `test subgraphWithTask no tool_choice support receive assistant message success`() = runTest {
        val blankTool = MockTools.BlankTool()
        val finishTool = MockTools.FinishTool

        val toolRegistry = ToolRegistry {
            tool(blankTool)
        }

        val model = OllamaModels.Meta.LLAMA_3_2

        val inputRequest = "Test input"
        val blankToolResult = "I'm done"
        val mockResponse = "Test assistant response"
        var assistantResponded = 0

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(blankTool, MockTools.BlankTool.Args(blankToolResult)) onRequestEquals inputRequest

            mockLLMAnswer(mockResponse) onCondition { input ->
                input.contains(inputRequest) && assistantResponded++ < SubgraphWithTaskUtils.ASSISTANT_RESPONSE_REPEAT_MAX
            }

            mockLLMToolCall(finishTool, MockTools.FinishTool.Args()) onCondition { input ->
                input.contains("CALL TOOLS") && assistantResponded++ >= SubgraphWithTaskUtils.ASSISTANT_RESPONSE_REPEAT_MAX
            }
        }

        val actualLLMCalls = mutableListOf<String>()
        val actualToolCalls = mutableListOf<String>()

        val expectedToolCallAssistantRequest =
            "# DO NOT CHAT WITH ME DIRECTLY! CALL TOOLS, INSTEAD.\n## IF YOU HAVE FINISHED, CALL `${finishTool.name}` TOOL!"

        val expectedLLMCalls = listOf(
            inputRequest,
            Json.encodeToString(blankToolResult),
            expectedToolCallAssistantRequest,
            expectedToolCallAssistantRequest,
            expectedToolCallAssistantRequest,
        )

        val expectedToolCalls = listOf(
            blankTool.name,
        )

        createAgent(
            model = model,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            finishTool = finishTool,
            installFeatures = {
                install(EventHandler) {
                    onToolCallStarting {
                        actualToolCalls += it.tool.name
                    }

                    onLLMCallStarting {
                        actualLLMCalls += it.prompt.messages.last().content
                    }
                }
            }
        ).use { agent ->
            val agentResult = agent.run(inputRequest)
            logger.info { "Agent is finished with result: $agentResult" }

            assertEquals(expectedToolCalls.size, actualToolCalls.size)
            assertContentEquals(expectedToolCalls, actualToolCalls)

            assertEquals(expectedLLMCalls.size, actualLLMCalls.size)
            assertContentEquals(expectedLLMCalls, actualLLMCalls)
        }
    }

    @Test
    @JsName("testSubgraphWithTaskNoToolChoiceSupportReceiveAssistantMessageExceedMaxAttempts")
    fun `test subgraphWithTask no tool_choice support receive assistant message exceed maxAttempts`() = runTest {
        val blankTool = MockTools.BlankTool()
        val finishTool = MockTools.FinishTool

        val toolRegistry = ToolRegistry {
            tool(blankTool)
        }

        val model = OllamaModels.Meta.LLAMA_3_2

        val inputRequest = "Test input"
        val blankToolResult = "I'm done"
        val mockResponse = "Test assistant response"
        var assistantResponded = 0

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(blankTool, MockTools.BlankTool.Args(blankToolResult)) onRequestEquals inputRequest

            mockLLMAnswer(mockResponse) onCondition { input ->
                input.contains(inputRequest) && assistantResponded++ > SubgraphWithTaskUtils.ASSISTANT_RESPONSE_REPEAT_MAX
            }
        }

        val actualLLMCalls = mutableListOf<String>()
        val actualToolCalls = mutableListOf<String>()

        val expectedToolCallAssistantRequest =
            "# DO NOT CHAT WITH ME DIRECTLY! CALL TOOLS, INSTEAD.\n## IF YOU HAVE FINISHED, CALL `${finishTool.name}` TOOL!"

        val expectedLLMCalls = listOf(
            inputRequest,
            Json.encodeToString(blankToolResult),
            expectedToolCallAssistantRequest,
            expectedToolCallAssistantRequest,
            expectedToolCallAssistantRequest,
        )

        val expectedToolCalls = listOf(
            blankTool.name,
        )

        createAgent(
            model = model,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            finishTool = finishTool,
            installFeatures = {
                install(EventHandler) {
                    onToolCallStarting {
                        actualToolCalls += it.tool.name
                    }

                    onLLMCallStarting {
                        actualLLMCalls += it.prompt.messages.last().content
                    }
                }
            }
        ).use { agent ->
            val throwable = assertFails { agent.run(inputRequest) }

            val expectedErrorMessage =
                "Unable to finish subgraph with task. Reason: the model '${model.id}' does not support tool choice, " +
                    "and was not able to call `${finishTool.name}` tool after <${SubgraphWithTaskUtils.ASSISTANT_RESPONSE_REPEAT_MAX}> attempts."

            assertEquals(expectedErrorMessage, throwable.message)

            assertEquals(expectedToolCalls.size, actualToolCalls.size)
            assertContentEquals(expectedToolCalls, actualToolCalls)

            assertEquals(expectedLLMCalls.size, actualLLMCalls.size)
            assertContentEquals(expectedLLMCalls, actualLLMCalls)
        }
    }

    //endregion Model Without tool_choice Support

    //region Private Methods

    fun createAgent(
        model: LLModel,
        toolRegistry: ToolRegistry? = null,
        finishTool: Tool<MockTools.FinishTool.Args, String>? = null,
        executor: PromptExecutor? = null,
        installFeatures: FeatureContext.() -> Unit = {},
    ): AIAgent<String, String> {
        val finishTool = finishTool ?: MockTools.FinishTool
        val toolRegistry = toolRegistry ?: ToolRegistry { }
        val llmParams = LLMParams()

        val strategy = strategy("test-strategy") {
            val testSubgraphWithTask by subgraphWithTask<String, MockTools.FinishTool.Args, String>(
                tools = toolRegistry.tools,
                finishTool = finishTool,
                llmModel = model,
                llmParams = llmParams,
            ) { input -> input }

            nodeStart then testSubgraphWithTask then nodeFinish
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {
                system("You are a test agent.")
            },
            model = model,
            maxAgentIterations = 20,
        )

        return AIAgent(
            promptExecutor = executor ?: getMockExecutor { },
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            installFeatures = installFeatures,
        )
    }

    //endregion Private Methods
}
