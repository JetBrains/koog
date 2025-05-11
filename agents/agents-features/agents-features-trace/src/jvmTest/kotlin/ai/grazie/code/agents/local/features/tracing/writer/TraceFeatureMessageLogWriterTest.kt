package ai.grazie.code.agents.local.features.tracing.writer

import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.simpleStrategy
import ai.grazie.code.agents.core.dsl.extension.nodeLLMRequest
import ai.grazie.code.agents.core.feature.model.*
import ai.grazie.code.agents.core.feature.model.AgentCreateEvent
import ai.grazie.code.agents.local.features.common.message.FeatureEvent
import ai.grazie.code.agents.local.features.common.message.FeatureMessage
import ai.grazie.code.agents.local.features.common.message.FeatureStringMessage
import ai.grazie.code.agents.local.features.common.message.use
import ai.grazie.code.agents.local.features.tracing.feature.TraceFeature
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

            val strategy = simpleStrategy(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val agent = createAgent(
                strategy = strategy,
                scope = this,
            ) {
                install(TraceFeature) {
                    messageFilter = { true }
                    addMessageProcessor(writer)
                }
            }

            agent.run("")

            val expectedLogMessages = listOf(
                "[INFO] Received feature message [event]: ${AgentCreateEvent::class.simpleName} (strategy name: $strategyName)",
                "[INFO] Received feature message [event]: ${StrategyStartEvent::class.simpleName} (strategy name: $strategyName)",
                "[INFO] Received feature message [event]: ${NodeExecutionStartEvent::class.simpleName} (stage: default, node: __start__, input: kotlin.Unit)",
                "[INFO] Received feature message [event]: ${NodeExecutionEndEvent::class.simpleName} (stage: default, node: __start__, input: kotlin.Unit, output: kotlin.Unit)",
                "[INFO] Received feature message [event]: ${NodeExecutionStartEvent::class.simpleName} (stage: default, node: test LLM call, input: Test LLM call prompt)",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsStartEvent::class.simpleName} (prompt: Test user message, tools: [dummy, __tools_list__])",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsEndEvent::class.simpleName} (responses: [Default test response], tools: [dummy, __tools_list__])",
                "[INFO] Received feature message [event]: ${NodeExecutionEndEvent::class.simpleName} (stage: default, node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response))",
                "[INFO] Received feature message [event]: ${NodeExecutionStartEvent::class.simpleName} (stage: default, node: test LLM call with tools, input: Test LLM call with tools prompt)",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsStartEvent::class.simpleName} (prompt: Test user message, tools: [dummy, __tools_list__])",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsEndEvent::class.simpleName} (responses: [Default test response], tools: [dummy, __tools_list__])",
                "[INFO] Received feature message [event]: ${NodeExecutionEndEvent::class.simpleName} (stage: default, node: test LLM call with tools, input: Test LLM call with tools prompt, output: Assistant(content=Default test response))"
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
            AgentCreateEvent("test strategy")
        )

        val expectedMessages = listOf(
            "[INFO] Received feature message [message]: CUSTOM STRING. Test string message",
            "[INFO] Received feature message [event]: CUSTOM EVENT. ${AgentCreateEvent::class.simpleName}",
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
            "[INFO] Received feature message [event]: CUSTOM. ${AgentCreateEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${StrategyStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionEndEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${LLMCallWithToolsStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${LLMCallWithToolsEndEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionEndEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${LLMCallWithToolsStartEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${LLMCallWithToolsEndEvent::class.simpleName}",
            "[INFO] Received feature message [event]: CUSTOM. ${NodeExecutionEndEvent::class.simpleName}",
        )

        TraceFeatureMessageLogWriter(targetLogger = targetLogger, format = customFormat).use { writer ->
            val strategyName = "tracing-test-strategy"

            val strategy = simpleStrategy(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val agent = createAgent(
                strategy = strategy,
                scope = this,
            ) {
                install(TraceFeature) {
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

            val strategy = simpleStrategy(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val agent = createAgent(
                strategy = strategy,
                scope = this,
            ) {
                install(TraceFeature) {
                    messageFilter = { true }
                    // Do not add stream providers
                }
            }

            agent.run("")

            val expectedLogMessages = listOf<String>()

            assertEquals(expectedLogMessages.count(), targetLogger.messages.size)
        }
    }

    @Test
    fun `test feature message log writer filter`() = runBlocking {

        TraceFeatureMessageLogWriter(targetLogger).use { writer ->

            val strategyName = "tracing-test-strategy"

            val strategy = simpleStrategy(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val agent = createAgent(
                strategy = strategy,
                scope = this,
            ) {
                install(TraceFeature) {
                    messageFilter = { message ->
                        message is LLMCallWithToolsStartEvent || message is LLMCallWithToolsEndEvent
                    }
                    addMessageProcessor(writer)
                }
            }

            agent.run("")

            val expectedLogMessages = listOf(
                "[INFO] Received feature message [event]: ${LLMCallWithToolsStartEvent::class.simpleName} (prompt: Test user message, tools: [dummy, __tools_list__])",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsEndEvent::class.simpleName} (responses: [Default test response], tools: [dummy, __tools_list__])",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsStartEvent::class.simpleName} (prompt: Test user message, tools: [dummy, __tools_list__])",
                "[INFO] Received feature message [event]: ${LLMCallWithToolsEndEvent::class.simpleName} (responses: [Default test response], tools: [dummy, __tools_list__])",
            )

            assertEquals(expectedLogMessages.size, targetLogger.messages.size)
            assertContentEquals(expectedLogMessages, targetLogger.messages)
        }
    }
}
