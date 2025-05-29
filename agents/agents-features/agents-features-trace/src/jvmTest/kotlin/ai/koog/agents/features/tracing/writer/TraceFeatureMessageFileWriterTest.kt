package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.feature.model.*
import ai.koog.agents.features.common.message.FeatureEvent
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureStringMessage
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.utils.use
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking
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
    fun `test file stream feature provider collect events on agent run`(@TempDir tempDir: Path) = runBlocking {

        TraceFeatureMessageFileWriter(createTempLogFile(tempDir), TraceFeatureMessageFileWriterTest::sinkOpener).use { writer ->

            val strategyName = "tracing-test-strategy"

            val userPrompt = "Test user prompt"
            val systemPrompt = "Test system prompt"
            val assistantPrompt = "Test assistant prompt"
            val promptId = "Test prompt id"

            val strategy = strategy(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val agent = createAgent(
                promptId = promptId,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                strategy = strategy
            ) {
                install(Tracing) {
                    messageFilter = { true }
                    addMessageProcessor(writer)
                }
            }

            val agentInput = "Hello World!"
            agent.run(agentInput)

            val expectedPrompt = Prompt(
                messages = listOf(
                    Message.System(systemPrompt),
                    Message.User(userPrompt),
                    Message.Assistant(assistantPrompt),
                ),
                id = promptId,
            )

            val expectedResponse = Message.Assistant(content = "Default test response")

            val expectedMessages = listOf(
                "${AIAgentStartedEvent::class.simpleName} (strategy name: $strategyName)",
                "${AIAgentStrategyStartEvent::class.simpleName} (strategy name: $strategyName)",
                "${AIAgentNodeExecutionStartEvent::class.simpleName} (node: __start__, input: $agentInput)",
                "${AIAgentNodeExecutionEndEvent::class.simpleName} (node: __start__, input: $agentInput, output: $agentInput)",
                "${AIAgentNodeExecutionStartEvent::class.simpleName} (node: test LLM call, input: Test LLM call prompt)",
                "${LLMCallStartEvent::class.simpleName} (prompt: ${expectedPrompt.copy(messages = expectedPrompt.messages + Message.User(content="Test LLM call prompt"))}, tools: [dummy])",
                "${LLMCallEndEvent::class.simpleName} (responses: [$expectedResponse])",
                "${AIAgentNodeExecutionEndEvent::class.simpleName} (node: test LLM call, input: Test LLM call prompt, output: $expectedResponse)",
                "${AIAgentNodeExecutionStartEvent::class.simpleName} (node: test LLM call with tools, input: Test LLM call with tools prompt)",
                "${LLMCallStartEvent::class.simpleName} (prompt: ${expectedPrompt.copy(messages = expectedPrompt.messages + listOf(Message.User(content="Test LLM call prompt"), Message.Assistant(content="Default test response"), Message.User(content="Test LLM call with tools prompt")))}, tools: [dummy])",
                "${LLMCallEndEvent::class.simpleName} (responses: [$expectedResponse])",
                "${AIAgentNodeExecutionEndEvent::class.simpleName} (node: test LLM call with tools, input: Test LLM call with tools prompt, output: $expectedResponse)",
                "${AIAgentStrategyFinishedEvent::class.simpleName} (strategy name: $strategyName, result: Done)",
                "${AIAgentFinishedEvent::class.simpleName} (strategy name: $strategyName, result: Done)",
            )

            val actualMessages = writer.targetPath.readLines()

            assertEquals(expectedMessages.size, actualMessages.size)
            assertContentEquals(expectedMessages, actualMessages)
        }
    }

    @Test
    fun `test feature message log writer with custom format function for direct message processing`(@TempDir tempDir: Path) = runBlocking {

        val customFormat: (FeatureMessage) -> String = { message ->
            when (message) {
                is FeatureStringMessage -> "CUSTOM STRING. ${message.message}"
                is FeatureEvent -> "CUSTOM EVENT. ${message.eventId}"
                else -> "CUSTOM OTHER: ${message::class.simpleName}"
            }
        }

        val messagesToProcess = listOf(
            FeatureStringMessage("Test string message"),
            AIAgentStartedEvent("test strategy")
        )

        val expectedMessages = listOf(
            "CUSTOM STRING. Test string message",
            "CUSTOM EVENT. ${AIAgentStartedEvent::class.simpleName}",
        )

        TraceFeatureMessageFileWriter(createTempLogFile(tempDir), TraceFeatureMessageFileWriterTest::sinkOpener, format = customFormat).use { writer ->
            writer.initialize()

            messagesToProcess.forEach { message -> writer.processMessage(message) }

            val actualMessage = writer.targetPath.readLines()

            assertEquals(expectedMessages.size, actualMessage.size)
            assertContentEquals(expectedMessages, actualMessage)
        }
    }

    @Test
    fun `test feature message log writer with custom format function`(@TempDir tempDir: Path) = runBlocking {
        val customFormat: (FeatureMessage) -> String = { message ->
            "CUSTOM. ${message::class.simpleName}"
        }

        val expectedEvents = listOf(
            "CUSTOM. ${AIAgentStartedEvent::class.simpleName}",
            "CUSTOM. ${AIAgentStrategyStartEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "CUSTOM. ${LLMCallStartEvent::class.simpleName}",
            "CUSTOM. ${LLMCallEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "CUSTOM. ${LLMCallStartEvent::class.simpleName}",
            "CUSTOM. ${LLMCallEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentStrategyFinishedEvent::class.simpleName}",
            "CUSTOM. ${AIAgentFinishedEvent::class.simpleName}",
        )

        TraceFeatureMessageFileWriter(createTempLogFile(tempDir), TraceFeatureMessageFileWriterTest::sinkOpener, format = customFormat).use { writer ->
            val strategyName = "tracing-test-strategy"

            val strategy = strategy(strategyName) {
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

            val actualMessages = writer.targetPath.readLines()

            assertEquals(expectedEvents.size, actualMessages.size)
            assertContentEquals(expectedEvents, actualMessages)
        }
    }

    @Test
    fun `test file stream feature provider is not set`(@TempDir tempDir: Path) = runBlocking {

        val logFile = createTempLogFile(tempDir)
        TraceFeatureMessageFileWriter(logFile, TraceFeatureMessageFileWriterTest::sinkOpener).use { writer ->

            val strategyName = "tracing-test-strategy"

            val strategy = strategy(strategyName) {
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

            assertEquals(listOf(logFile), tempDir.listDirectoryEntries())
            assertEquals(emptyList(), logFile.readLines())
        }

        assertEquals(listOf(logFile), tempDir.listDirectoryEntries())
        assertEquals(emptyList(), logFile.readLines())
    }

    @Test
    fun `test logger stream feature provider message filter`(@TempDir tempDir: Path) = runBlocking {

        TraceFeatureMessageFileWriter(createTempLogFile(tempDir), TraceFeatureMessageFileWriterTest::sinkOpener).use { writer ->

            val strategyName = "tracing-test-strategy"

            val userPrompt = "Test user prompt"
            val systemPrompt = "Test system prompt"
            val assistantPrompt = "Test assistant prompt"
            val promptId = "Test prompt id"

            val strategy = strategy(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val agent = createAgent(
                promptId = promptId,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                strategy = strategy
            ) {
                install(Tracing) {
                    messageFilter = { message ->
                        message is LLMCallStartEvent || message is LLMCallEndEvent
                    }
                    addMessageProcessor(writer)
                }
            }

            agent.run("")

            val expectedPrompt = Prompt(
                messages = listOf(
                    Message.System(systemPrompt),
                    Message.User(userPrompt),
                    Message.Assistant(assistantPrompt),
                ),
                id = promptId,
            )

            val expectedResponse = Message.Assistant(content = "Default test response")

            val expectedLogMessages = listOf(
                "${LLMCallStartEvent::class.simpleName} (prompt: ${expectedPrompt.copy(messages = expectedPrompt.messages + Message.User(content="Test LLM call prompt"))}, tools: [dummy])",
                "${LLMCallEndEvent::class.simpleName} (responses: [$expectedResponse])",
                "${LLMCallStartEvent::class.simpleName} (prompt: ${expectedPrompt.copy(messages = expectedPrompt.messages + listOf(
                    Message.User(content="Test LLM call prompt"), 
                    Message.Assistant(content="Default test response"), 
                    Message.User(content="Test LLM call with tools prompt")
                ))}, tools: [dummy])",
                "${LLMCallEndEvent::class.simpleName} (responses: [$expectedResponse])",
            )

            val actualMessages = writer.targetPath.readLines()

            assertEquals(expectedLogMessages.size, actualMessages.size)
            assertContentEquals(expectedLogMessages, actualMessages)
        }
    }
}