package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.feature.model.*
import ai.koog.agents.features.common.message.FeatureEvent
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureStringMessage
import ai.koog.agents.features.tracing.*
import ai.koog.agents.features.tracing.feature.Tracing
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

            val strategyName = "tracing-test-strategy"

            val userPrompt = "Test user prompt"
            val systemPrompt = "Test system prompt"
            val assistantPrompt = "Test assistant prompt"
            val promptId = "Test prompt id"

            val strategy = strategy<String, String>(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val testModel = LLModel(
                provider = TestLLMProvider(),
                id = "test-llm-id",
                capabilities = emptyList()
            )

            var runId = ""

            val agent = createAgent(
                promptId = promptId,
                model = testModel,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                strategy = strategy
            ) {
                install(Tracing) {
                    messageFilter = { message ->
                        if (message is AIAgentStartedEvent) { runId = message.runId }
                        true
                    }
                    addMessageProcessor(writer)
                }
            }

            val agentInput = "Hello World!"
            agent.run(agentInput)
            agent.close()

            val expectedPrompt = Prompt(
                messages = listOf(
                    systemMessage(systemPrompt),
                    userMessage(userPrompt),
                    assistantMessage(assistantPrompt),
                ),
                id = promptId,
            )

            val expectedResponse = assistantMessage(content = "Default test response")

            val expectedLogMessages = listOf(
                "[INFO] Received feature message [event]: ${AIAgentStartedEvent::class.simpleName} (agent id: ${agent.id}, run id: ${runId}, strategy: $strategyName)",
                "[INFO] Received feature message [event]: ${AIAgentStrategyStartEvent::class.simpleName} (run id: ${runId}, strategy: $strategyName)",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: ${runId}, node: __start__, input: $agentInput)",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: ${runId}, node: __start__, input: $agentInput, output: $agentInput)",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: ${runId}, node: test LLM call, input: Test LLM call prompt)",
                "[INFO] Received feature message [event]: ${BeforeLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = "Test LLM call prompt"
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [dummy])",
                "[INFO] Received feature message [event]: ${AfterLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = "Test LLM call prompt"
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [${expectedResponse.traceString}])",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: ${runId}, node: test LLM call, input: Test LLM call prompt, output: $expectedResponse)",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: ${runId}, node: test LLM call with tools, input: Test LLM call with tools prompt)",
                "[INFO] Received feature message [event]: ${BeforeLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = "Test LLM call prompt"),
                            assistantMessage(content = "Default test response"),
                            userMessage(content = "Test LLM call with tools prompt")
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [dummy])",
                "[INFO] Received feature message [event]: ${AfterLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = "Test LLM call prompt"),
                            assistantMessage(content = "Default test response"),
                            userMessage(content = "Test LLM call with tools prompt")
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [${expectedResponse.traceString}])",
                "[INFO] Received feature message [event]: ${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: ${runId}, node: test LLM call with tools, input: Test LLM call with tools prompt, output: $expectedResponse)",
                "[INFO] Received feature message [event]: ${AIAgentStrategyFinishedEvent::class.simpleName} (run id: ${runId}, strategy: $strategyName, result: Done)",
                "[INFO] Received feature message [event]: ${AIAgentFinishedEvent::class.simpleName} (agent id: ${agent.id}, run id: ${runId}, result: Done)",
                "[INFO] Received feature message [event]: ${AIAgentBeforeCloseEvent::class.simpleName} (agent id: ${agent.id})",
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
        val strategyName = "test-strategy"

        val actualMessages = listOf(
            FeatureStringMessage("Test string message"),
            AIAgentStartedEvent(agentId = agentId, runId = runId, strategyName = strategyName)
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
        TraceFeatureMessageLogWriter(targetLogger).use { writer ->

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

            val strategyName = "tracing-test-strategy"

            val userPrompt = "Test user prompt"
            val systemPrompt = "Test system prompt"
            val assistantPrompt = "Test assistant prompt"
            val promptId = "Test prompt id"

            val strategy = strategy<String, String>(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val testModel = LLModel(
                provider = TestLLMProvider(),
                id = "test-llm-id",
                capabilities = emptyList()
            )

            var runId = ""

            val agent = createAgent(
                strategy = strategy,
                promptId = promptId,
                model = testModel,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt
            ) {
                install(Tracing) {
                    messageFilter = { message ->
                        if (message is AIAgentStartedEvent) { runId = message.runId }
                        message is BeforeLLMCallEvent || message is AfterLLMCallEvent
                    }
                    addMessageProcessor(writer)
                }
            }

            val agentInput = "Hello World!"
            agent.run(agentInput)
            agent.close()

            val expectedPrompt = Prompt(
                messages = listOf(
                    systemMessage(systemPrompt),
                    userMessage(userPrompt),
                    assistantMessage(assistantPrompt),
                ),
                id = promptId,
            )

            val expectedResponse = assistantMessage(content = "Default test response")

            val expectedLogMessages = listOf(
                "[INFO] Received feature message [event]: ${BeforeLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = "Test LLM call prompt"
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [dummy])",
                "[INFO] Received feature message [event]: ${AfterLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = "Test LLM call prompt"
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [${expectedResponse.traceString}])",
                "[INFO] Received feature message [event]: ${BeforeLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = "Test LLM call prompt"),
                            assistantMessage(content = "Default test response"),
                            userMessage(content = "Test LLM call with tools prompt")
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [dummy])",
                "[INFO] Received feature message [event]: ${AfterLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = "Test LLM call prompt"),
                            assistantMessage(content = "Default test response"),
                            userMessage(content = "Test LLM call with tools prompt")
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [${expectedResponse.traceString}])",
            )

            val actualMessages = targetLogger.messages

            assertEquals(expectedLogMessages.size, actualMessages.size)
            assertContentEquals(expectedLogMessages, actualMessages)
        }
    }
}