package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.filterTextOnly
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MultiLLMPromptExecutorTest {

    val mockClock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    // Mock client for Anthropic
    private inner class MockAnthropicLLMClient : LLMClient {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> {
            return listOf(Message.Assistant("Anthropic response", ResponseMetaInfo.create(clock = mockClock)))
        }

        override fun llmProvider(): LLMProvider = LLMProvider.Anthropic

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Flow<StreamFrame> =
            flowOf("Anthropic", " streaming", " response").map(StreamFrame::Append)

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
            throw UnsupportedOperationException("Moderation is not supported by mock client.")
        }

        override fun close() {
            // No resources to close
        }
    }

    // Mock client for Gemini
    private inner class MockGoogleLLMClient : LLMClient {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> {
            return listOf(Message.Assistant("Gemini response", ResponseMetaInfo.create(clock = mockClock)))
        }

        override fun llmProvider(): LLMProvider = LLMProvider.Google

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Flow<StreamFrame> =
            flowOf("Gemini", " streaming", " response").map(StreamFrame::Append)

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
            throw UnsupportedOperationException("Moderation is not supported by mock client.")
        }

        override fun close() {}
    }

    @Test
    fun testExecuteWithOpenAI() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockOpenAILLMClient(clock = mockClock),
            LLMProvider.Anthropic to MockAnthropicLLMClient(),
            LLMProvider.Google to MockGoogleLLMClient()
        )

        val model = OpenAIModels.Chat.GPT4o
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = model).single()

        assertEquals("OpenAI response", response.content)
    }

    @Test
    fun testExecuteWithAnthropic() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockOpenAILLMClient(clock = mockClock),
            LLMProvider.Anthropic to MockAnthropicLLMClient(),
            LLMProvider.Google to MockGoogleLLMClient()
        )

        val model = AnthropicModels.Sonnet_3_5
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = model).single()

        assertEquals("Anthropic response", response.content)
    }

    @Test
    fun testExecuteWithGoogle() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockOpenAILLMClient(clock = mockClock),
            LLMProvider.Anthropic to MockAnthropicLLMClient(),
            LLMProvider.Google to MockGoogleLLMClient()
        )

        val model = GoogleModels.Gemini2_0Flash
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = model).single()

        assertEquals("Gemini response", response.content)
    }

    @Test
    fun testExecuteStreamingWithOpenAI() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockOpenAILLMClient(clock = mockClock),
            LLMProvider.Anthropic to MockAnthropicLLMClient(),
            LLMProvider.Google to MockGoogleLLMClient()
        )

        val model = OpenAIModels.Chat.GPT4o
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val responseChunks = executor.executeStreaming(prompt, model)
            .filterTextOnly()
            .toList()
        assertEquals(3, responseChunks.size, "Response should have three chunks")
        assertEquals(
            "OpenAI streaming response",
            responseChunks.joinToString(""),
            "Response should be from OpenAI client"
        )
    }

    @Test
    fun testExecuteStreamingWithAnthropic() = runTest {
        val executor = MultiLLMPromptExecutor(
            MockOpenAILLMClient(clock = mockClock),
            MockAnthropicLLMClient(),
            MockGoogleLLMClient()
        )

        val model = AnthropicModels.Sonnet_3_7
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val responseChunks = executor.executeStreaming(prompt, model)
            .filterTextOnly()
            .toList()
        assertEquals(3, responseChunks.size, "Response should have three chunks")
        assertEquals(
            "Anthropic streaming response",
            responseChunks.joinToString(""),
            "Response should be from Anthropic client"
        )
    }

    @Test
    fun testExecuteStreamingWithGoogle() = runTest {
        val executor = MultiLLMPromptExecutor(
            MockOpenAILLMClient(clock = mockClock),
            MockAnthropicLLMClient(),
            MockGoogleLLMClient()
        )

        val model = GoogleModels.Gemini2_0Flash
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val responseChunks = executor.executeStreaming(prompt, model)
            .filterTextOnly()
            .toList()
        assertEquals(3, responseChunks.size, "Response should have three chunks")
        assertEquals(
            "Gemini streaming response",
            responseChunks.joinToString(""),
            "Response should be from Gemini client"
        )
    }

    @Test
    fun testExecuteWithUnsupportedProvider() = runTest {
        val executor = MultiLLMPromptExecutor()

        val model = AnthropicModels.Sonnet_3_7
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        assertFailsWith<IllegalArgumentException>("Should throw IllegalArgumentException for unsupported provider") {
            executor.execute(prompt = prompt, model = model)
        }
    }

    @Test
    fun testExecuteStreamingWithUnsupportedProvider() = runTest {
        val executor = MultiLLMPromptExecutor(LLMProvider.OpenAI to MockOpenAILLMClient(clock = mockClock))
        val model = AnthropicModels.Sonnet_3_7
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        assertFailsWith<IllegalArgumentException>("Should throw IllegalArgumentException for unsupported provider") {
            executor.executeStreaming(prompt, model).collect()
        }
    }
}
