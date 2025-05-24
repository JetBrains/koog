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
import kotlinx.coroutines.runBlocking
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
    fun `test feature message log writer collect events on agent run`() = runBlocking {
        TraceFeatureMessageLogWriter(targetLogger).use { writer ->

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

            val agentInput = "Hello World!"
            agent.run(agentInput)

            val expectedLogMessages = listOf(
                "[INFO] Received feature message [event]: ${AIAgentStartedEvent::class.simpleName} (strategy name: $strategyName)",
                "[INFO] Received feature message [event]: ${AIAgentStrategyStartEvent::class.simpleName} (strategy name: $strategyName)",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionStartEvent::class.simpleName} (node: __start__, input: $agentInput)",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionEndEvent::class.simpleName} (node: __start__, input: $agentInput, output: $agentInput)",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionStartEvent::class.simpleName} (node: test LLM call, input: Test LLM call prompt)",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsStartEvent::class.simpleName} (prompt: Test user message, tools: [dummy])",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsEndEvent::class.simpleName} (responses: [Default test response], tools: [dummy])",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionEndEvent::class.simpleName} (node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response, finishReason=null))",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionStartEvent::class.simpleName} (node: test LLM call with tools, input: Test LLM call with tools prompt)",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsStartEvent::class.simpleName} (prompt: Test user message, tools: [dummy])",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsEndEvent::class.simpleName} (responses: [Default test response], tools: [dummy])",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionEndEvent::class.simpleName} (node: test LLM call with tools, input: Test LLM call with tools prompt, output: Assistant(content=Default test response, finishReason=null))",
                "[INFO] Received feature message [event]: ${AIAgentStrategyFinishedEvent::class.simpleName} (strategy name: $strategyName, result: Done)",
                "[INFO] Received feature message [event]: ${AIAgentFinishedEvent::class.simpleName} (strategy name: $strategyName, result: Done)",
            )

            assertEquals(expectedLogMessages.size, targetLogger.messages.size)
            assertContentEquals(expectedLogMessages, targetLogger.messages)
        }
    }

    @Test
    fun `test feature message log writer with custom format function for direct message processing`() = runBlocking {

        val customFormat: (FeatureMessage) -> String = { message ->
            when (message) {
                is FeatureStringMessage -> "CUSTOM STRING. ${message.message}"
                is FeatureEvent -> "CUSTOM EVENT. ${message.eventId}"
                else -> "OTHER: ${message::class.simpleName}"
            }
        }

        val actualMessages = listOf(
            FeatureStringMessage("Test string message"),
            AIAgentStartedEvent("test strategy")
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
    fun `test feature message log writer with custom format function`() = runBlocking {
        val customFormat: (FeatureMessage) -> String = { message ->
            "CUSTOM. ${message::class.simpleName}"
        }

        val expectedEvents = listOf(
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentStartedEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentStrategyStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${LLMCallWithToolsStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${LLMCallWithToolsEndEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${LLMCallWithToolsStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${LLMCallWithToolsEndEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentStrategyFinishedEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${AIAgentFinishedEvent::class.simpleName}",
        )

        TraceFeatureMessageLogWriter(targetLogger = targetLogger, format = customFormat).use { writer ->
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

            assertEquals(expectedEvents.size, targetLogger.messages.size)
            assertContentEquals(expectedEvents, targetLogger.messages)
        }
    }

    @Test
    fun `test feature message log writer is not set`() = runBlocking {
        TraceFeatureMessageLogWriter(targetLogger).use { writer ->

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
                    // Do not add stream providers
                }
            }

            val agentInput = "Hello World!"
            agent.run(agentInput)

            val expectedLogMessages = listOf<String>()

            assertEquals(expectedLogMessages.count(), targetLogger.messages.size)
        }
    }

    @Test
    fun `test feature message log writer filter`() = runBlocking {

        TraceFeatureMessageLogWriter(targetLogger).use { writer ->

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
                    messageFilter = { message ->
                        message is LLMCallWithToolsStartEvent || message is LLMCallWithToolsEndEvent
                    }
                    addMessageProcessor(writer)
                }
            }

            val agentInput = "Hello World!"
            agent.run(agentInput)

            val expectedLogMessages = listOf(
                "[INFO] Received feature message [event]: ${LLMCallWithToolsStartEvent::class.simpleName} (prompt: Test user message, tools: [dummy])",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsEndEvent::class.simpleName} (responses: [Default test response], tools: [dummy])",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsStartEvent::class.simpleName} (prompt: Test user message, tools: [dummy])",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsEndEvent::class.simpleName} (responses: [Default test response], tools: [dummy])",
            )

            assertEquals(expectedLogMessages.size, targetLogger.messages.size)
            assertContentEquals(expectedLogMessages, targetLogger.messages)
        }
    }
}
