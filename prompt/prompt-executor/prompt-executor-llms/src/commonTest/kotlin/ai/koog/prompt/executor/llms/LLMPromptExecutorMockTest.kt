package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LLMPromptExecutorMockTest {

    // Mock client for OpenAI
    private class MockOpenAILLMClient : LLMClient {
        override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
            return listOf(Message.Assistant("OpenAI response"))
        }

        override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
            return flowOf("OpenAI", " streaming", " response")
        }
    }

    // Mock client for Anthropic
    private class MockAnthropicLLMClient : LLMClient {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> {
            return listOf(Message.Assistant("Anthropic response"))
        }

        override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
            return flowOf("Anthropic", " streaming", " response")
        }
    }

    // Mock client for Gemini
    private class MockGoogleLLMClient : LLMClient {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> {
            return listOf(Message.Assistant("Gemini response"))
        }

        override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
            return flowOf("Gemini", " streaming", " response")
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

        val response = executor.execute(prompt, model)

        assertEquals("OpenAI response", response)
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

        val response = executor.execute(prompt, model)

        assertEquals("Anthropic response", response)
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

        val response = executor.execute(prompt, model)

        assertEquals("Gemini response", response)
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
            executor.execute(prompt, model)
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
