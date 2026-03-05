package ai.koog.agents.core.agent

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.IncompleteStreamException
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.collectText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for agent behavior when connection exceptions are thrown during streaming LLM calls.
 *
 * These tests verify that [IncompleteStreamException], [LLMClientException], and other
 * connection-related errors propagate correctly through the agent pipeline when using
 * the streaming API (requestLLMStreaming).
 */
class StreamingConnectionExceptionTest {

    /**
     * A [PromptExecutor] that overrides [executeStreaming] with custom behavior
     * for simulating streaming failures (connection drops, incomplete streams, etc.).
     */
    private class StreamOverrideExecutor(
        private val delegate: PromptExecutor,
        private val streamingBehavior: () -> Flow<StreamFrame>
    ) : PromptExecutor() {

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> = delegate.execute(prompt, model, tools)

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Flow<StreamFrame> = streamingBehavior()

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
            delegate.moderate(prompt, model)

        override fun close() = delegate.close()
    }

    private val model = OpenAIModels.Chat.GPT4oMini

    private fun baseMockExecutor(): PromptExecutor = getMockExecutor {
        mockLLMAnswer("fallback").asDefaultResponse
    }

    // -- Functional agent tests --

    @Test
    fun testIncompleteStreamExceptionPropagatesThroughAgent() = runTest {
        val errors = mutableListOf<Throwable>()

        val executor = StreamOverrideExecutor(baseMockExecutor()) {
            flow {
                emit(StreamFrame.TextDelta("partial"))
                throw IncompleteStreamException()
            }
        }

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            strategy = functionalStrategy<String, String> { input ->
                requestLLMStreaming(input).collectText()
            },
            systemPrompt = "You are helpful"
        ) {
            install(EventHandler) {
                onAgentExecutionFailed { ctx -> errors.add(ctx.throwable) }
            }
        }

        val exception = assertFailsWith<Exception> {
            agent.run("test input")
        }

