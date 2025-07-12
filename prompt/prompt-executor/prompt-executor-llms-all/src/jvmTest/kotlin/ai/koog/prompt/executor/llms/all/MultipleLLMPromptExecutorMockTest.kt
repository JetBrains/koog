package ai.koog.prompt.executor.llms.all

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutorExt.execute
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MultipleLLMPromptExecutorMockTest {

    companion object {
        private const val API_KEY = "fake-key"
    }

    val mockClock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    // Mock client for OpenAI
    private inner class MockOpenAILLMClient : OpenAILLMClient(API_KEY) {
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
    }

    // Mock client for Anthropic
    private inner class MockAnthropicLLMClient : AnthropicLLMClient(API_KEY) {
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
    }

    // Mock client for Anthropic
    private inner class MockGoogleLLMClient : GoogleLLMClient(API_KEY) {
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
    }

    private lateinit var executor: DefaultMultiLLMPromptExecutor

    @BeforeTest
    fun initializeExecutor() {
        executor = DefaultMultiLLMPromptExecutor(
            openAIClient = MockOpenAILLMClient(),
            anthropicClient = MockAnthropicLLMClient(),
            googleClient = MockGoogleLLMClient()
        )
    }

    @Test
    fun testExecuteWithOpenAI() = runTest {
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = OpenAIModels.Chat.GPT4o)

        assertEquals(
            "OpenAI response",
            response.content,
            "Response should be from OpenAI client"
        )
    }

    @Test
    fun testExecuteWithAnthropic() = runTest {
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = AnthropicModels.Sonnet_3_7)

        assertEquals(
            "Anthropic response",
            response.content,
            "Response should be from Anthropic client")
    }

    @Test
    fun testExecuteWithGoogle() = runTest {
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = GoogleModels.Gemini2_0Flash)

        assertEquals(
            "Gemini response",
            response.content,
            "Response should be from Google client")
    }

    @Test
    fun testExecuteStreamingWithOpenAI() = runTest {
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val responseChunks = executor.executeStreaming(prompt, OpenAIModels.Chat.GPT4o).toList()

        assertEquals(3, responseChunks.size, "Response should have three chunks")
        assertEquals(
            "OpenAI streaming response",
            responseChunks.joinToString(""),
            "Response should be from OpenAI client"
        )
    }

    @Test
    fun testExecuteStreamingWithAnthropic() = runTest {
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val responseChunks = executor.executeStreaming(prompt, AnthropicModels.Sonnet_3_7).toList()

        assertEquals(3, responseChunks.size, "Response should have three chunks")
        assertEquals(
            "Anthropic streaming response",
            responseChunks.joinToString(""),
            "Response should be from Anthropic client"
        )
    }

    @Test
    fun testExecuteStreamingWithGoogle() = runTest {
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val responseChunks = executor.executeStreaming(prompt, GoogleModels.Gemini2_0Flash).toList()

        assertEquals(3, responseChunks.size, "Response should have three chunks")
        assertEquals(
            "Gemini streaming response",
            responseChunks.joinToString(""),
            "Response should be from Google client"
        )
    }
}