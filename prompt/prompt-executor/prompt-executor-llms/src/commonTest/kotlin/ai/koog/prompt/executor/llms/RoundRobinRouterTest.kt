package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RoundRobinRouterTest {

    @Test
    fun shouldAlternateBetweenClientsOfSameProvider() {
        // Given: 2 OpenAI clients
        val firstOpenAIClient = TestLLMClient("FirstOpenAIClient", LLMProvider.OpenAI)
        val secondOpenAIClient = TestLLMClient("SecondOpenAIClient", LLMProvider.OpenAI)
        val router = RoundRobinRouter(listOf(firstOpenAIClient, secondOpenAIClient))

        // And: An OpenAI model
        val model = LLModel(LLMProvider.OpenAI, "gpt-4")

        // When: Making multiple requests
        val chosenClients = (1..5).map { router.clientFor(model) }

        // Then: Clients alternate in round-robin fashion
        assertEquals(
            listOf(firstOpenAIClient, secondOpenAIClient, firstOpenAIClient, secondOpenAIClient, firstOpenAIClient),
            chosenClients
        )
    }

    @Test
    fun shouldRoundRobinIndependentlyPerProvider() {
        // Given: 2 OpenAI clients and 1 Anthropic client
        val firstOpenAIClient = TestLLMClient("FirstOpenAIClient", LLMProvider.OpenAI)
        val secondOpenAIClient = TestLLMClient("SecondOpenAIClient", LLMProvider.OpenAI)
        val anthropicClient = TestLLMClient("AnthropicClient", LLMProvider.Anthropic)
        val router = RoundRobinRouter(listOf(firstOpenAIClient, secondOpenAIClient, anthropicClient))

        // And: Models from both OpenAI and Anthropic
        val gpt4 = LLModel(LLMProvider.OpenAI, "gpt-4")
        val claude = LLModel(LLMProvider.Anthropic, "claude-3")

        // When: Interleaving requests across providers
        val chosenClients = listOf(
            router.clientFor(gpt4),
            router.clientFor(claude),
            router.clientFor(gpt4),
            router.clientFor(claude),
            router.clientFor(gpt4)
        )

        // Then: Each provider maintains independent round-robin state
        assertEquals(
            listOf(firstOpenAIClient, anthropicClient, secondOpenAIClient, anthropicClient, firstOpenAIClient),
            chosenClients
        )
    }

    @Test
    fun shouldReturnNullForUnsupportedProvider() {
        // Given: Only OpenAI clients available
        val openAIClient = TestLLMClient("OpenAIClient", LLMProvider.OpenAI)
        val router = RoundRobinRouter(listOf(openAIClient))

        // When: Requesting a model from Anthropic (unsupported provider)
        val anthropicModel = LLModel(LLMProvider.Anthropic, "claude-3")
        val result = router.clientFor(anthropicModel)

        // Then: Returns null
        assertNull(result)
    }

    @Test
    fun shouldAlwaysReturnSameClientWhenOnlyOneAvailable() {
        // Given: Single client
        val openAIClient = TestLLMClient("OpenAIClient", LLMProvider.OpenAI)
        val router = RoundRobinRouter(listOf(openAIClient))

        // And: A model of matching provider
        val model = LLModel(LLMProvider.OpenAI, "gpt-4")

        // When: Making multiple requests
        val chosenClients = (1..3).map { router.clientFor(model) }

        // Then: Always returns the same client
        assertEquals(
            listOf(openAIClient, openAIClient, openAIClient),
            chosenClients
        )
    }

    @Test
    fun shouldFailWhenNoClientsProvided() {
        // When/Then: Creating router with empty list throws
        assertFailsWith<IllegalArgumentException> {
            RoundRobinRouter(emptyList())
        }
    }

    @Test
    fun shouldRoundRobinThroughClientsOfSameProvider() {
        // Given: 3 clients of same provider
        val firstGoogleClient = TestLLMClient("FirstGoogleClient", LLMProvider.Google)
        val secondGoogleClient = TestLLMClient("SecondGoogleClient", LLMProvider.Google)
        val thirdGoogleClient = TestLLMClient("ThirdGoogleClient", LLMProvider.Google)
        val router = RoundRobinRouter(listOf(firstGoogleClient, secondGoogleClient, thirdGoogleClient))

        // And: A model of that provider
        val model = LLModel(LLMProvider.Google, "gemini-pro")

        // When: Making requests
        val chosenClients = (1..7).map { router.clientFor(model) }

        // Then: Cycles through all 3 clients and wraps around
        assertEquals(
            listOf(
                firstGoogleClient,
                secondGoogleClient,
                thirdGoogleClient,
                firstGoogleClient,
                secondGoogleClient,
                thirdGoogleClient,
                firstGoogleClient
            ),
            chosenClients
        )
    }

    private class TestLLMClient(
        override val clientName: String,
        private val provider: LLMProvider
    ) : LLMClient {

        override fun llmProvider(): LLMProvider = provider

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> {
            throw NotImplementedError("Not implemented for test")
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
            throw NotImplementedError("Not implemented for test")
        }

        override fun close() {
            throw NotImplementedError("Not implemented for test")
        }
    }
}
