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
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TraceFeatureMessageFileWriterTest {

    companion object {
        private fun createTempLogFile(tempDir: Path) = Files.createTempFile(tempDir, "agent-trace", ".log")

        private fun sinkOpener(path: Path): Sink {
            return SystemFileSystem.sink(path = kotlinx.io.files.Path(path.pathString)).buffered()
        }
    }

    @Test
    fun `test file stream feature provider collect events on agent run`(@TempDir tempDir: Path) = runTest {
        TraceFeatureMessageFileWriter(
            targetPath = createTempLogFile(tempDir),
            sinkOpener = TraceFeatureMessageFileWriterTest::sinkOpener
        ).use { writer ->

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

            val expectedMessages = listOf(
                "${AIAgentStartedEvent::class.simpleName} (agent id: $agentId, run id: $runId, strategy: $strategyName)",
                "${AIAgentStrategyStartEvent::class.simpleName} (run id: $runId, strategy: $strategyName)",
                "${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: $runId, node: __start__, input: $userPrompt)",
                "${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: $runId, node: __start__, input: $userPrompt, output: $userPrompt)",
                "${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: $runId, node: test-llm-call, input: $userPrompt)",
                "${BeforeLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [${dummyTool.name}])",
                "${AfterLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [{role: Tool, message: {\"dummy\":\"test\"}}])",
                "${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: $runId, node: test-llm-call, input: $userPrompt, output: ${
                    toolCallMessage(
                        dummyTool.name,
                        content = """{"dummy":"test"}"""
                    )
                })",
                "${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: $runId, node: test-tool-call, input: ${
                    toolCallMessage(
                        dummyTool.name,
                        content = """{"dummy":"test"}"""
                    )
                })",
                "${ToolCallEvent::class.simpleName} (run id: $runId, tool: ${dummyTool.name}, tool args: {\"dummy\":\"test\"})",
                "${ToolCallResultEvent::class.simpleName} (run id: $runId, tool: ${dummyTool.name}, tool args: {\"dummy\":\"test\"}, result: ${dummyTool.result})",
                "${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: $runId, node: test-tool-call, input: ${
                    toolCallMessage(
                        dummyTool.name,
                        content = """{"dummy":"test"}"""
                    )
                }, output: ${toolResult("0", dummyTool.name, dummyTool.result, dummyTool.result)})",
                "${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: $runId, node: test-node-llm-send-tool-result, input: ${
                    toolResult(
                        "0",
                        dummyTool.name,
                        dummyTool.result,
                        dummyTool.result
                    )
                })",
                "${BeforeLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
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
                "${AfterLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
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
                "${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: $runId, node: test-node-llm-send-tool-result, input: ${
                    toolResult(
                        "0",
                        dummyTool.name,
                        dummyTool.result,
                        dummyTool.result
                    )
                }, output: $expectedResponse)",
                "${AIAgentStrategyFinishedEvent::class.simpleName} (run id: $runId, strategy: $strategyName, result: $mockResponse)",
                "${AIAgentFinishedEvent::class.simpleName} (agent id: $agentId, run id: $runId, result: $mockResponse)",
                "${AIAgentBeforeCloseEvent::class.simpleName} (agent id: $agentId)",
            )

            val actualMessages = writer.targetPath.readLines()

            assertEquals(expectedMessages.size, actualMessages.size)
            assertContentEquals(expectedMessages, actualMessages)
        }
    }

    @Test
    fun `test feature message log writer with custom format function for direct message processing`(
        @TempDir tempDir: Path
    ) = runTest {
        val customFormat: (FeatureMessage) -> String = { message ->
            when (message) {
                is FeatureStringMessage -> "CUSTOM STRING. ${message.message}"
                is FeatureEvent -> "CUSTOM EVENT. ${message.eventId}"
                else -> "CUSTOM OTHER: ${message::class.simpleName}"
            }
        }

        val agentId = "test-agent-id"
        val runId = "test-run-id"
        val strategyName = "test-strategy"

        val messagesToProcess = listOf(
            FeatureStringMessage("Test string message"),
            AIAgentStartedEvent(agentId = agentId, runId = runId, strategyName = strategyName)
        )

        val expectedMessages = listOf(
            "CUSTOM STRING. Test string message",
            "CUSTOM EVENT. ${AIAgentStartedEvent::class.simpleName}",
        )

        TraceFeatureMessageFileWriter(
            createTempLogFile(tempDir),
            TraceFeatureMessageFileWriterTest::sinkOpener,
            format = customFormat
        ).use { writer ->
            writer.initialize()

            messagesToProcess.forEach { message -> writer.processMessage(message) }

            val actualMessage = writer.targetPath.readLines()

            assertEquals(expectedMessages.size, actualMessage.size)
            assertContentEquals(expectedMessages, actualMessage)
        }
    }

    @Test
    fun `test feature message log writer with custom format function`(@TempDir tempDir: Path) = runTest {
        val customFormat: (FeatureMessage) -> String = { message ->
            "CUSTOM. ${message::class.simpleName}"
        }

        val expectedEvents = listOf(
            "CUSTOM. ${AIAgentStartedEvent::class.simpleName}",
            "CUSTOM. ${AIAgentStrategyStartEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "CUSTOM. ${BeforeLLMCallEvent::class.simpleName}",
            "CUSTOM. ${AfterLLMCallEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "CUSTOM. ${BeforeLLMCallEvent::class.simpleName}",
            "CUSTOM. ${AfterLLMCallEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentStrategyFinishedEvent::class.simpleName}",
            "CUSTOM. ${AIAgentFinishedEvent::class.simpleName}",
            "CUSTOM. ${AIAgentBeforeCloseEvent::class.simpleName}",
        )

        TraceFeatureMessageFileWriter(
            createTempLogFile(tempDir),
            TraceFeatureMessageFileWriterTest::sinkOpener,
            format = customFormat
        ).use { writer ->
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

            val actualMessages = writer.targetPath.readLines()

            assertEquals(expectedEvents.size, actualMessages.size)
            assertContentEquals(expectedEvents, actualMessages)
        }
    }

    @Test
    fun `test file stream feature provider is not set`(@TempDir tempDir: Path) = runTest {
        val logFile = createTempLogFile(tempDir)
        TraceFeatureMessageFileWriter(logFile, TraceFeatureMessageFileWriterTest::sinkOpener).use {
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
                }
            }

            agent.run("")
            agent.close()

            assertEquals(listOf(logFile), tempDir.listDirectoryEntries())
            assertEquals(emptyList(), logFile.readLines())
        }

        assertEquals(listOf(logFile), tempDir.listDirectoryEntries())
        assertEquals(emptyList(), logFile.readLines())
    }

    @Test
    fun `test logger stream feature provider message filter`(@TempDir tempDir: Path) = runTest {
        TraceFeatureMessageFileWriter(
            createTempLogFile(tempDir),
            TraceFeatureMessageFileWriterTest::sinkOpener
        ).use { writer ->

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

            val expectedMessages = listOf(
                "${BeforeLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [${dummyTool.name}])",
                "${AfterLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [{role: Tool, message: {\"dummy\":\"test\"}}])",
                "${BeforeLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
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
                "${AfterLLMCallEvent::class.simpleName} (run id: $runId, prompt: ${
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

            val actualMessages = writer.targetPath.readLines()

            assertEquals(expectedMessages.size, actualMessages.size)
            assertContentEquals(expectedMessages, actualMessages)
        }
    }
}
