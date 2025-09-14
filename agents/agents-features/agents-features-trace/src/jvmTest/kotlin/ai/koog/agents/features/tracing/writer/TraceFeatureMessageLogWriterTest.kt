package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.message.FeatureStringMessage
import ai.koog.agents.core.feature.model.AIAgentBeforeCloseEvent
import ai.koog.agents.core.feature.model.AIAgentFinishedEvent
import ai.koog.agents.core.feature.model.AIAgentNodeExecutionEndEvent
import ai.koog.agents.core.feature.model.AIAgentNodeExecutionStartEvent
import ai.koog.agents.core.feature.model.AIAgentStartedEvent
import ai.koog.agents.core.feature.model.AIAgentStrategyFinishedEvent
import ai.koog.agents.core.feature.model.AIAgentStrategyStartEvent
import ai.koog.agents.core.feature.model.AfterLLMCallEvent
import ai.koog.agents.core.feature.model.BeforeLLMCallEvent
import ai.koog.agents.core.feature.model.ToolCallEvent
import ai.koog.agents.core.feature.model.ToolCallResultEvent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.tracing.eventString
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.mock.MockLLMProvider
import ai.koog.agents.features.tracing.mock.TestLogger
import ai.koog.agents.features.tracing.mock.assistantMessage
import ai.koog.agents.features.tracing.mock.createAgent
import ai.koog.agents.features.tracing.mock.systemMessage
import ai.koog.agents.features.tracing.mock.testClock
import ai.koog.agents.features.tracing.mock.toolCallMessage
import ai.koog.agents.features.tracing.mock.toolResult
import ai.koog.agents.features.tracing.mock.userMessage
import ai.koog.agents.features.tracing.traceString
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.utils.use
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TraceFeatureMessageLogWriterTest {

    private val targetLogger = TestLogger("test-logger")

    @AfterTest
    fun resetLogger() {
        targetLogger.reset()
    }

    @Test
    fun `test feature message log writer collect events on agent run`() = runTest {
        TraceFeatureMessageLogWriter(targetLogger).use { writer ->

            // Agent Config
            val agentId = "test-agent-id"
            val strategyName = "test-strategy"

            val userPrompt = "Call the dummy tool with argument: test"
            val systemPrompt = "Test system prompt"
            val assistantPrompt = "Test assistant prompt"
            val promptId = "Test prompt id"

            val mockResponse = "Return test result"

            // Tools
            val dummyTool = DummyTool()

            val toolRegistry = ToolRegistry {
                tool(dummyTool)
            }

            // Model
            val testModel = LLModel(
                provider = MockLLMProvider(),
                id = "test-llm-id",
                capabilities = emptyList(),
                contextLength = 1_000,
            )

            // Prompt
            val expectedPrompt = Prompt(
                messages = listOf(
                    systemMessage(systemPrompt),
                    userMessage(userPrompt),
                    assistantMessage(assistantPrompt)
                ),
                id = promptId
            )

            val expectedResponse = assistantMessage(content = mockResponse)

            val strategy = strategy(strategyName) {
                val nodeSendInput by nodeLLMRequest("test-llm-call")
                val nodeExecuteTool by nodeExecuteTool("test-tool-call")
                val nodeSendToolResult by nodeLLMSendToolResult("test-node-llm-send-tool-result")

                edge(nodeStart forwardTo nodeSendInput)
                edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
                edge(nodeExecuteTool forwardTo nodeSendToolResult)
                edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
                edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
            }

            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMToolCall(tool = dummyTool, args = DummyTool.Args("test"), toolCallId = "0") onRequestEquals
                    userPrompt
                mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
            }

            var runId = ""

            createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                model = testModel,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                toolRegistry = toolRegistry,
                promptExecutor = mockExecutor
            ) {
                install(Tracing) {
                    messageFilter = { message ->
                        if (message is AIAgentStartedEvent) {
                            runId = message.runId
                        }
                        true
                    }
                    addMessageProcessor(writer)
                }
            }.use { agent ->
                agent.run(userPrompt)
            }

            val expectedLogMessages = listOf(
                "[INFO] Received feature message [event]: ${AIAgentStartedEvent::class.simpleName} (agent id: $agentId, run id: $runId)",
                "[INFO] Received feature message [event]: ${AIAgentStrategyStartEvent::class.simpleName} (run id: $runId, strategy: $strategyName)",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: $runId, node: __start__, input: $userPrompt)",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: $runId, node: __start__, input: $userPrompt, output: $userPrompt)",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: $runId, node: test-llm-call, input: $userPrompt)",
                "[INFO] Received feature message [event]: ${BeforeLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [${dummyTool.name}])",
                "[INFO] Received feature message [event]: ${AfterLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [{role: Tool, message: {\"dummy\":\"test\"}}])",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: $runId, node: test-llm-call, input: $userPrompt, output: ${
                    toolCallMessage(
                        dummyTool.name,
                        content = """{"dummy":"test"}"""
                    )
                })",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: $runId, node: test-tool-call, input: ${
                    toolCallMessage(
                        dummyTool.name,
                        content = """{"dummy":"test"}"""
                    )
                })",
                "[INFO] Received feature message [event]: ${ToolCallEvent::class.simpleName} (run id: $runId, tool: ${dummyTool.name}, tool args: {\"dummy\":\"test\"})",
                "[INFO] Received feature message [event]: ${ToolCallResultEvent::class.simpleName} (run id: $runId, tool: ${dummyTool.name}, tool args: {\"dummy\":\"test\"}, result: ${dummyTool.result})",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: $runId, node: test-tool-call, input: ${
                    toolCallMessage(
                        dummyTool.name,
                        content = """{"dummy":"test"}"""
                    )
                }, output: ${toolResult("0", dummyTool.name, dummyTool.result, dummyTool.result)})",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: $runId, node: test-node-llm-send-tool-result, input: ${
                    toolResult(
                        "0",
                        dummyTool.name,
                        dummyTool.result,
                        dummyTool.result
                    )
                })",
                "[INFO] Received feature message [event]: ${BeforeLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = userPrompt),
                            toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                            toolResult(
                                "0",
                                dummyTool.name,
                                dummyTool.result,
                                dummyTool.result
                            ).toMessage(clock = testClock)
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [${dummyTool.name}])",
                "[INFO] Received feature message [event]: ${AfterLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = userPrompt),
                            toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                            toolResult(
                                "0",
                                dummyTool.name,
                                dummyTool.result,
                                dummyTool.result
                            ).toMessage(clock = testClock)
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [{${expectedResponse.traceString}}])",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: $runId, node: test-node-llm-send-tool-result, input: ${
                    toolResult(
                        "0",
                        dummyTool.name,
                        dummyTool.result,
                        dummyTool.result
                    )
                }, output: $expectedResponse)",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: $runId, node: __finish__, input: $mockResponse)",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: $runId, node: __finish__, input: $mockResponse, output: $mockResponse)",
                "[INFO] Received feature message [event]: ${AIAgentStrategyFinishedEvent::class.simpleName} (run id: $runId, strategy: $strategyName, result: $mockResponse)",
                "[INFO] Received feature message [event]: ${AIAgentFinishedEvent::class.simpleName} (agent id: $agentId, run id: $runId, result: $mockResponse)",
                "[INFO] Received feature message [event]: ${AIAgentBeforeCloseEvent::class.simpleName} (agent id: $agentId)",
            )

            val actualMessages = targetLogger.messages

            assertEquals(expectedLogMessages.size, actualMessages.size)
            assertContentEquals(expectedLogMessages, actualMessages)
        }
    }

    @Test
    fun `test feature message log writer with custom format function for direct message processing`() = runTest {
        val customFormat: (FeatureMessage) -> String = { message ->
            when (message) {
                is FeatureStringMessage -> "CUSTOM STRING. ${message.message}"
                is FeatureEvent -> "CUSTOM EVENT. ${message.eventId}"
                else -> "OTHER: ${message::class.simpleName}"
            }
        }

        val agentId = "test-agent-id"
        val runId = "test-run-id"

        val actualMessages = listOf(
            FeatureStringMessage("Test string message"),
            AIAgentStartedEvent(agentId = agentId, runId = runId)
        )

        val expectedMessages = listOf(
            "[INFO] Received feature message [message]: CUSTOM STRING. Test string message",
            "[INFO] Received feature message [event]: CUSTOM EVENT. ${AIAgentStartedEvent::class.simpleName}",
        )

        TraceFeatureMessageLogWriter(targetLogger = targetLogger, format = customFormat).use { writer ->
            writer.initialize()

            actualMessages.forEach { message -> writer.processMessage(message) }

            assertEquals(expectedMessages.size, targetLogger.messages.size)
            assertContentEquals(expectedMessages, targetLogger.messages)
        }
    }

    @Test
    fun `test feature message log writer with custom format function`() = runTest {
        val customFormat: (FeatureMessage) -> String = { message ->
            "CUSTOM. ${message::class.simpleName}"
        }

        val expectedEvents = listOf(
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentStartedEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentStrategyStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${BeforeLLMCallEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AfterLLMCallEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${BeforeLLMCallEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AfterLLMCallEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentStrategyFinishedEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentFinishedEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentBeforeCloseEvent::class.simpleName}",
        )

        TraceFeatureMessageLogWriter(targetLogger = targetLogger, format = customFormat).use { writer ->
            val strategyName = "tracing-test-strategy"

            val strategy = strategy<String, String>(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val agent = createAgent(strategy = strategy) {
                install(Tracing) {
                    messageFilter = { true }
                    addMessageProcessor(writer)
                }
            }

            agent.run("")
            agent.close()

            assertEquals(expectedEvents.size, targetLogger.messages.size)
            assertContentEquals(expectedEvents, targetLogger.messages)
        }
    }

    @Test
    fun `test feature message log writer is not set`() = runTest {
        TraceFeatureMessageLogWriter(targetLogger).use {
            val strategyName = "tracing-test-strategy"

            val strategy = strategy<String, String>(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val agent = createAgent(strategy = strategy) {
                install(Tracing) {
                    messageFilter = { true }
                    // Do not add stream providers
                }
            }

            val agentInput = "Hello World!"
            agent.run(agentInput)
            agent.close()

            val expectedLogMessages = listOf<String>()

            assertEquals(expectedLogMessages.count(), targetLogger.messages.size)
        }
    }

    @Test
    fun `test feature message log writer filter`() = runTest {
        TraceFeatureMessageLogWriter(targetLogger).use { writer ->

            // Agent Config
            val agentId = "test-agent-id"
            val strategyName = "test-strategy"

            val userPrompt = "Call the dummy tool with argument: test"
            val systemPrompt = "Test system prompt"
            val assistantPrompt = "Test assistant prompt"
            val promptId = "Test prompt id"

            val mockResponse = "Return test result"

            // Tools
            val dummyTool = DummyTool()

            val toolRegistry = ToolRegistry {
                tool(dummyTool)
            }

            // Model
            val testModel = LLModel(
                provider = MockLLMProvider(),
                id = "test-llm-id",
                capabilities = emptyList(),
                contextLength = 1_000,
            )

            // Prompt
            val expectedPrompt = Prompt(
                messages = listOf(
                    systemMessage(systemPrompt),
                    userMessage(userPrompt),
                    assistantMessage(assistantPrompt)
                ),
                id = promptId
            )

            val expectedResponse = assistantMessage(content = mockResponse)

            val strategy = strategy(strategyName) {
                val nodeSendInput by nodeLLMRequest("test-llm-call")
                val nodeExecuteTool by nodeExecuteTool("test-tool-call")
                val nodeSendToolResult by nodeLLMSendToolResult("test-node-llm-send-tool-result")

                edge(nodeStart forwardTo nodeSendInput)
                edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
                edge(nodeExecuteTool forwardTo nodeSendToolResult)
                edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
                edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
            }

            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMToolCall(tool = dummyTool, args = DummyTool.Args("test"), toolCallId = "0") onRequestEquals
                    userPrompt
                mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
            }

            var runId = ""

            createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                model = testModel,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                toolRegistry = toolRegistry,
                promptExecutor = mockExecutor
            ) {
                install(Tracing) {
                    messageFilter = { message ->
                        if (message is AIAgentStartedEvent) {
                            runId = message.runId
                        }
                        message is BeforeLLMCallEvent || message is AfterLLMCallEvent
                    }
                    addMessageProcessor(writer)
                }
            }.use { agent ->
                agent.run(userPrompt)
            }

            val expectedLogMessages = listOf(
                "[INFO] Received feature message [event]: ${BeforeLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [${dummyTool.name}])",
                "[INFO] Received feature message [event]: ${AfterLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [{role: Tool, message: {\"dummy\":\"test\"}}])",
                "[INFO] Received feature message [event]: ${BeforeLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = userPrompt),
                            toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                            toolResult(
                                "0",
                                dummyTool.name,
                                dummyTool.result,
                                dummyTool.result
                            ).toMessage(clock = testClock)
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [${dummyTool.name}])",
                "[INFO] Received feature message [event]: ${AfterLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = userPrompt),
                            toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                            toolResult(
                                "0",
                                dummyTool.name,
                                dummyTool.result,
                                dummyTool.result
                            ).toMessage(clock = testClock)
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [{${expectedResponse.traceString}}])",
            )

            val actualMessages = targetLogger.messages

            assertEquals(expectedLogMessages.size, actualMessages.size)
            assertContentEquals(expectedLogMessages, actualMessages)
        }
    }
}
