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
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.toModelInfo
import ai.koog.utils.io.use
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
                    writer.setMessageFilter { message ->
                        if (message is AgentStartingEvent) {
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
                "${AgentStartingEvent::class.simpleName} (agent id: $agentId, run id: $runId)",
                "${GraphStrategyStartingEvent::class.simpleName} (run id: $runId, strategy: $strategyName)",
                "${NodeExecutionStartingEvent::class.simpleName} (run id: $runId, node: __start__, input: $userPrompt)",
                "${NodeExecutionCompletedEvent::class.simpleName} (run id: $runId, node: __start__, input: $userPrompt, output: $userPrompt)",
                "${NodeExecutionStartingEvent::class.simpleName} (run id: $runId, node: test-llm-call, input: $userPrompt)",
                "${LLMCallStartingEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.toModelInfo().eventString}, tools: [${dummyTool.name}])",
                "${LLMCallCompletedEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.toModelInfo().eventString}, responses: [{role: Tool, message: {\"dummy\":\"test\"}}])",
                "${NodeExecutionCompletedEvent::class.simpleName} (run id: $runId, node: test-llm-call, input: $userPrompt, output: ${
                    toolCallMessage(
                        dummyTool.name,
                        content = """{"dummy":"test"}"""
                    )
                })",
                "${NodeExecutionStartingEvent::class.simpleName} (run id: $runId, node: test-tool-call, input: ${
                    toolCallMessage(
                        dummyTool.name,
                        content = """{"dummy":"test"}"""
                    )
                })",
                "${ToolCallStartingEvent::class.simpleName} (run id: $runId, tool: ${dummyTool.name}, tool args: {\"dummy\":\"test\"})",
                "${ToolCallCompletedEvent::class.simpleName} (run id: $runId, tool: ${dummyTool.name}, tool args: {\"dummy\":\"test\"}, result: ${dummyTool.result})",
                "${NodeExecutionCompletedEvent::class.simpleName} (run id: $runId, node: test-tool-call, input: ${
                    toolCallMessage(
                        dummyTool.name,
                        content = """{"dummy":"test"}"""
                    )
                }, output: ${toolResult("0", dummyTool.name, dummyTool.result, dummyTool.result)})",
                "${NodeExecutionStartingEvent::class.simpleName} (run id: $runId, node: test-node-llm-send-tool-result, input: ${
                    toolResult(
                        "0",
                        dummyTool.name,
                        dummyTool.result,
                        dummyTool.result
                    )
                })",
                "${LLMCallStartingEvent::class.simpleName} (run id: $runId, prompt: ${
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
                }, model: ${testModel.toModelInfo().eventString}, tools: [${dummyTool.name}])",
                "${LLMCallCompletedEvent::class.simpleName} (run id: $runId, prompt: ${
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
                "${NodeExecutionCompletedEvent::class.simpleName} (run id: $runId, node: test-node-llm-send-tool-result, input: ${
                    toolResult(
                        "0",
                        dummyTool.name,
                        dummyTool.result,
                        dummyTool.result
                    )
                }, output: $expectedResponse)",
                "${NodeExecutionStartingEvent::class.simpleName} (run id: $runId, node: __finish__, input: $mockResponse)",
                "${NodeExecutionCompletedEvent::class.simpleName} (run id: $runId, node: __finish__, input: $mockResponse, output: $mockResponse)",
                "${StrategyCompletedEvent::class.simpleName} (run id: $runId, strategy: $strategyName, result: $mockResponse)",
                "${AgentCompletedEvent::class.simpleName} (agent id: $agentId, run id: $runId, result: $mockResponse)",
                "${AgentClosingEvent::class.simpleName} (agent id: $agentId)",
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
                is FeatureEvent -> "CUSTOM EVENT. No event message"
                else -> "CUSTOM OTHER: ${message::class.simpleName}"
            }
        }

        val agentId = "test-agent-id"
        val runId = "test-run-id"

        val messagesToProcess = listOf(
            FeatureStringMessage("Test string message"),
            AgentStartingEvent(agentId = agentId, runId = runId)
        )

        val expectedMessages = listOf(
            "CUSTOM STRING. Test string message",
            "CUSTOM EVENT. No event message",
        )

        TraceFeatureMessageFileWriter(
            createTempLogFile(tempDir),
            TraceFeatureMessageFileWriterTest::sinkOpener,
            format = customFormat
        ).use { writer ->
            writer.initialize()

            messagesToProcess.forEach { message -> writer.onMessage(message) }

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
            "CUSTOM. ${AgentStartingEvent::class.simpleName}",
            "CUSTOM. ${GraphStrategyStartingEvent::class.simpleName}",
            "CUSTOM. ${NodeExecutionStartingEvent::class.simpleName}",
            "CUSTOM. ${NodeExecutionCompletedEvent::class.simpleName}",
            "CUSTOM. ${NodeExecutionStartingEvent::class.simpleName}",
            "CUSTOM. ${LLMCallStartingEvent::class.simpleName}",
            "CUSTOM. ${LLMCallCompletedEvent::class.simpleName}",
            "CUSTOM. ${NodeExecutionCompletedEvent::class.simpleName}",
            "CUSTOM. ${NodeExecutionStartingEvent::class.simpleName}",
            "CUSTOM. ${LLMCallStartingEvent::class.simpleName}",
            "CUSTOM. ${LLMCallCompletedEvent::class.simpleName}",
            "CUSTOM. ${NodeExecutionCompletedEvent::class.simpleName}",
            "CUSTOM. ${NodeExecutionStartingEvent::class.simpleName}",
            "CUSTOM. ${NodeExecutionCompletedEvent::class.simpleName}",
            "CUSTOM. ${StrategyCompletedEvent::class.simpleName}",
            "CUSTOM. ${AgentCompletedEvent::class.simpleName}",
            "CUSTOM. ${AgentClosingEvent::class.simpleName}",
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
                install(Tracing)
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
                    writer.setMessageFilter { message ->
                        if (message is AgentStartingEvent) {
                            runId = message.runId
                        }
                        message is LLMCallStartingEvent || message is LLMCallCompletedEvent
                    }
                    addMessageProcessor(writer)
                }
            }.use { agent ->
                agent.run(userPrompt)
            }

            val expectedMessages = listOf(
                "${LLMCallStartingEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.toModelInfo().eventString}, tools: [${dummyTool.name}])",
                "${LLMCallCompletedEvent::class.simpleName} (run id: $runId, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = userPrompt
                        )
                    ).traceString
                }, model: ${testModel.toModelInfo().eventString}, responses: [{role: Tool, message: {\"dummy\":\"test\"}}])",
                "${LLMCallStartingEvent::class.simpleName} (run id: $runId, prompt: ${
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
                }, model: ${testModel.toModelInfo().eventString}, tools: [${dummyTool.name}])",
                "${LLMCallCompletedEvent::class.simpleName} (run id: $runId, prompt: ${
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
