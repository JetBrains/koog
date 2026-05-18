package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.ToolCalls
import ai.koog.agents.core.dsl.extension.asUserMessage
import ai.koog.agents.core.dsl.extension.nodeExecuteToolsAndGetResults
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.FeatureStringMessage
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.GraphStrategyStartingEvent
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.StrategyCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.mock.MockLLMProvider
import ai.koog.agents.features.tracing.mock.TestLogger
import ai.koog.agents.features.tracing.mock.assistantMessage
import ai.koog.agents.features.tracing.mock.createAgent
import ai.koog.agents.features.tracing.mock.receivedToolResult
import ai.koog.agents.features.tracing.mock.receivedToolResultMessage
import ai.koog.agents.features.tracing.mock.systemMessage
import ai.koog.agents.features.tracing.mock.testClock
import ai.koog.agents.features.tracing.mock.toolCallMessage
import ai.koog.agents.features.tracing.mock.toolCallMessagePart
import ai.koog.agents.features.tracing.mock.userMessage
import ai.koog.agents.features.tracing.traceString
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.toModelInfo
import ai.koog.prompt.message.Message
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import ai.koog.utils.io.use
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TraceFeatureMessageLogWriterTest {
    private val serializer = KotlinxSerializer()
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
                val nodeExecuteTool by nodeExecuteToolsAndGetResults("test-tool-call")
                val nodeSendToolResult by nodeLLMSendToolResults("test-node-llm-send-tool-result")

                edge(nodeStart forwardTo nodeSendInput asUserMessage { it })
                edge(nodeSendInput forwardTo nodeExecuteTool onToolCalls { true })
                edge(nodeSendInput forwardTo nodeFinish onTextMessage { true })
                edge(nodeExecuteTool forwardTo nodeSendToolResult)
                edge(nodeSendToolResult forwardTo nodeFinish onTextMessage { true })
                edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { true })
            }

            val mockExecutor = getMockExecutor(serializer, clock = testClock) {
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
                    writer.setMessageFilter { message ->
                        if (message is AgentStartingEvent) {
                            runId = message.runId
                        }
                        true
                    }
                    addMessageProcessor(writer)
                }
            }.use { agent ->
                agent.run(userPrompt, null)
            }

            val dummyToolArgsEncoded = dummyTool.encodeArgs(DummyTool.Args("test"), serializer)
            val dummyToolResultEncoded = dummyTool.encodeResult(dummyTool.result, serializer)
            val dummyToolName = dummyTool.name
            val dummyToolDescription = dummyTool.descriptor.description

            val dummyToolCallArgs = dummyToolArgsEncoded.toString()
            val toolCallPart = toolCallMessagePart(toolName = dummyToolName, content = dummyToolCallArgs)
            val toolCallAssistant = toolCallMessage(toolName = dummyToolName, content = dummyToolCallArgs)
            val userPromptMessage = userMessage(content = userPrompt)
            val toolResultMessage = receivedToolResultMessage(
                toolCallId = "0",
                toolName = dummyToolName,
                toolArgs = dummyTool.encodeArgs(DummyTool.Args("test"), serializer),
                toolDescription = dummyToolDescription,
                content = dummyTool.result,
                result = dummyToolResultEncoded,
            )
            val toolResultsValue = ReceivedToolResults(
                toolResults = listOf(
                    receivedToolResult(
                        toolCallId = "0",
                        toolName = dummyToolName,
                        toolArgs = dummyTool.encodeArgs(DummyTool.Args("test"), serializer),
                        toolDescription = dummyToolDescription,
                        content = dummyTool.result,
                        result = dummyToolResultEncoded,
                    )
                )
            )

            val userMessageEncoded = serializer.encodeToJSONElement(userPromptMessage, typeToken<Message.User>())
            val toolCallMessageEncoded = serializer.encodeToJSONElement(toolCallAssistant, typeToken<Message.Assistant>())
            val toolCallsEncoded = serializer.encodeToJSONElement(ToolCalls(listOf(toolCallPart)), typeToken<ToolCalls>())
            val toolResultsEncoded = serializer.encodeToJSONElement(toolResultsValue, typeToken<ReceivedToolResults>())
            val responseEncoded = serializer.encodeToJSONElement(expectedResponse, typeToken<Message.Assistant>())

            val expectedPromptFirstCall = expectedPrompt.copy(
                messages = expectedPrompt.messages + userPromptMessage
            ).traceString
            val expectedPromptSecondCall = expectedPrompt.copy(
                messages = expectedPrompt.messages + listOf(
                    userPromptMessage,
                    toolCallAssistant,
                    toolResultMessage,
                )
            ).traceString

            val expectedLogMessages = listOf(
                "[INFO] Received feature message [event]: ${AgentStartingEvent::class.simpleName} (agent id: $agentId, run id: $runId)",
                "[INFO] Received feature message [event]: ${GraphStrategyStartingEvent::class.simpleName} (run id: $runId, strategy: $strategyName)",
                "[INFO] Received feature message [event]: ${NodeExecutionStartingEvent::class.simpleName} (run id: $runId, node: __start__, input: \"$userPrompt\")",
                "[INFO] Received feature message [event]: ${NodeExecutionCompletedEvent::class.simpleName} (run id: $runId, node: __start__, input: \"$userPrompt\", output: \"$userPrompt\")",
                "[INFO] Received feature message [event]: ${NodeExecutionStartingEvent::class.simpleName} (run id: $runId, node: test-llm-call, input: $userMessageEncoded)",
                "[INFO] Received feature message [event]: ${LLMCallStartingEvent::class.simpleName} (run id: $runId, prompt: $expectedPromptFirstCall, model: ${testModel.toModelInfo().modelIdentifierName}, tools: [$dummyToolName])",
                "[INFO] Received feature message [event]: ${LLMCallCompletedEvent::class.simpleName} (run id: $runId, prompt: $expectedPromptFirstCall, model: ${testModel.toModelInfo().modelIdentifierName}, response: ${toolCallAssistant.traceString}])",
                "[INFO] Received feature message [event]: ${NodeExecutionCompletedEvent::class.simpleName} (run id: $runId, node: test-llm-call, input: $userMessageEncoded, output: $toolCallMessageEncoded)",
                "[INFO] Received feature message [event]: ${NodeExecutionStartingEvent::class.simpleName} (run id: $runId, node: test-tool-call, input: $toolCallsEncoded)",
                "[INFO] Received feature message [event]: ${ToolCallStartingEvent::class.simpleName} (run id: $runId, tool: $dummyToolName, tool args: $dummyToolArgsEncoded)",
                "[INFO] Received feature message [event]: ${ToolCallCompletedEvent::class.simpleName} (run id: $runId, tool: $dummyToolName, tool args: $dummyToolArgsEncoded, description: $dummyToolDescription, result: $dummyToolResultEncoded)",
                "[INFO] Received feature message [event]: ${NodeExecutionCompletedEvent::class.simpleName} (run id: $runId, node: test-tool-call, input: $toolCallsEncoded, output: $toolResultsEncoded)",
                "[INFO] Received feature message [event]: ${NodeExecutionStartingEvent::class.simpleName} (run id: $runId, node: test-node-llm-send-tool-result, input: $toolResultsEncoded)",
                "[INFO] Received feature message [event]: ${LLMCallStartingEvent::class.simpleName} (run id: $runId, prompt: $expectedPromptSecondCall, model: ${testModel.toModelInfo().modelIdentifierName}, tools: [$dummyToolName])",
                "[INFO] Received feature message [event]: ${LLMCallCompletedEvent::class.simpleName} (run id: $runId, prompt: $expectedPromptSecondCall, model: ${testModel.toModelInfo().modelIdentifierName}, response: ${expectedResponse.traceString}])",
                "[INFO] Received feature message [event]: ${NodeExecutionCompletedEvent::class.simpleName} (run id: $runId, node: test-node-llm-send-tool-result, input: $toolResultsEncoded, output: $responseEncoded)",
                "[INFO] Received feature message [event]: ${NodeExecutionStartingEvent::class.simpleName} (run id: $runId, node: __finish__, input: \"$mockResponse\")",
                "[INFO] Received feature message [event]: ${NodeExecutionCompletedEvent::class.simpleName} (run id: $runId, node: __finish__, input: \"$mockResponse\", output: \"$mockResponse\")",
                "[INFO] Received feature message [event]: ${StrategyCompletedEvent::class.simpleName} (run id: $runId, strategy: $strategyName, result: $mockResponse)",
                "[INFO] Received feature message [event]: ${AgentCompletedEvent::class.simpleName} (agent id: $agentId, run id: $runId, result: $mockResponse)",
                "[INFO] Received feature message [event]: ${AgentClosingEvent::class.simpleName} (agent id: $agentId)",
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
                is FeatureEvent -> "CUSTOM EVENT. No event message"
                else -> "OTHER: ${message::class.simpleName}"
            }
        }

        val id = "test-event-id"
        val parentId = "test-parent-id"
        val agentId = "test-agent-id"
        val runId = "test-run-id"

        val actualMessages = listOf(
            FeatureStringMessage("Test string message"),
            AgentStartingEvent(
                agentId = agentId,
                runId = runId
            )
        )

        val expectedMessages = listOf(
            "[INFO] Received feature message [message]: CUSTOM STRING. Test string message",
            "[INFO] Received feature message [event]: CUSTOM EVENT. No event message",
        )

        TraceFeatureMessageLogWriter(targetLogger = targetLogger, format = customFormat).use { writer ->
            writer.initialize()

            actualMessages.forEach { message -> writer.onMessage(message) }

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
            "[INFO] Received feature message [event]: CUSTOM. ${AgentStartingEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${GraphStrategyStartingEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionStartingEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionCompletedEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionStartingEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${LLMCallStartingEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${LLMCallCompletedEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionCompletedEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionStartingEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${LLMCallStartingEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${LLMCallCompletedEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionCompletedEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionStartingEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionCompletedEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${StrategyCompletedEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AgentCompletedEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AgentClosingEvent::class.simpleName}",
        )

        TraceFeatureMessageLogWriter(targetLogger = targetLogger, format = customFormat).use { writer ->
            val strategyName = "tracing-test-strategy"

            val strategy = strategy<String, String>(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode asUserMessage { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode asUserMessage { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val agent = createAgent(strategy = strategy) {
                install(Tracing) {
                    addMessageProcessor(writer)
                }
            }

            agent.run("", null)
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

                edge(nodeStart forwardTo llmCallNode asUserMessage { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode asUserMessage { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val agent = createAgent(strategy = strategy) {
                install(Tracing) {
                    // Do not add stream providers
                }
            }

            val agentInput = "Hello World!"
            agent.run(agentInput, null)
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
                val nodeExecuteTool by nodeExecuteToolsAndGetResults("test-tool-call")
                val nodeSendToolResult by nodeLLMSendToolResults("test-node-llm-send-tool-result")

                edge(nodeStart forwardTo nodeSendInput asUserMessage { it })
                edge(nodeSendInput forwardTo nodeExecuteTool onToolCalls { true })
                edge(nodeSendInput forwardTo nodeFinish onTextMessage { true })
                edge(nodeExecuteTool forwardTo nodeSendToolResult)
                edge(nodeSendToolResult forwardTo nodeFinish onTextMessage { true })
                edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { true })
            }

            val mockExecutor = getMockExecutor(serializer, clock = testClock) {
                mockLLMToolCall(
                    tool = dummyTool,
                    args = DummyTool.Args("test"),
                    toolCallId = "0"
                ) onRequestEquals userPrompt

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
                    writer.setMessageFilter { message ->
                        if (message is AgentStartingEvent) {
                            runId = message.runId
                        }
                        message is LLMCallStartingEvent || message is LLMCallCompletedEvent
                    }
                    addMessageProcessor(writer)
                }
            }.use { agent ->
                agent.run(userPrompt, null)
            }

            val dummyToolArgsEncoded = dummyTool.encodeArgs(DummyTool.Args("test"), serializer)
            val dummyToolResultEncoded = dummyTool.encodeResult(dummyTool.result, serializer)
            val dummyToolName = dummyTool.name
            val dummyToolDescription = dummyTool.descriptor.description

            val expectedLogMessages = listOf(
                "[INFO] Received feature message [event]: ${LLMCallStartingEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.toModelInfo().modelIdentifierName}, tools: [$dummyToolName])",
                "[INFO] Received feature message [event]: ${LLMCallCompletedEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.toModelInfo().modelIdentifierName}, response: ${toolCallMessage(toolName = dummyToolName, content = dummyToolArgsEncoded.toString()).traceString}])",
                "[INFO] Received feature message [event]: ${LLMCallStartingEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = userPrompt),
                            toolCallMessage(dummyToolName, content = dummyToolArgsEncoded.toString()),
                            receivedToolResultMessage(
                                toolCallId = "0",
                                toolName = dummyToolName,
                                toolArgs = dummyTool.encodeArgs(DummyTool.Args("test"), serializer),
                                toolDescription = dummyToolDescription,
                                content = dummyTool.result,
                                result = dummyToolResultEncoded,
                            )
                        )
                    ).traceString
                }, model: ${testModel.toModelInfo().modelIdentifierName}, tools: [$dummyToolName])",
                "[INFO] Received feature message [event]: ${LLMCallCompletedEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = userPrompt),
                            toolCallMessage(dummyToolName, content = dummyToolArgsEncoded.toString()),
                            receivedToolResultMessage(
                                toolCallId = "0",
                                toolName = dummyToolName,
                                toolArgs = dummyTool.encodeArgs(DummyTool.Args("test"), serializer),
                                toolDescription = dummyToolDescription,
                                content = dummyTool.result,
                                result = dummyToolResultEncoded,
                            )
                        )
                    ).traceString
                }, model: ${testModel.toModelInfo().modelIdentifierName}, response: ${expectedResponse.traceString}])",
            )

            val actualMessages = targetLogger.messages

            assertEquals(expectedLogMessages.size, actualMessages.size)
            assertContentEquals(expectedLogMessages, actualMessages)
        }
    }
}
