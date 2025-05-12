package ai.jetbrains.code.prompt.executor.llms

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.clients.LLMClient
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.llm.LLMProvider
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LLMPromptExecutorTest {

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
        override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
            return listOf(Message.Assistant("Anthropic response"))
        }

        override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
            return flowOf("Anthropic", " streaming", " response")
        }
    }

    @Test
    fun testExecuteWithOpenAI() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockOpenAILLMClient(),
            LLMProvider.Anthropic to MockAnthropicLLMClient()
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
            LLMProvider.Anthropic to MockAnthropicLLMClient()
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
    fun testExecuteStreamingWithOpenAI() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockOpenAILLMClient(),
            LLMProvider.Anthropic to MockAnthropicLLMClient()
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
            LLMProvider.Anthropic to MockAnthropicLLMClient()
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