        assertIs<IncompleteStreamException>(exception)
        assertTrue(errors.isNotEmpty(), "Agent should have reported execution failure")
    }

    @Test
    fun testLLMClientExceptionDuringStreamingPropagatesThroughAgent() = runTest {
        val errors = mutableListOf<Throwable>()

        val executor = StreamOverrideExecutor(baseMockExecutor()) {
            flow {
                throw LLMClientException("test-client", "Connection refused")
            }
        }

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            strategy = functionalStrategy<String, String> { input ->
                requestLLMStreaming(input).collectText()
            },
            systemPrompt = "You are helpful"
        ) {
            install(EventHandler) {
                onAgentExecutionFailed { ctx -> errors.add(ctx.throwable) }
            }
        }

        val exception = assertFailsWith<LLMClientException> {
            agent.run("test input")
        }

        assertTrue(exception.message!!.contains("test-client"), "Exception should identify the client")
        assertTrue(errors.isNotEmpty(), "Agent should have reported execution failure")
    }

    @Test
    fun testConnectionExceptionAfterPartialStreamData() = runTest {
        val errors = mutableListOf<Throwable>()

        val executor = StreamOverrideExecutor(baseMockExecutor()) {
            flow {
                emit(StreamFrame.TextDelta("Hello "))
                emit(StreamFrame.TextDelta("World"))
                throw LLMClientException("test-client", "Connection reset by peer")
            }
        }

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            strategy = functionalStrategy<String, String> { input ->
                requestLLMStreaming(input).toList()
                "should not reach here"
            },
            systemPrompt = "You are helpful"
        ) {
            install(EventHandler) {
                onAgentExecutionFailed { ctx -> errors.add(ctx.throwable) }
            }
        }

        val exception = assertFailsWith<LLMClientException> {
            agent.run("test input")
        }

        assertTrue(exception.message!!.contains("Connection reset"), "Should carry original error message")
        assertTrue(errors.isNotEmpty(), "Agent should have reported execution failure")
    }

    @Test
    fun testRuntimeExceptionDuringStreamingPropagatesThroughAgent() = runTest {
        val errors = mutableListOf<Throwable>()

        val executor = StreamOverrideExecutor(baseMockExecutor()) {
            flow {
                throw RuntimeException("Unexpected network error")
            }
        }

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            strategy = functionalStrategy<String, String> { input ->
                requestLLMStreaming(input).collectText()
            },
            systemPrompt = "You are helpful"
        ) {
            install(EventHandler) {
                onAgentExecutionFailed { ctx -> errors.add(ctx.throwable) }
            }
        }

        val exception = assertFailsWith<RuntimeException> {
            agent.run("test input")
        }

        assertEquals("Unexpected network error", exception.message)
        assertTrue(errors.isNotEmpty(), "Agent should have reported execution failure")
    }

    @Test
    fun testStreamingExceptionWithPartialToolCallData() = runTest {
        val errors = mutableListOf<Throwable>()
        val toolCallsStarted = mutableListOf<String>()

        val toolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val executor = StreamOverrideExecutor(baseMockExecutor()) {
            flow {
                // Emit a partial tool call delta, then drop connection
                emit(StreamFrame.ToolCallDelta(id = "call_1", name = "create", content = "{\"na", index = 0))
                throw IncompleteStreamException("Stream dropped during tool call")
            }
        }

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            toolRegistry = toolRegistry,
            strategy = functionalStrategy<String, String> { input ->
                requestLLMStreaming(input).toList()
                "should not reach here"
            },
            systemPrompt = "You are helpful"
        ) {
            install(EventHandler) {
                onAgentExecutionFailed { ctx -> errors.add(ctx.throwable) }
                onToolCallStarting { ctx -> toolCallsStarted.add(ctx.toolName) }
            }
        }

        assertFailsWith<IncompleteStreamException> {
            agent.run("test input")
        }

        assertTrue(errors.isNotEmpty(), "Agent should have reported execution failure")
        assertTrue(toolCallsStarted.isEmpty(), "No tool should have been started since stream was incomplete")
    }

    // -- Streaming pipeline event tests --

    @Test
    fun testStreamingFailedEventFiredOnException() = runTest {
        val streamingFailedErrors = mutableListOf<Throwable>()

        val executor = StreamOverrideExecutor(baseMockExecutor()) {
            flow {
                throw IncompleteStreamException()
            }
        }

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            strategy = functionalStrategy<String, String> { input ->
                requestLLMStreaming(input).collectText()
            },
            systemPrompt = "You are helpful"
        ) {
            install(EventHandler) {
                onLLMStreamingFailed { ctx ->
                    streamingFailedErrors.add(ctx.error)
                }
            }
        }

        assertFailsWith<IncompleteStreamException> {
            agent.run("test input")
        }

        assertTrue(
            streamingFailedErrors.isNotEmpty(),
            "onLLMStreamingFailed event should have been fired"
        )
        assertIs<IncompleteStreamException>(streamingFailedErrors.first())
    }

    @Test
    fun testStreamingFailedEventFiredForLLMClientException() = runTest {
        val streamingFailedErrors = mutableListOf<Throwable>()

        val executor = StreamOverrideExecutor(baseMockExecutor()) {
            flow {
                emit(StreamFrame.TextDelta("partial data"))
                throw LLMClientException("anthropic", "502 Bad Gateway")
            }
        }

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            strategy = functionalStrategy<String, String> { input ->
                requestLLMStreaming(input).collectText()
            },
            systemPrompt = "You are helpful"
        ) {
            install(EventHandler) {
                onLLMStreamingFailed { ctx ->
                    streamingFailedErrors.add(ctx.error)
                }
            }
        }

        assertFailsWith<LLMClientException> {
            agent.run("test input")
        }

        assertTrue(
            streamingFailedErrors.isNotEmpty(),
            "onLLMStreamingFailed event should have been fired"
        )
        assertIs<LLMClientException>(streamingFailedErrors.first())
        assertTrue(streamingFailedErrors.first().message!!.contains("anthropic"))
    }

    // -- Graph-based agent streaming exception test --

    @Test
    fun testGraphAgentStreamingExceptionPropagates() = runTest {
        val streamingFailedErrors = mutableListOf<Throwable>()

        val executor = StreamOverrideExecutor(baseMockExecutor()) {
            flow {
                throw IncompleteStreamException("Connection dropped")
            }
        }

        val agentStrategy = strategy<String, String>("streaming-error-test") {
            val streamNode by nodeLLMRequestStreaming { stream ->
                stream
            }

            val collectNode by node<Flow<StreamFrame>, String> { stream ->
                stream.collectText()
            }

            edge(nodeStart forwardTo streamNode)
            edge(streamNode forwardTo collectNode)
            edge(collectNode forwardTo nodeFinish)
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = agentStrategy,
            llmModel = model,
            systemPrompt = "You are helpful"
        ) {
            install(EventHandler) {
                onLLMStreamingFailed { ctx ->
                    streamingFailedErrors.add(ctx.error)
                }
            }
        }

        assertFailsWith<IncompleteStreamException> {
            agent.run("test input")
        }

        assertTrue(
            streamingFailedErrors.isNotEmpty(),
            "onLLMStreamingFailed event should have been fired for graph agent"
        )
        assertIs<IncompleteStreamException>(streamingFailedErrors.first())
    }

    // -- Baseline: successful streaming --

    @Test
    fun testSuccessfulStreamingWorksEndToEnd() = runTest {
        val executor = StreamOverrideExecutor(baseMockExecutor()) {
            flow {
                emit(StreamFrame.TextDelta("Hello "))
                emit(StreamFrame.TextDelta("World"))
                emit(StreamFrame.TextComplete("Hello World", index = 0))
                emit(StreamFrame.End(finishReason = "stop"))
            }
        }

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            strategy = functionalStrategy<String, String> { input ->
                requestLLMStreaming(input).collectText()
            },
            systemPrompt = "You are helpful"
        )

        val result = agent.run("test input")
        assertEquals("Hello World", result)
    }
}
