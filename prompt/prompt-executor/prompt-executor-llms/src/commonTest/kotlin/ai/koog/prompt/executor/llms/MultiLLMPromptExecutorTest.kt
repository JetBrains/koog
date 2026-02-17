package ai.koog.prompt.executor.llms

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.streaming.filterTextOnly
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MultiLLMPromptExecutorTest {

    @Test
    fun testExecuteWithOpenAI() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockLLMClient(provider = LLMProvider.OpenAI),
            LLMProvider.Anthropic to MockLLMClient(provider = LLMProvider.Anthropic),
            LLMProvider.Google to MockLLMClient(provider = LLMProvider.Google)
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
            LLMProvider.OpenAI to MockLLMClient(provider = LLMProvider.OpenAI),
            LLMProvider.Anthropic to MockLLMClient(provider = LLMProvider.Anthropic),
            LLMProvider.Google to MockLLMClient(provider = LLMProvider.Google)
        )

        val model = AnthropicModels.Opus_4_6
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
            LLMProvider.OpenAI to MockLLMClient(provider = LLMProvider.OpenAI),
            LLMProvider.Anthropic to MockLLMClient(provider = LLMProvider.Anthropic),
            LLMProvider.Google to MockLLMClient(provider = LLMProvider.Google)
        )

        val model = GoogleModels.Gemini2_0Flash
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = model).single()

        assertEquals("Google response", response.content)
    }

    @Test
    fun testExecuteStreamingWithOpenAI() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockLLMClient(provider = LLMProvider.OpenAI),
            LLMProvider.Anthropic to MockLLMClient(provider = LLMProvider.Anthropic),
            LLMProvider.Google to MockLLMClient(provider = LLMProvider.Google)
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
            MockLLMClient(provider = LLMProvider.OpenAI),
            MockLLMClient(provider = LLMProvider.Anthropic),
            MockLLMClient(provider = LLMProvider.Google)
        )

        val model = AnthropicModels.Opus_4_6
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
            MockLLMClient(provider = LLMProvider.OpenAI),
            MockLLMClient(provider = LLMProvider.Anthropic),
            MockLLMClient(provider = LLMProvider.Google)
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
            "Google streaming response",
            responseChunks.joinToString(""),
            "Response should be from Gemini client"
        )
    }

    @Test
    fun testExecuteWithUnsupportedProvider() = runTest {
        val executor = MultiLLMPromptExecutor(MockLLMClient(provider = LLMProvider.Google))

        val model = AnthropicModels.Opus_4_6
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
        val executor = MultiLLMPromptExecutor(LLMProvider.OpenAI to MockLLMClient(provider = LLMProvider.OpenAI))
        val model = AnthropicModels.Opus_4_6
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        assertFailsWith<IllegalArgumentException>("Should throw IllegalArgumentException for unsupported provider") {
            executor.executeStreaming(prompt, model).collect()
        }
    }
}
