package ai.koog.prompt.executor.llms

import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.builder.MultiLLMPromptExecutorBuilder
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.factory.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorOperation
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.filterTextOnly
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

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

        val response = executor.execute(prompt = prompt, model = model)
        val textPart = assertIs<MessagePart.Text>(response.parts.single())
        assertEquals("OpenAI response", textPart.text)
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

        val response = executor.execute(prompt = prompt, model = model)
        val textPart = assertIs<MessagePart.Text>(response.parts.single())
        assertEquals("Anthropic response", textPart.text)
    }

    @Test
    fun testExecuteWithGoogle() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockLLMClient(provider = LLMProvider.OpenAI),
            LLMProvider.Anthropic to MockLLMClient(provider = LLMProvider.Anthropic),
            LLMProvider.Google to MockLLMClient(provider = LLMProvider.Google)
        )

        val model = GoogleModels.Gemini2_5Flash
        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt = prompt, model = model)
        val textPart = assertIs<MessagePart.Text>(response.parts.single())

        assertEquals("Google response", textPart.text)
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

        val model = GoogleModels.Gemini2_5Flash
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

    // Regression test: ensures resolveModel() on a built PromptExecutor returns the fallback model
    // when the requested provider is not registered. Decorators (e.g. ContextualPromptExecutorBuilder)
    // rely on this to fire pipeline events with the actual model that will run.
    @Test
    fun testResolveModelReturnsFallbackForUnregisteredProvider() = runTest {
        val executor = MultiLLMPromptExecutor(
            llmClients = mapOf(LLMProvider.Anthropic to MockLLMClient(provider = LLMProvider.Anthropic)),
            fallback = MultiLLMPromptExecutorBuilder.FallbackPromptExecutorSettings(
                fallbackProvider = LLMProvider.Anthropic,
                fallbackModel = AnthropicModels.Opus_4_6,
            ),
        )

        // Requested model's provider (Google) is not registered → resolve returns the Anthropic fallback.
        val resolved = executor.resolveModel(GoogleModels.Gemini2_5Flash, PromptExecutorOperation.Execute)
        assertEquals(AnthropicModels.Opus_4_6, resolved)

        // Idempotency contract: resolving an already-resolved model returns it unchanged.
        val twice = executor.resolveModel(resolved, PromptExecutorOperation.Execute)
        assertEquals(resolved, twice)

        // Streaming has no fallback today — resolution returns the requested model unchanged.
        val streaming = executor.resolveModel(GoogleModels.Gemini2_5Flash, PromptExecutorOperation.Streaming)
        assertEquals(GoogleModels.Gemini2_5Flash, streaming)
    }

    @Test
    fun testResolveModelIsIdentityForRegisteredProvider() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockLLMClient(provider = LLMProvider.OpenAI),
            LLMProvider.Anthropic to MockLLMClient(provider = LLMProvider.Anthropic),
        )

        val requested = OpenAIModels.Chat.GPT4o
        assertEquals(requested, executor.resolveModel(requested, PromptExecutorOperation.Execute))
        assertEquals(requested, executor.resolveModel(requested, PromptExecutorOperation.Streaming))
        assertEquals(requested, executor.resolveModel(requested, PromptExecutorOperation.Moderate))
        assertEquals(requested, executor.resolveModel(requested, PromptExecutorOperation.MultipleChoices))
    }
}
