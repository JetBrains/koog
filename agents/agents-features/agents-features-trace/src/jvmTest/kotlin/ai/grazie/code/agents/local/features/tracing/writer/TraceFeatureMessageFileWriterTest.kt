package ai.grazie.code.agents.local.features.tracing.writer

import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.simpleStrategy
import ai.grazie.code.agents.core.dsl.extension.nodeLLMRequest
import ai.grazie.code.agents.core.feature.model.*
import ai.grazie.code.agents.local.features.common.message.FeatureEvent
import ai.grazie.code.agents.local.features.common.message.FeatureMessage
import ai.grazie.code.agents.local.features.common.message.FeatureStringMessage
import ai.grazie.code.agents.local.features.common.message.use
import ai.grazie.code.agents.local.features.tracing.feature.Tracing
import ai.grazie.code.files.jvm.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TraceFeatureMessageFileWriterTest {

    @Test
    fun `test file stream feature provider collect events on agent run`(@TempDir tempDir: Path) = runBlocking {

        TraceFeatureMessageFileWriter(JVMFileSystemProvider.ReadWrite, tempDir).use { writer ->

            val strategyName = "tracing-test-strategy"

            val strategy = simpleStrategy(strategyName) {
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

            val expectedMessages = listOf(
                "${AIAgentCreateEvent::class.simpleName} (strategy name: $strategyName)",
                "${AIAgentStartedEvent::class.simpleName} (strategy name: $strategyName)",
                "${AIAgentStrategyStartEvent::class.simpleName} (strategy name: $strategyName)",
                "${AIAgentNodeExecutionStartEvent::class.simpleName} (stage: default, node: __start__, input: kotlin.Unit)",
                "${AIAgentNodeExecutionEndEvent::class.simpleName} (stage: default, node: __start__, input: kotlin.Unit, output: kotlin.Unit)",
                "${AIAgentNodeExecutionStartEvent::class.simpleName} (stage: default, node: test LLM call, input: Test LLM call prompt)",
                "${LLMCallWithToolsStartEvent::class.simpleName} (prompt: Test user message, tools: [dummy, __tools_list__])",
                "${LLMCallWithToolsEndEvent::class.simpleName} (responses: [Default test response], tools: [dummy, __tools_list__])",
                "${AIAgentNodeExecutionEndEvent::class.simpleName} (stage: default, node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response))",
                "${AIAgentNodeExecutionStartEvent::class.simpleName} (stage: default, node: test LLM call with tools, input: Test LLM call with tools prompt)",
                "${LLMCallWithToolsStartEvent::class.simpleName} (prompt: Test user message, tools: [dummy, __tools_list__])",
                "${LLMCallWithToolsEndEvent::class.simpleName} (responses: [Default test response], tools: [dummy, __tools_list__])",
                "${AIAgentNodeExecutionEndEvent::class.simpleName} (stage: default, node: test LLM call with tools, input: Test LLM call with tools prompt, output: Assistant(content=Default test response))",
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
            AIAgentCreateEvent("test strategy")
        )

        val expectedMessages = listOf(
            "CUSTOM STRING. Test string message",
            "CUSTOM EVENT. ${AIAgentCreateEvent::class.simpleName}",
        )

        TraceFeatureMessageFileWriter(fs = JVMFileSystemProvider.ReadWrite, path = tempDir, format = customFormat).use { writer ->
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
            "CUSTOM. ${AIAgentCreateEvent::class.simpleName}",
            "CUSTOM. ${AIAgentStartedEvent::class.simpleName}",
            "CUSTOM. ${AIAgentStrategyStartEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "CUSTOM. ${LLMCallWithToolsStartEvent::class.simpleName}",
            "CUSTOM. ${LLMCallWithToolsEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "CUSTOM. ${LLMCallWithToolsStartEvent::class.simpleName}",
            "CUSTOM. ${LLMCallWithToolsEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentStrategyFinishedEvent::class.simpleName}",
            "CUSTOM. ${AIAgentFinishedEvent::class.simpleName}",
        )

        TraceFeatureMessageFileWriter(fs = JVMFileSystemProvider.ReadWrite, path = tempDir, format = customFormat).use { writer ->
            val strategyName = "tracing-test-strategy"

            val strategy = simpleStrategy(strategyName) {
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

        TraceFeatureMessageFileWriter(JVMFileSystemProvider.ReadWrite, tempDir).use { writer ->

            val strategyName = "tracing-test-strategy"

            val strategy = simpleStrategy(strategyName) {
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

            assertEquals(
                0, tempDir.toFile().listFiles()?.size ?: 0,
                "No files should be created"
            )
        }
    }

    @Test
    fun `test logger stream feature provider message filter`(@TempDir tempDir: Path) = runBlocking {

        TraceFeatureMessageFileWriter(JVMFileSystemProvider.ReadWrite, tempDir).use { writer ->

            val strategyName = "tracing-test-strategy"

            val strategy = simpleStrategy(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val agent = createAgent(strategy = strategy) {
                install(Tracing) {
                    messageFilter = { message ->
                        message is LLMCallWithToolsStartEvent || message is LLMCallWithToolsEndEvent
                    }
                    addMessageProcessor(writer)
                }
            }

            agent.run("")

            val expectedLogMessages = listOf(
                "${LLMCallWithToolsStartEvent::class.simpleName} (prompt: Test user message, tools: [dummy, __tools_list__])",
                "${LLMCallWithToolsEndEvent::class.simpleName} (responses: [Default test response], tools: [dummy, __tools_list__])",
                "${LLMCallWithToolsStartEvent::class.simpleName} (prompt: Test user message, tools: [dummy, __tools_list__])",
                "${LLMCallWithToolsEndEvent::class.simpleName} (responses: [Default test response], tools: [dummy, __tools_list__])",
            )

            val actualMessages = writer.targetPath.readLines()

            assertEquals(expectedLogMessages.size, actualMessages.size)
            assertContentEquals(expectedLogMessages, actualMessages)
        }
    }
}