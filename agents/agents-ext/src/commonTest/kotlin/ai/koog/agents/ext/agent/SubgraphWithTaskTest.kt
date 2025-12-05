package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.TestBlankTool
import ai.koog.agents.testing.tools.TestFinishTool
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
    @JsName("testSequentialSubgraphWithTaskToolChoiceSupportSuccess")
    fun `test sequential subgraphWithTask tool_choice support success`() = runTest {
        val blankTool = TestBlankTool()
        val finishTool = TestFinishTool

        val toolRegistry = ToolRegistry {
            tool(blankTool)
        }

        val model = OpenAIModels.Chat.GPT4o

        val inputRequest = "Test input"
        val blankToolResult = "I'm done"

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(blankTool, TestBlankTool.Args(blankToolResult)) onRequestEquals inputRequest
            mockLLMToolCall(finishTool, TestFinishTool.Args()) onRequestContains blankToolResult
        }

        // Expected / Actual
        val blankToolArgsSerialized = blankTool.encodeArgsToString(TestBlankTool.Args(blankToolResult))
        val finishToolArgsSerialized = finishTool.encodeArgsToString(TestFinishTool.Args())

        val expectedExecutionResult = listOf(
            requestString(Message.Role.User, inputRequest),
            responseString(Message.Role.Tool, blankToolArgsSerialized),
            toolCallString(blankTool.name, blankToolArgsSerialized),
            requestString(Message.Role.Tool, "\"$blankToolResult\""),
            responseString(Message.Role.Tool, finishToolArgsSerialized),
        )

        val actualExecutionResult = mutableListOf<String>()

        // Run Test
        createAgent(
            model = model,
            runMode = ToolCalls.SINGLE_RUN_SEQUENTIAL,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            finishTool = finishTool,
            installFeatures = {
                installEventHandlerCaptureEvents(actualExecutionResult)
            }
        ).use { agent ->
            val agentResult = agent.run(inputRequest)
            logger.info { "Agent is finished with result: $agentResult" }
        }

        assertEquals(expectedExecutionResult.size, actualExecutionResult.size)
        assertContentEquals(expectedExecutionResult, actualExecutionResult)
    }

    @Test
    @JsName("testSequentialSubgraphWithTaskToolChoiceSupportReceiveAssistantMessage")
    fun `test sequential subgraphWithTask tool_choice support receive assistant message`() = runTest {
        val model = OpenAIModels.Chat.GPT4o
        val toolRegistry = ToolRegistry { }

        val inputRequest = "Test input"
        val testAssistantResponse = "Test assistant response"

        val mockExecutor = getMockExecutor {
            mockLLMAnswer(testAssistantResponse) onRequestEquals inputRequest
        }

        createAgent(
            model = model,
            runMode = ToolCalls.SINGLE_RUN_SEQUENTIAL,
            executor = mockExecutor,
            toolRegistry = toolRegistry,
        ).use { agent ->
            val throwable = assertFails { agent.run(inputRequest) }

            val expectedMessage =
                "Subgraph with task must always call tools, but no ${Message.Tool.Call::class.simpleName} was generated, " +
                    "got instead: ${Message.Assistant::class.simpleName}"

            assertEquals(expectedMessage, throwable.message)
        }
    }

    @Test
    @JsName("testParallelSubgraphWithTaskToolChoiceSupportSuccess")
    fun `test parallel subgraphWithTask tool_choice support success`() = runTest {
        val blankTool1 = TestBlankTool("blank-tool-1")
        val blankTool2 = TestBlankTool("blank-tool-2")
        val finishTool = TestFinishTool

        val toolRegistry = ToolRegistry {
            tool(blankTool1)
            tool(blankTool2)
        }

        val model = OpenAIModels.Chat.GPT4o

        val inputRequest = "Test input"
        val blankTool1Result = "Blank tool 1 result"
        val blankTool2Result = "Blank tool 2 result"

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(
                toolCalls = listOf(
                    blankTool1 to TestBlankTool.Args(blankTool1Result),
                    blankTool2 to TestBlankTool.Args(blankTool2Result),
                )
            ) onRequestEquals inputRequest

            mockLLMToolCall(finishTool, TestFinishTool.Args()) onRequestContains "Blank tool"
        }

        // Expected / Actual
        val blankTool1ArgsSerialized = blankTool1.encodeArgsToString(TestBlankTool.Args(blankTool1Result))
        val blankTool2ArgsSerialized = blankTool2.encodeArgsToString(TestBlankTool.Args(blankTool2Result))
        val finishToolArgsSerialized = finishTool.encodeArgsToString(TestFinishTool.Args())

        val actualExecutionResult = mutableListOf<String>()

        val expectedExecutionResult = listOf(
            requestString(Message.Role.User, inputRequest),
            responseString(Message.Role.Tool, blankTool1ArgsSerialized),
            responseString(Message.Role.Tool, blankTool2ArgsSerialized),
            toolCallString(blankTool1.name, blankTool1ArgsSerialized),
            toolCallString(blankTool2.name, blankTool2ArgsSerialized),
            requestString(Message.Role.Tool, "\"$blankTool1Result\""),
            // TODO: Currently, mock request execution support processing only a single llm request content.
            //  Uncomment this when a test framework support condition for multi-llm-request
            // requestString(Message.Role.Tool, blankTool2Result),
            responseString(Message.Role.Tool, finishToolArgsSerialized),
        )

        // Run Test
        createAgent(
            model = model,
            runMode = ToolCalls.PARALLEL,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            finishTool = finishTool,
            installFeatures = {
                installEventHandlerCaptureEvents(actualExecutionResult)
            }
        ).use { agent ->
            val agentResult = agent.run(inputRequest)
            logger.info { "Agent is finished with result: $agentResult" }
        }

        assertEquals(expectedExecutionResult.size, actualExecutionResult.size)
        assertContentEquals(expectedExecutionResult, actualExecutionResult)
    }

    @Test
    @JsName("testParallelSubgraphWithTaskToolChoiceSupportReceiveAssistantMessage")
    fun `test parallel subgraphWithTask tool_choice support receive assistant message`() = runTest {
        val model = OpenAIModels.Chat.GPT4o

        val inputRequest = "Test input"
        val testAssistantResponse = "Test assistant response"

        val mockExecutor = getMockExecutor {
            mockLLMMixedResponse(
                toolCalls = emptyList<Pair<Tool<Any?, Any?>, Any?>>(),
                responses = listOf(testAssistantResponse)
            ) onRequestEquals inputRequest
        }

        createAgent(
            model = model,
            runMode = ToolCalls.PARALLEL,
            executor = mockExecutor,
        ).use { agent ->
            val throwable = assertFails { agent.run(inputRequest) }

            val expectedMessage =
                "Subgraph with task must always call tools, but no ${Message.Tool.Call::class.simpleName} was generated, " +
                    "got instead: ${Message.Assistant::class.simpleName}"

            assertEquals(expectedMessage, throwable.message)
        }
    }

    @Test
    @JsName("testParallelSubgraphWithTaskToolChoiceSupportReceiveToolCallsAndAssistantMessageSuccess")
    fun `test parallel subgraphWithTask tool_choice support receive tool calls and assistant message success`() = runTest {
        val blankTool = TestBlankTool("blank-tool")
        val finishTool = TestFinishTool

        val toolRegistry = ToolRegistry {
            tool(blankTool)
        }

        val model = OpenAIModels.Chat.GPT4o

        val inputRequest = "Test input"
        val blankToolResult = "Blank tool result"
        val assistantResponse = "Assistant response"

        val mockExecutor = getMockExecutor {
            mockLLMMixedResponse(
                toolCalls = listOf(blankTool to TestBlankTool.Args(blankToolResult)),
                responses = listOf(assistantResponse)
            ) onRequestEquals inputRequest

            mockLLMToolCall(finishTool, TestFinishTool.Args()) onRequestContains blankToolResult
        }

        // Expected / Actual
        val blankToolArgsSerialized = blankTool.encodeArgsToString(TestBlankTool.Args(blankToolResult))
        val finishToolArgsSerialized = finishTool.encodeArgsToString(TestFinishTool.Args())

        val expectedExecutionResult = listOf(
            requestString(Message.Role.User, inputRequest),
            responseString(Message.Role.Assistant, assistantResponse),
            responseString(Message.Role.Tool, blankToolArgsSerialized),
            toolCallString(blankTool.name, blankToolArgsSerialized),
            requestString(Message.Role.Tool, "\"$blankToolResult\""),
            responseString(Message.Role.Tool, finishToolArgsSerialized),
        )

        val actualExecutionResult = mutableListOf<String>()

        // Run Test
        createAgent(
            model = model,
            runMode = ToolCalls.PARALLEL,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            finishTool = finishTool,
            installFeatures = {
                installEventHandlerCaptureEvents(actualExecutionResult)
            }
        ).use { agent ->
            val agentResult = agent.run(inputRequest)
            logger.info { "Agent is finished with result: $agentResult" }
        }

        assertEquals(expectedExecutionResult.size, actualExecutionResult.size)
        assertContentEquals(expectedExecutionResult, actualExecutionResult)
    }

    //endregion Model With tool_choice Support

    //region Model Without tool_choice Support

    @Test
    @JsName("testSequentialSubgraphWithTaskNoToolChoiceSupportSuccess")
    fun `test sequential subgraphWithTask no tool_choice support success`() = runTest {
        val blankTool = TestBlankTool()
        val finishTool = TestFinishTool

        val toolRegistry = ToolRegistry {
            tool(blankTool)
        }

        val model = OllamaModels.Meta.LLAMA_3_2

        val inputRequest = "Test input"
        val blankToolResult = "I'm done"

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(blankTool, TestBlankTool.Args(blankToolResult)) onRequestEquals inputRequest
            mockLLMToolCall(finishTool, TestFinishTool.Args()) onRequestContains blankToolResult
        }

        // Expected / Actual
        val blankToolArgsSerialized = blankTool.encodeArgsToString(TestBlankTool.Args(blankToolResult))
        val finishToolArgsSerialized = finishTool.encodeArgsToString(TestFinishTool.Args())

        val expectedExecutionResult = listOf(
            requestString(Message.Role.User, inputRequest),
            responseString(Message.Role.Tool, blankToolArgsSerialized),
            toolCallString(blankTool.name, blankToolArgsSerialized),
            requestString(Message.Role.Tool, "\"$blankToolResult\""),
            responseString(Message.Role.Tool, finishToolArgsSerialized),
        )

        val actualExecutionResult = mutableListOf<String>()

        // Run Test
        createAgent(
            model = model,
            runMode = ToolCalls.SINGLE_RUN_SEQUENTIAL,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            finishTool = finishTool,
            installFeatures = {
                installEventHandlerCaptureEvents(actualExecutionResult)
            }
        ).use { agent ->
            val agentResult = agent.run(inputRequest)
            logger.info { "Agent is finished with result: $agentResult" }
        }

        assertEquals(expectedExecutionResult.size, actualExecutionResult.size)
        assertContentEquals(expectedExecutionResult, actualExecutionResult)
    }

    @Test
    @JsName("testSequentialSubgraphWithTaskNoToolChoiceSupportReceiveAssistantMessageSuccess")
    fun `test sequential subgraphWithTask no tool_choice support receive assistant message success`() = runTest {
        val blankTool = TestBlankTool()
        val finishTool = TestFinishTool

        val toolRegistry = ToolRegistry {
            tool(blankTool)
        }

        val model = OllamaModels.Meta.LLAMA_3_2

        val inputRequest = "Test input"
        val blankToolResult = "Blank tool result"
        val mockResponse = "Test assistant response"
        var assistantResponded = 0
        var responses = 0

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(blankTool, TestBlankTool.Args(blankToolResult)) onCondition { input ->
                responses++ == 0 && input == inputRequest
            }

            mockLLMAnswer(mockResponse) onCondition {
                responses > 0 &&
                    assistantResponded++ < SubgraphWithTaskUtils.ASSISTANT_RESPONSE_REPEAT_MAX - 1
            }

            mockLLMToolCall(finishTool, TestFinishTool.Args()) onCondition { input ->
                responses > 0 &&
                    input.contains("CALL TOOLS") &&
                    assistantResponded++ >= SubgraphWithTaskUtils.ASSISTANT_RESPONSE_REPEAT_MAX - 1
            }
        }

        // Expected / Actual
        val blankToolArgsSerialized = blankTool.encodeArgsToString(TestBlankTool.Args(blankToolResult))
        val finishToolArgsSerialized = finishTool.encodeArgsToString(TestFinishTool.Args())
        val expectedToolCallAssistantRequest =
            "# DO NOT CHAT WITH ME DIRECTLY! CALL TOOLS, INSTEAD.\n## IF YOU HAVE FINISHED, CALL `${finishTool.name}` TOOL!"

        val expectedExecutionResult = listOf(
            requestString(Message.Role.User, inputRequest),
            responseString(Message.Role.Tool, blankToolArgsSerialized),
            toolCallString(blankTool.name, blankToolArgsSerialized),
            requestString(Message.Role.Tool, "\"$blankToolResult\""),
            responseString(Message.Role.Assistant, mockResponse),
            requestString(Message.Role.User, expectedToolCallAssistantRequest),
            responseString(Message.Role.Assistant, mockResponse),
            requestString(Message.Role.User, expectedToolCallAssistantRequest),
            responseString(Message.Role.Tool, finishToolArgsSerialized),
        )

        val actualExecutionResult = mutableListOf<String>()

        // Run Test
        createAgent(
            model = model,
            runMode = ToolCalls.SINGLE_RUN_SEQUENTIAL,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            finishTool = finishTool,
            installFeatures = {
                installEventHandlerCaptureEvents(actualExecutionResult)
            }
        ).use { agent ->
            val agentResult = agent.run(inputRequest)
            logger.info { "Agent is finished with result: $agentResult" }
        }

        assertEquals(expectedExecutionResult.size, actualExecutionResult.size)
        assertContentEquals(expectedExecutionResult, actualExecutionResult)
    }

    @Test
    @JsName("testSequentialSubgraphWithTaskNoToolChoiceSupportReceiveAssistantMessageExceedMaxAttempts")
    fun `test sequential subgraphWithTask no tool_choice support receive assistant message exceed maxAttempts`() = runTest {
        val blankTool = TestBlankTool()
        val finishTool = TestFinishTool

        val toolRegistry = ToolRegistry {
            tool(blankTool)
        }

        val model = OllamaModels.Meta.LLAMA_3_2

        val inputRequest = "Test input"
        val blankToolResult = "I'm done"
        val mockResponse = "Test assistant response"
        var responses = 0

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(blankTool, TestBlankTool.Args(blankToolResult)) onCondition { input ->
                responses++ == 0 && input == inputRequest
            }

            mockLLMAnswer(mockResponse) onCondition { responses > 0 }
        }

        // Expected / Actual
        val blankToolArgsSerialized = blankTool.encodeArgsToString(TestBlankTool.Args(blankToolResult))
        val expectedToolCallAssistantRequest =
            "# DO NOT CHAT WITH ME DIRECTLY! CALL TOOLS, INSTEAD.\n## IF YOU HAVE FINISHED, CALL `${finishTool.name}` TOOL!"

        val expectedExecutionResult = listOf(
            requestString(Message.Role.User, inputRequest),
            responseString(Message.Role.Tool, blankToolArgsSerialized),
            toolCallString(blankTool.name, blankToolArgsSerialized),
            requestString(Message.Role.Tool, "\"$blankToolResult\""),
            responseString(Message.Role.Assistant, mockResponse),
            requestString(Message.Role.User, expectedToolCallAssistantRequest),
            responseString(Message.Role.Assistant, mockResponse),
            requestString(Message.Role.User, expectedToolCallAssistantRequest),
            responseString(Message.Role.Assistant, mockResponse),
            requestString(Message.Role.User, expectedToolCallAssistantRequest),
            responseString(Message.Role.Assistant, mockResponse),
        )

        val actualExecutionResult = mutableListOf<String>()

        createAgent(
            model = model,
            runMode = ToolCalls.SINGLE_RUN_SEQUENTIAL,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            finishTool = finishTool,
            installFeatures = {
                installEventHandlerCaptureEvents(actualExecutionResult)
            }
        ).use { agent ->
            val throwable = assertFails { agent.run(inputRequest) }

            val expectedErrorMessage =
                "Unable to finish subgraph with task. Reason: the model '${model.id}' does not support tool choice, " +
                    "and was not able to call `${finishTool.name}` tool after <${SubgraphWithTaskUtils.ASSISTANT_RESPONSE_REPEAT_MAX}> attempts."

            assertEquals(expectedErrorMessage, throwable.message)
        }

        assertEquals(expectedExecutionResult.size, actualExecutionResult.size)
        assertContentEquals(expectedExecutionResult, actualExecutionResult)
    }

    @Test
    @JsName("testParallelSubgraphWithTaskNoToolChoiceSupportSuccess")
    fun `test parallel subgraphWithTask no tool_choice support success`() = runTest {
        val blankTool1 = TestBlankTool("blank-tool-1")
        val blankTool2 = TestBlankTool("blank-tool-2")
        val finishTool = TestFinishTool

        val toolRegistry = ToolRegistry {
            tool(blankTool1)
            tool(blankTool2)
        }

        val model = OllamaModels.Meta.LLAMA_3_2

        val inputRequest = "Test input"
        val blankTool1Result = "Blank tool 1 result"
        val blankTool2Result = "Blank tool 2 result"

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(
                toolCalls = listOf(
                    blankTool1 to TestBlankTool.Args(blankTool1Result),
                    blankTool2 to TestBlankTool.Args(blankTool2Result),
                )
            ) onRequestEquals inputRequest

            mockLLMToolCall(finishTool, TestFinishTool.Args()) onRequestContains "Blank tool"
        }

        // Expected / Actual
        val blankTool1ArgsSerialized = blankTool1.encodeArgsToString(TestBlankTool.Args(blankTool1Result))
        val blankTool2ArgsSerialized = blankTool2.encodeArgsToString(TestBlankTool.Args(blankTool2Result))
        val finishToolArgsSerialized = finishTool.encodeArgsToString(TestFinishTool.Args())

        val expectedExecutionResult = listOf(
            requestString(Message.Role.User, inputRequest),
            responseString(Message.Role.Tool, blankTool1ArgsSerialized),
            responseString(Message.Role.Tool, blankTool2ArgsSerialized),
            toolCallString(blankTool1.name, blankTool1ArgsSerialized),
            toolCallString(blankTool2.name, blankTool2ArgsSerialized),
            requestString(Message.Role.Tool, "\"$blankTool1Result\""),
            // TODO: Currently, mock request execution support processing only a single llm request content.
            //  Uncomment this when a test framework support condition for multi-llm-request
            // requestString(Message.Role.Tool, blankTool2Result),
            responseString(Message.Role.Tool, finishToolArgsSerialized),
        )

        val actualExecutionResult = mutableListOf<String>()

        // Run Test
        createAgent(
            model = model,
            runMode = ToolCalls.PARALLEL,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            finishTool = finishTool,
            installFeatures = {
                installEventHandlerCaptureEvents(actualExecutionResult)
            }
        ).use { agent ->
            val agentResult = agent.run(inputRequest)
            logger.info { "Agent is finished with result: $agentResult" }
        }

        assertEquals(expectedExecutionResult.size, actualExecutionResult.size)
        assertContentEquals(expectedExecutionResult, actualExecutionResult)
    }

    @Test
    @JsName("testParallelSubgraphWithTaskNoToolChoiceSupportReceiveAssistantMessageSuccess")
    fun `test parallel subgraphWithTask no tool_choice support receive assistant message success`() = runTest {
        val blankTool = TestBlankTool()
        val finishTool = TestFinishTool

        val toolRegistry = ToolRegistry {
            tool(blankTool)
        }

        val model = OllamaModels.Meta.LLAMA_3_2

        val inputRequest = "Test input"
        val blankToolResult = "Blank tool result"
        val mockResponse = "Test assistant response"
        var assistantResponded = 0
        var responses = 0

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(blankTool, TestBlankTool.Args(blankToolResult)) onCondition { input ->
                responses++ == 0 && input == inputRequest
            }

            mockLLMMixedResponse(
                toolCalls = emptyList<Pair<Tool<Any?, Any?>, Any?>>(),
                responses = listOf(mockResponse)
            ) onCondition {
                responses > 0 &&
                    assistantResponded++ < SubgraphWithTaskUtils.ASSISTANT_RESPONSE_REPEAT_MAX - 1
            }

            mockLLMToolCall(finishTool, TestFinishTool.Args()) onCondition { input ->
                responses > 0 &&
                    input.contains("CALL TOOLS") &&
                    assistantResponded++ >= SubgraphWithTaskUtils.ASSISTANT_RESPONSE_REPEAT_MAX - 1
            }
        }

        // Expected / Actual
        val blankToolArgsSerialized = blankTool.encodeArgsToString(TestBlankTool.Args(blankToolResult))
        val finishToolArgsSerialized = finishTool.encodeArgsToString(TestFinishTool.Args())
        val expectedToolCallAssistantRequest =
            "# DO NOT CHAT WITH ME DIRECTLY! CALL TOOLS, INSTEAD.\n## IF YOU HAVE FINISHED, CALL `${finishTool.name}` TOOL!"

        val expectedExecutionResult = listOf(
            requestString(Message.Role.User, inputRequest),
            responseString(Message.Role.Tool, blankToolArgsSerialized),
            toolCallString(blankTool.name, blankToolArgsSerialized),
            requestString(Message.Role.Tool, "\"$blankToolResult\""),
            responseString(Message.Role.Assistant, mockResponse),
            requestString(Message.Role.User, expectedToolCallAssistantRequest),
            responseString(Message.Role.Assistant, mockResponse),
            requestString(Message.Role.User, expectedToolCallAssistantRequest),
            responseString(Message.Role.Tool, finishToolArgsSerialized),
        )

        val actualExecutionResult = mutableListOf<String>()

        // Run Test
        createAgent(
            model = model,
            runMode = ToolCalls.PARALLEL,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            finishTool = finishTool,
            installFeatures = {
                installEventHandlerCaptureEvents(actualExecutionResult)
            }
        ).use { agent ->
            val agentResult = agent.run(inputRequest)
            logger.info { "Agent is finished with result: $agentResult" }
        }

        assertEquals(expectedExecutionResult.size, actualExecutionResult.size)
        assertContentEquals(expectedExecutionResult, actualExecutionResult)
    }

    @Test
    @JsName("testParallelSubgraphWithTaskNoToolChoiceSupportReceiveAssistantMessageExceedMaxAttempts")
    fun `test parallel subgraphWithTask no tool_choice support receive assistant message exceed maxAttempts`() = runTest {
        val blankTool = TestBlankTool()
        val finishTool = TestFinishTool

        val toolRegistry = ToolRegistry {
            tool(blankTool)
        }

        val model = OllamaModels.Meta.LLAMA_3_2

        val inputRequest = "Test input"
        val blankToolResult = "I'm done"
        val mockResponse = "Test assistant response"
        var responses = 0

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(blankTool, TestBlankTool.Args(blankToolResult)) onCondition { input ->
                responses++ == 0 && input == inputRequest
            }

            mockLLMMixedResponse(
                toolCalls = emptyList<Pair<Tool<Any?, Any?>, Any?>>(),
                responses = listOf(mockResponse)
            ) onCondition { responses > 0 }
        }

        // Expected / Actual
        val blankToolArgsSerialized = blankTool.encodeArgsToString(TestBlankTool.Args(blankToolResult))
        val expectedToolCallAssistantRequest =
            "# DO NOT CHAT WITH ME DIRECTLY! CALL TOOLS, INSTEAD.\n## IF YOU HAVE FINISHED, CALL `${finishTool.name}` TOOL!"

        val expectedExecutionResult = listOf(
            requestString(Message.Role.User, inputRequest),
            responseString(Message.Role.Tool, blankToolArgsSerialized),
            toolCallString(blankTool.name, blankToolArgsSerialized),
            requestString(Message.Role.Tool, "\"$blankToolResult\""),
            responseString(Message.Role.Assistant, mockResponse),
            requestString(Message.Role.User, expectedToolCallAssistantRequest),
            responseString(Message.Role.Assistant, mockResponse),
            requestString(Message.Role.User, expectedToolCallAssistantRequest),
            responseString(Message.Role.Assistant, mockResponse),
            requestString(Message.Role.User, expectedToolCallAssistantRequest),
            responseString(Message.Role.Assistant, mockResponse),
        )

        val actualExecutionResult = mutableListOf<String>()

        createAgent(
            model = model,
            runMode = ToolCalls.PARALLEL,
            toolRegistry = toolRegistry,
            executor = mockExecutor,
            finishTool = finishTool,
            installFeatures = {
                installEventHandlerCaptureEvents(actualExecutionResult)
            }
        ).use { agent ->
            val throwable = assertFails { agent.run(inputRequest) }

            val expectedErrorMessage =
                "Unable to finish subgraph with task. Reason: the model '${model.id}' does not support tool choice, " +
                    "and was not able to call `${finishTool.name}` tool after <${SubgraphWithTaskUtils.ASSISTANT_RESPONSE_REPEAT_MAX}> attempts."

            assertEquals(expectedErrorMessage, throwable.message)
        }

        assertEquals(expectedExecutionResult.size, actualExecutionResult.size)
        assertContentEquals(expectedExecutionResult, actualExecutionResult)
    }

    //endregion Model Without tool_choice Support

    //region Private Methods

    fun createAgent(
        model: LLModel,
        runMode: ToolCalls,
        toolRegistry: ToolRegistry? = null,
        finishTool: Tool<TestFinishTool.Args, String>? = null,
        executor: PromptExecutor? = null,
        installFeatures: FeatureContext.() -> Unit = {},
    ): AIAgent<String, String> {
        val finishTool = finishTool ?: TestFinishTool
        val toolRegistry = toolRegistry ?: ToolRegistry { }
        val llmParams = LLMParams()

        val strategy = strategy("test-strategy") {
            val testSubgraphWithTask by subgraphWithTask<String, TestFinishTool.Args, String>(
                tools = toolRegistry.tools,
                finishTool = finishTool,
                llmModel = model,
                llmParams = llmParams,
                runMode = runMode,
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

    private fun FeatureContext.installEventHandlerCaptureEvents(actualEvents: MutableList<String>) {
        install(EventHandler) {
            onToolCallStarting {
                actualEvents += toolCallString(it.toolName, it.toolArgs.toString())
            }

            onLLMCallStarting {
                val request = it.prompt.messages.last()
                actualEvents += requestString(request.role, request.content)
            }

            onLLMCallCompleted { context ->
                actualEvents.addAll(context.responses.map { message -> responseString(message.role, message.content) })
            }
        }
    }

    private fun toolCallString(name: String, args: String): String =
        "$name: $args"

    private fun requestString(role: Message.Role, content: String): String =
        "request: ${role.name}: $content"

    private fun responseString(role: Message.Role, content: String): String =
        "response: ${role.name}: $content"

    //endregion Private Methods
}
