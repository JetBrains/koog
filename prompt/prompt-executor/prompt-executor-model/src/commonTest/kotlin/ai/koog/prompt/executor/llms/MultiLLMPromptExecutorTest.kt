package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.ModelResolutionException
import ai.koog.prompt.executor.model.PromptExecutorOperation
import ai.koog.prompt.executor.model.ResolvedModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.filterTextOnly
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MultiLLMPromptExecutorTest {

    private class LegacyOverrideMultiLLMPromptExecutor(
        client: MockLLMClient
    ) : MultiLLMPromptExecutor(LLMProvider.OpenAI to client) {

        var executeCalls = 0
        var executeStreamingCalls = 0
        var executeMultipleChoicesCalls = 0
        var moderateCalls = 0

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Message.Assistant {
            executeCalls++
            return super.execute(prompt, model, tools)
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Flow<StreamFrame> {
            executeStreamingCalls++
            return super.executeStreaming(prompt, model, tools)
        }

        override suspend fun executeMultipleChoices(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): LLMChoice {
            executeMultipleChoicesCalls++
            return super.executeMultipleChoices(prompt, model, tools)
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
            moderateCalls++
            return super.moderate(prompt, model)
        }
    }

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

        assertFailsWith<ModelResolutionException> {
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

        assertFailsWith<ModelResolutionException> {
            executor.executeStreaming(prompt, model).collect()
        }
    }

    @Test
    fun testResolveModelReturnsRequestedWhenProviderRegistered() = runTest {
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to MockLLMClient(provider = LLMProvider.OpenAI),
            LLMProvider.Anthropic to MockLLMClient(provider = LLMProvider.Anthropic),
        )
        val requested = OpenAIModels.Chat.GPT4o

        assertEquals(
            ResolvedModel(requested),
            executor.resolveModel(requested, PromptExecutorOperation.Execute)
        )
    }

    @Test
    fun testResolveModelUsesFallbackWhenProviderUnregistered() = runTest {
        val fallbackModel = OpenAIModels.Chat.GPT4o
        val executor = MultiLLMPromptExecutor(
            llmClients = mapOf(LLMProvider.OpenAI to MockLLMClient(provider = LLMProvider.OpenAI)),
            fallback = MultiLLMPromptExecutor.FallbackPromptExecutorSettings(
                fallbackProvider = LLMProvider.OpenAI,
                fallbackModel = fallbackModel,
            ),
        )

        assertEquals(
            ResolvedModel(fallbackModel),
            executor.resolveModel(AnthropicModels.Opus_4_6, PromptExecutorOperation.Execute)
        )
    }

    @Test
    fun testResolveModelThrowsWhenNoClientAndNoFallback() = runTest {
        val executor = MultiLLMPromptExecutor(MockLLMClient(provider = LLMProvider.OpenAI))
        val requested = AnthropicModels.Opus_4_6

        assertFailsWith<ModelResolutionException> {
            executor.resolveModel(requested, PromptExecutorOperation.Execute)
        }
    }

    @Test
    fun testLegacyLLModelOverridesRemainSupported() = runTest {
        val executor = LegacyOverrideMultiLLMPromptExecutor(MockLLMClient(provider = LLMProvider.OpenAI))
        val model = OpenAIModels.Chat.GPT4o
        val prompt = Prompt.build("test-prompt") {
            user("Test message")
        }

        executor.execute(prompt, model)
        executor.executeStreaming(prompt, model).collect()
        executor.executeMultipleChoices(prompt, model, emptyList())
        executor.moderate(prompt, model)

        assertEquals(1, executor.executeCalls)
        assertEquals(1, executor.executeStreamingCalls)
        assertEquals(1, executor.executeMultipleChoicesCalls)
        assertEquals(1, executor.moderateCalls)
    }
}
