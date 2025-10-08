package ai.koog.prompt.executor.clients.retry

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.emitAppend
import ai.koog.prompt.streaming.streamFrameFlow
import ai.koog.prompt.streaming.streamFrameFlowOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds

class RetryingLLMClientTest {

    private val testModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = "test-model",
        capabilities = listOf(LLMCapability.Completion),
        contextLength = 4096
    )

    private val testPrompt = prompt("test-prompt") {
        system("Test system message")
        user("Test user message")
    }

    private val testMetaInfo = ResponseMetaInfo.create(Clock.System)

    private val testResponse = listOf(
        Message.Assistant(
            content = "Test response",
            metaInfo = testMetaInfo
        )
    )

    @Test
    fun testSucceedOnFirstAttempt() = runTest {
        val mockClient = MockLLMClient(
            executeResponse = testResponse
        )

        val retryingClient = RetryingLLMClient(mockClient)

        val result = retryingClient.execute(testPrompt, testModel, emptyList())

        assertEquals(testResponse, result)
        assertEquals(1, mockClient.executeCalls)
    }

    @Test
    fun testConvertLLMClientToRetryingClientWithDefaultConfig() = runTest {
        val mockClient = MockLLMClient()
        // when
        val retryingClient = mockClient.toRetryingClient()

        // then
        assertSame(actual = retryingClient.config, expected = RetryConfig.DEFAULT)
    }

    @Test
    fun testConvertLLMClientToRetryingClientWithCustomConfig() = runTest {
        // given
        val mockClient = MockLLMClient()
        val retryConfig = RetryConfig(maxAttempts = 100500)
        // when
        val retryingClient = mockClient.toRetryingClient(retryConfig)

        // then
        assertSame(actual = retryingClient.config, expected = retryConfig)
    }

    @Test
    fun testRetryOnRateLimitError() = runTest {
        val mockClient = MockLLMClient(
            executeResponse = testResponse,
            failuresBeforeSuccess = 2,
            failureMessage = "Error from API: 429 Too Many Requests"
        )

        val retryingClient = RetryingLLMClient(
            mockClient,
            RetryConfig(
                maxAttempts = 3,
                initialDelay = 10.milliseconds // Fast for testing
            )
        )

        val result = retryingClient.execute(testPrompt, testModel, emptyList())

        assertEquals(testResponse, result)
        assertEquals(3, mockClient.executeCalls) // 2 failures + 1 success
    }

    @Test
    fun testRetryOnServiceUnavailable() = runTest {
        val mockClient = MockLLMClient(
            executeResponse = testResponse,
            failuresBeforeSuccess = 1,
            failureMessage = "Error: 503 Service Unavailable"
        )

        val retryingClient = RetryingLLMClient(
            mockClient,
            RetryConfig(
                maxAttempts = 2,
                initialDelay = 10.milliseconds
            )
        )

        val result = retryingClient.execute(testPrompt, testModel, emptyList())

        assertEquals(testResponse, result)
        assertEquals(2, mockClient.executeCalls)
    }

    @Test
    fun testRetryOnTimeout() = runTest {
        val mockClient = MockLLMClient(
            executeResponse = testResponse,
            failuresBeforeSuccess = 1,
            failureMessage = "Connection timeout"
        )

        val retryingClient = RetryingLLMClient(
            mockClient,
            RetryConfig(
                maxAttempts = 2,
                initialDelay = 10.milliseconds
            )
        )

        val result = retryingClient.execute(testPrompt, testModel, emptyList())

        assertEquals(testResponse, result)
        assertEquals(2, mockClient.executeCalls)
    }

    @Test
    fun testNoRetryOnClientError() = runTest {
        val mockClient = MockLLMClient(
            executeResponse = testResponse,
            failuresBeforeSuccess = 1,
            failureMessage = "Error: 400 Bad Request"
        )

        val retryingClient = RetryingLLMClient(
            mockClient,
            RetryConfig(maxAttempts = 3)
        )

        assertFailsWith<RuntimeException> {
            retryingClient.execute(testPrompt, testModel, emptyList())
        }

        assertEquals(1, mockClient.executeCalls) // No retry
    }

    @Test
    fun testThrowAfterMaxAttempts() = runTest {
        val mockClient = MockLLMClient(
            executeResponse = testResponse,
            failuresBeforeSuccess = 5, // Will always fail
            failureMessage = "Error: 503"
        )

        val retryingClient = RetryingLLMClient(
            mockClient,
            RetryConfig(
                maxAttempts = 3,
                initialDelay = 10.milliseconds
            )
        )

        val exception = assertFailsWith<RuntimeException> {
            retryingClient.execute(testPrompt, testModel, emptyList())
        }

        assertEquals(exception.message?.contains("503"), true)
        assertEquals(3, mockClient.executeCalls)
    }

    @Test
    fun testCustomRetryPatterns() = runTest {
        val mockClient = MockLLMClient(
            executeResponse = testResponse,
            failuresBeforeSuccess = 1,
            failureMessage = "CUSTOM_ERROR_123"
        )

        val retryingClient = RetryingLLMClient(
            mockClient,
            RetryConfig(
                maxAttempts = 2,
                initialDelay = 10.milliseconds,
                retryablePatterns = listOf(
                    RetryablePattern.Regex(Regex("CUSTOM_ERROR_\\d+"))
                )
            )
        )

        val result = retryingClient.execute(testPrompt, testModel, emptyList())

        assertEquals(testResponse, result)
        assertEquals(2, mockClient.executeCalls)
    }

    @Test
    fun testNoRetryOnCancellation() = runTest {
        val mockClient = MockLLMClient(
            executeResponse = testResponse,
            throwCancellation = true
        )

        val retryingClient = RetryingLLMClient(
            mockClient,
            RetryConfig(maxAttempts = 3)
        )

        assertFailsWith<CancellationException> {
            retryingClient.execute(testPrompt, testModel, emptyList())
        }

        assertEquals(1, mockClient.executeCalls) // No retry on cancellation
    }

    @Test
    fun testDisabledRetryConfig() = runTest {
        val mockClient = MockLLMClient(
            executeResponse = testResponse,
            failuresBeforeSuccess = 1,
            failureMessage = "Error: 503"
        )

        val retryingClient = RetryingLLMClient(
            mockClient,
            RetryConfig.DISABLED
        )

        assertFailsWith<RuntimeException> {
            retryingClient.execute(testPrompt, testModel, emptyList())
        }

        assertEquals(1, mockClient.executeCalls) // No retry with DISABLED config
    }

    @Test
    fun testStreamingSucceedOnFirstAttempt() = runTest {
        val mockClient = MockLLMClient(
            streamResponse = streamFrameFlowOf("chunk1", "chunk2"),
            streamFailuresBeforeSuccess = 0
        )

        val retryingClient = RetryingLLMClient(
            mockClient,
            RetryConfig(
                maxAttempts = 2
            )
        )

        val result = retryingClient.executeStreaming(testPrompt, testModel).toList()

        assertEquals(listOf("chunk1", "chunk2").map(StreamFrame::Append), result)
        assertEquals(1, mockClient.streamCalls)
    }

    @Test
    fun testStreamingWithRetry() = runTest {
        val mockClient = MockLLMClient(
            streamResponse = streamFrameFlowOf("chunk1", "chunk2"),
            streamFailuresBeforeSuccess = 1,
            failureMessage = "Error: 503"
        )

        val retryingClient = RetryingLLMClient(
            mockClient,
            RetryConfig(
                maxAttempts = 2,
                initialDelay = 10.milliseconds,
            )
        )

        val result = retryingClient.executeStreaming(testPrompt, testModel).toList()

        assertEquals(listOf("chunk1", "chunk2").map(StreamFrame::Append), result)
        assertEquals(2, mockClient.streamCalls)
    }

    @Test
    fun testStreamingNoRetryAfterFirstToken() = runTest {
        // Mock that emits one token then fails
        val mockClient = MockLLMClient(
            streamResponse = streamFrameFlow {
                emitAppend("first-token")
                throw RuntimeException("Connection lost after first token")
            }
        )

        val retryingClient = RetryingLLMClient(
            mockClient,
            RetryConfig(
                maxAttempts = 3,
            )
        )

        // Should not retry because we already received a token
        val exception = assertFailsWith<RuntimeException> {
            retryingClient.executeStreaming(testPrompt, testModel).collect()
        }

        assertEquals("Connection lost after first token", exception.message)
        assertEquals(1, mockClient.streamCalls) // No retry
    }

    @Test
    fun testRetryMultipleChoices() = runTest {
        val choices = listOf(
            listOf(Message.Assistant("Choice 1", testMetaInfo)),
            listOf(Message.Assistant("Choice 2", testMetaInfo))
        )

        val mockClient = MockLLMClient(
            multipleChoicesResponse = choices,
            failuresBeforeSuccess = 1,
            failureMessage = "Error: 429"
        )

        val retryingClient = RetryingLLMClient(
            mockClient,
            RetryConfig(
                maxAttempts = 2,
                initialDelay = 10.milliseconds
            )
        )

        val result = retryingClient.executeMultipleChoices(testPrompt, testModel, emptyList())

        assertEquals(choices, result)
        assertEquals(2, mockClient.multipleChoicesCalls)
    }

    @Test
    fun testRetryModerate() = runTest {
        val moderationResult = ModerationResult(
            isHarmful = false,
            categories = emptyMap()
        )

        val mockClient = MockLLMClient(
            moderateResponse = moderationResult,
            failuresBeforeSuccess = 1,
            failureMessage = "Error: 503"
        )

        val retryingClient = RetryingLLMClient(
            mockClient,
            RetryConfig(
                maxAttempts = 2,
                initialDelay = 10.milliseconds
            )
        )

        val result = retryingClient.moderate(testPrompt, testModel)

        assertEquals(moderationResult, result)
        assertEquals(2, mockClient.moderateCalls)
    }

    // Mock LLMClient for testing
    private class MockLLMClient(
        private val executeResponse: List<Message.Response> = emptyList(),
        private val streamResponse: Flow<StreamFrame> = flowOf(),
        private val multipleChoicesResponse: List<LLMChoice> = emptyList(),
        private val moderateResponse: ModerationResult = ModerationResult(false, emptyMap()),
        private var failuresBeforeSuccess: Int = 0,
        private var streamFailuresBeforeSuccess: Int = 0,
        private val failureMessage: String = "Mock failure",
        private val throwCancellation: Boolean = false,
        private val llmProvider: LLMProvider = LLMProvider.OpenAI,
    ) : LLMClient {

        var executeCalls = 0
        var streamCalls = 0
        var multipleChoicesCalls = 0
        var moderateCalls = 0

        private var executeFailures = 0
        private var streamFailures = 0
        private var multipleChoicesFailures = 0
        private var moderateFailures = 0

        override fun llmProvider(): LLMProvider = llmProvider

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> {
            executeCalls++

            if (throwCancellation) {
                throw CancellationException("Cancelled")
            }

            if (executeFailures < failuresBeforeSuccess) {
                executeFailures++
                throw RuntimeException(failureMessage)
            }

            return executeResponse
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Flow<StreamFrame> = flow {
            streamCalls++

            if (streamFailures < streamFailuresBeforeSuccess) {
                streamFailures++
                throw RuntimeException(failureMessage)
            }

            streamResponse.collect { emit(it) }
        }

        override suspend fun executeMultipleChoices(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<LLMChoice> {
            multipleChoicesCalls++

            if (multipleChoicesFailures < failuresBeforeSuccess) {
                multipleChoicesFailures++
                throw RuntimeException(failureMessage)
            }

            return multipleChoicesResponse
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
            moderateCalls++

            if (moderateFailures < failuresBeforeSuccess) {
                moderateFailures++
                throw RuntimeException(failureMessage)
            }

            return moderateResponse
        }
    }
}
