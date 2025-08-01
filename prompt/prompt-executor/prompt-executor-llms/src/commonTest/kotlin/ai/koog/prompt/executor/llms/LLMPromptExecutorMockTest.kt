package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutorExt.execute
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LLMPromptExecutorMockTest {

    val mockClock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    // Mock client for OpenAI
    private inner class MockOpenAILLMClient : LLMClient {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> {
            return listOf(Message.Assistant("OpenAI response", ResponseMetaInfo.create(mockClock)))
        }

        override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
            return flowOf("OpenAI", " streaming", " response")
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
            throw UnsupportedOperationException("Moderation is not supported by mock client.")
        }
    }

    // Mock client for Anthropic
    private inner class MockAnthropicLLMClient : LLMClient {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> {
            return listOf(Message.Assistant("Anthropic response", ResponseMetaInfo.create(mockClock)))
        }

        override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
            return flowOf("Anthropic", " streaming", " response")
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
            throw UnsupportedOperationException("Moderation is not supported by mock client.")
        }
    }

    // Mock client for Gemini
    private inner class MockGoogleLLMClient : LLMClient {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> {
            return listOf(Message.Assistant("Gemini response", ResponseMetaInfo.create(mockClock)))
        }

        override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
            return flowOf("Gemini", " streaming", " response")
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
            throw UnsupportedOperationException("Moderation is not supported by mock client.")
        }
    }

    @Test
    fun testExecuteWithOpenAI() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockOpenAILLMClient(),
            LLMProvider.Anthropic to MockAnthropicLLMClient(),
            LLMProvider.Google to MockGoogleLLMClient()
        )

        val model = OpenAIModels.Chat.GPT4o
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = model)

        assertEquals("OpenAI response", response.content)
    }

    @Test
    fun testExecuteWithAnthropic() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockOpenAILLMClient(),
            LLMProvider.Anthropic to MockAnthropicLLMClient(),
            LLMProvider.Google to MockGoogleLLMClient()
        )

        val model = AnthropicModels.Sonnet_3_5
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = model)

        assertEquals("Anthropic response", response.content)
    }

    @Test
    fun testExecuteWithGoogle() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockOpenAILLMClient(),
            LLMProvider.Anthropic to MockAnthropicLLMClient(),
            LLMProvider.Google to MockGoogleLLMClient()
        )

        val model = GoogleModels.Gemini2_0Flash
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = model)

        assertEquals("Gemini response", response.content)
    }

    @Test
    fun testExecuteStreamingWithOpenAI() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockOpenAILLMClient(),
            LLMProvider.Anthropic to MockAnthropicLLMClient(),
            LLMProvider.Google to MockGoogleLLMClient()
        )

        val model = OpenAIModels.Chat.GPT4o
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val responseChunks = executor.executeStreaming(prompt, model).toList()
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
            LLMProvider.OpenAI to MockOpenAILLMClient(),
            LLMProvider.Anthropic to MockAnthropicLLMClient(),
            LLMProvider.Google to MockGoogleLLMClient()
        )

        val model = AnthropicModels.Sonnet_3_7
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val responseChunks = executor.executeStreaming(prompt, model).toList()
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
            LLMProvider.OpenAI to MockOpenAILLMClient(),
            LLMProvider.Anthropic to MockAnthropicLLMClient(),
            LLMProvider.Google to MockGoogleLLMClient()
        )

        val model = GoogleModels.Gemini2_0Flash
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val responseChunks = executor.executeStreaming(prompt, model).toList()
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
        val executor = MultiLLMPromptExecutor(LLMProvider.OpenAI to MockOpenAILLMClient())
        val model = AnthropicModels.Sonnet_3_7
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        assertFailsWith<IllegalArgumentException>("Should throw IllegalArgumentException for unsupported provider") {
            executor.executeStreaming(prompt, model).toList()
        }
    }
}
