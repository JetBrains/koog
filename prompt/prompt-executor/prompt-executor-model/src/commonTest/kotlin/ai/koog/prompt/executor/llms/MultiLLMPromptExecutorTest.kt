package ai.koog.prompt.executor.llms

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.selection.ExperimentalSelectionApi
import ai.koog.prompt.executor.selection.ModelSelection
import ai.koog.prompt.executor.selection.ModelSelector
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.filterTextOnly
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalSelectionApi::class)
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

    @Test
    fun testExecuteWithModelSelectorUsesTopRoutableModel() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val googleClient = MockLLMClient(provider = LLMProvider.Google)
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Google to googleClient
        )

        // And
        val selector = selectorReturning(OpenAIModels.Chat.GPT4o, GoogleModels.Gemini2_0Flash)

        // When
        val response = executor.execute(prompt, selector)

        // Then
        assertEquals(openAIClient.executeResponse.single().content, response.single().content)
    }

    @Test
    fun testExecuteWithModelSelectorSkipsUnroutableTopModelAndUsesNext() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val executor = MultiLLMPromptExecutor(LLMProvider.OpenAI to openAIClient)

        // And — Anthropic is ranked first but has no client; OpenAI is ranked second
        val selector = selectorReturning(AnthropicModels.Opus_4_6, OpenAIModels.Chat.GPT4o)

        // When
        val response = executor.execute(prompt, selector)

        // Then
        assertEquals(openAIClient.executeResponse.single().content, response.single().content)
    }

    @Test
    fun testExecuteWithModelSelectorUsesFallbackWhenNoRankedModelIsRoutable() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val googleClient = MockLLMClient(provider = LLMProvider.Google)
        val fallback = MultiLLMPromptExecutor.FallbackPromptExecutorSettings(
            fallbackProvider = LLMProvider.OpenAI,
            fallbackModel = OpenAIModels.Chat.GPT4o
        )
        val executor = MultiLLMPromptExecutor(
            mapOf(LLMProvider.OpenAI to openAIClient, LLMProvider.Google to googleClient),
            fallback
        )

        // And — only Anthropic in selection, but no Anthropic client registered
        val selector = selectorReturning(AnthropicModels.Opus_4_6)

        // When
        val response = executor.execute(prompt, selector)

        // Then
        assertEquals(openAIClient.executeResponse.single().content, response.single().content)
    }

    @Test
    fun testExecuteWithModelSelectorFailsWhenSelectionIsEmptyAndNoFallback() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val executor = MultiLLMPromptExecutor(LLMProvider.OpenAI to openAIClient)

        // And
        val selector = ModelSelector { ModelSelection.EMPTY }

        // When, Then
        assertFailsWith<IllegalArgumentException> {
            executor.execute(prompt, selector)
        }
    }

    @Test
    fun testExecuteWithModelSelectorReceivesExecutorModels() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val googleClient = MockLLMClient(provider = LLMProvider.Google)
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Google to googleClient
        )
        var modelsSeenBySelector: List<LLModel> = emptyList()
        val selector = ModelSelector { models ->
            modelsSeenBySelector = models
            ModelSelection.single(OpenAIModels.Chat.GPT4o)
        }

        // When
        executor.execute(prompt, selector)

        // Then
        assertEquals(executor.models(), modelsSeenBySelector)
    }

    @Test
    fun testExecuteStreamingWithModelSelectorSkipsUnroutableTopModelAndUsesNext() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val executor = MultiLLMPromptExecutor(LLMProvider.OpenAI to openAIClient)

        // And
        val selector = selectorReturning(AnthropicModels.Opus_4_6, OpenAIModels.Chat.GPT4o)

        // When
        val response = executor.executeStreaming(prompt, selector)
            .filterTextOnly()
            .toList()

        // Then
        assertEquals(openAIClient.executeStreamingResponse.filterTextOnly().toList(), response)
    }

    @Test
    fun testExecuteMultipleChoicesWithModelSelectorSkipsUnroutableTopModelAndUsesNext() = runTest {
        // Given
        val openAIChoices: List<LLMChoice> = listOf(
            listOf(Message.Assistant("openai-choice", ResponseMetaInfo.Empty))
        )
        val openAIClient = MockLLMClient(
            provider = LLMProvider.OpenAI,
            executeMultipleContent = openAIChoices
        )
        val executor = MultiLLMPromptExecutor(LLMProvider.OpenAI to openAIClient)

        // And
        val selector = selectorReturning(AnthropicModels.Opus_4_6, OpenAIModels.Chat.GPT4o)

        // When
        val choices = executor.executeMultipleChoices(prompt, selector, emptyList())

        // Then
        assertEquals(openAIChoices, choices)
    }

    @Test
    fun testModerateWithModelSelectorSkipsUnroutableTopModelAndUsesNext() = runTest {
        // Given
        val openAIModeration = ai.koog.prompt.dsl.ModerationResult(isHarmful = true, categories = emptyMap())
        val openAIClient = MockLLMClient(
            provider = LLMProvider.OpenAI,
            moderateContent = openAIModeration
        )
        val executor = MultiLLMPromptExecutor(LLMProvider.OpenAI to openAIClient)

        // And
        val selector = selectorReturning(AnthropicModels.Opus_4_6, OpenAIModels.Chat.GPT4o)

        // When
        val result = executor.moderate(prompt, selector)

        // Then
        assertEquals(openAIModeration, result)
    }

    private val prompt = Prompt.build("test-prompt") {
        user("Test message")
    }

    private fun selectorReturning(vararg ranked: LLModel): ModelSelector =
        ModelSelector { ModelSelection(ranked.toList()) }
}
