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

class RoundRobinBasedExecutorTest {

    private val prompt = Prompt.build("test-prompt") {
        user("Test message")
    }

    @Test
    fun testExecuteWithSingleClient() = runTest {
        // Given
        val client = MockLLMClient(LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(RoundRobinRouter(client))

        // When
        val response = executor.execute(
            prompt = prompt,
            model = OpenAIModels.Chat.GPT4o
        )

        // Then
        assertEquals(client.executeResponse, response)
    }

    @Test
    fun testExecuteWithClientsFromSingleProvider() = runTest {
        // Given
        val firstClient = MockLLMClient(provider = LLMProvider.OpenAI, executeContent = "Response A")
        val secondClient = MockLLMClient(provider = LLMProvider.OpenAI, executeContent = "Response B")
        val thirdClient = MockLLMClient(provider = LLMProvider.OpenAI, executeContent = "Response C")
        val executor = RoutingLLMPromptExecutor(firstClient, secondClient, thirdClient)

        // When
        val responses = (1..6).map {
            executor.execute(prompt, OpenAIModels.Chat.GPT4o)
        }

        // Then
        assertEquals(firstClient.executeResponse, responses[0])
        assertEquals(secondClient.executeResponse, responses[1])
        assertEquals(thirdClient.executeResponse, responses[2])
        assertEquals(firstClient.executeResponse, responses[3])
        assertEquals(secondClient.executeResponse, responses[4])
        assertEquals(thirdClient.executeResponse, responses[5])
    }

    @Test
    fun testExecuteWithMultipleClientsAndProviders() = runTest {
        // Given
        val firstOpenAI = MockLLMClient(provider = LLMProvider.OpenAI, executeContent = "First OpenAI")
        val secondOpenAI = MockLLMClient(provider = LLMProvider.OpenAI, executeContent = "Second OpenAI")
        val firstAnthropicClient = MockLLMClient(provider = LLMProvider.Anthropic, executeContent = "First Anthropic")
        val secondAnthropicClient = MockLLMClient(provider = LLMProvider.Anthropic, executeContent = "Second Anthropic")
        val executor = RoutingLLMPromptExecutor(
            mapOf(
                LLMProvider.OpenAI to listOf(firstOpenAI, secondOpenAI),
                LLMProvider.Anthropic to listOf(firstAnthropicClient, secondAnthropicClient)
            )
        )

        // When
        val openAIResponses = (1..3).map {
            executor.execute(prompt, OpenAIModels.Chat.GPT4o)
        }
        val anthropicResponses = (1..5).map {
            executor.execute(prompt, AnthropicModels.Sonnet_4)
        }

        // Then
        assertEquals(firstOpenAI.executeResponse, openAIResponses[0])
        assertEquals(secondOpenAI.executeResponse, openAIResponses[1])
        assertEquals(firstOpenAI.executeResponse, openAIResponses[2])
        assertEquals(firstAnthropicClient.executeResponse, anthropicResponses[0])
        assertEquals(secondAnthropicClient.executeResponse, anthropicResponses[1])
        assertEquals(firstAnthropicClient.executeResponse, anthropicResponses[2])
        assertEquals(secondAnthropicClient.executeResponse, anthropicResponses[3])
        assertEquals(firstAnthropicClient.executeResponse, anthropicResponses[4])
    }

    @Test
    fun testExecuteStreamingForSingleProvider() = runTest {
        // Given
        val firstClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val secondClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(firstClient, secondClient)

        // When
        val streamingResponses = (1..2).map {
            executor.executeStreaming(prompt, OpenAIModels.Chat.GPT4o)
                .filterTextOnly()
                .toList()
        }

        // Then
        assertEquals(firstClient.executeStreamingResponse.filterTextOnly().toList(), streamingResponses[0])
        assertEquals(secondClient.executeStreamingResponse.filterTextOnly().toList(), streamingResponses[1])
    }

    @Test
    fun testExecuteWithUnsupportedProvider() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(openAIClient)

        // When, Then
        assertFailsWith<IllegalArgumentException> {
            executor.execute(prompt, AnthropicModels.Sonnet_4)
        }
    }

    @Test
    fun testExecuteStreamingWithUnsupportedProvider() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(openAIClient)

        // When, Then
        assertFailsWith<IllegalArgumentException> {
            executor.executeStreaming(prompt, AnthropicModels.Sonnet_4).collect()
        }
    }

    @Test
    fun testFallbackWithClientInRouter() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val anthropicClient = MockLLMClient(provider = LLMProvider.Anthropic)
        val fallback = RoutingLLMPromptExecutor.FallbackPromptExecutorSettings(AnthropicModels.Sonnet_4)
        val executor = RoutingLLMPromptExecutor(openAIClient, anthropicClient, fallback = fallback)

        // When
        val fallbackExecuteResponse = executor.execute(prompt, GoogleModels.Gemini2_0Flash)
        val fallbackExecuteStreamingResponse = executor.executeStreaming(prompt, GoogleModels.Gemini2_0Flash)
        val fallbackExecuteMultipleChoicesResponse = executor.executeMultipleChoices(
            prompt,
            GoogleModels.Gemini2_0Flash,
            emptyList()
        )
        val fallbackModerateResponse = executor.moderate(prompt, GoogleModels.Gemini2_0Flash)

        // Then
        assertEquals(anthropicClient.executeResponse, fallbackExecuteResponse)
        assertEquals(
            anthropicClient.executeStreamingResponse.toList(),
            fallbackExecuteStreamingResponse.toList()
        )
        assertEquals(anthropicClient.executeMultipleChoicesResponse, fallbackExecuteMultipleChoicesResponse)
        assertEquals(anthropicClient.moderateResponse, fallbackModerateResponse)
    }

    @Test
    fun testFallbackClientNotFoundInRouterFails() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val fallback = RoutingLLMPromptExecutor.FallbackPromptExecutorSettings(AnthropicModels.Sonnet_4)

        // When, Then
        assertFailsWith<IllegalStateException> {
            RoutingLLMPromptExecutor(openAIClient, fallback = fallback)
        }
    }
}
