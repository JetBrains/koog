package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
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
import kotlin.test.assertTrue

class RoutingLLMPromptExecutorTest {

    private val prompt = Prompt.build("test-prompt") {
        user("Test message")
    }

    private class SimpleTestRouter(override val clients: List<LLMClient>) : LLMClientRouter {
        constructor(vararg clients: LLMClient) : this(clients.toList())

        override fun clientFor(model: LLModel): LLMClient? =
            clients.firstOrNull { it.llmProvider() == model.provider }
    }

    private class LegacyOverrideRoutingLLMPromptExecutor(
        router: LLMClientRouter
    ) : RoutingLLMPromptExecutor(router) {

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
    fun testExecute() = runTest {
        // Given
        val client = MockLLMClient(provider = LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(SimpleTestRouter(client))

        // When
        val response = executor.execute(prompt, OpenAIModels.Chat.GPT4o)
        val textPart = assertIs<MessagePart.Text>(response.parts.single())
        val expectedTextPart = assertIs<MessagePart.Text>(client.executeResponse.parts.single())

        // Then
        assertEquals(expectedTextPart, textPart)
    }

    @Test
    fun testExecuteWithFallback() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val googleClient = MockLLMClient(provider = LLMProvider.Google)
        val fallback = RoutingLLMPromptExecutor.FallbackPromptExecutorSettings(
            fallbackModel = OpenAIModels.Chat.GPT4o
        )
        val executor = RoutingLLMPromptExecutor(
            clientRouter = SimpleTestRouter(openAIClient, googleClient),
            fallback = fallback
        )

        // When
        val response = executor.execute(prompt, AnthropicModels.Sonnet_4)

        // Then
        val textPart = assertIs<MessagePart.Text>(response.parts.single())
        val expectedTextPart = assertIs<MessagePart.Text>(openAIClient.executeResponse.parts.single())

        // Then
        assertEquals(expectedTextPart, textPart)
    }

    @Test
    fun testExecuteFailsOnClientNotFound() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val googleClient = MockLLMClient(provider = LLMProvider.Google)
        val executor = RoutingLLMPromptExecutor(
            clientRouter = SimpleTestRouter(openAIClient, googleClient),
            fallback = null
        )

        // When, Then
        assertFailsWith<ModelResolutionException> {
            executor.execute(prompt, AnthropicModels.Sonnet_4)
        }
    }

    @Test
    fun testExecuteStreaming() = runTest {
        // Given
        val client = MockLLMClient(provider = LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(SimpleTestRouter(client))

        // When
        val response = executor.executeStreaming(prompt, OpenAIModels.Chat.GPT4o)
            .filterTextOnly()
            .toList()

        // Then
        assertEquals(client.executeStreamingResponse.filterTextOnly().toList(), response)
    }

    @Test
    fun testExecuteStreamingWithFallback() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val googleClient = MockLLMClient(provider = LLMProvider.Google)
        val fallback = RoutingLLMPromptExecutor.FallbackPromptExecutorSettings(
            fallbackModel = OpenAIModels.Chat.GPT4o
        )
        val executor = RoutingLLMPromptExecutor(
            clientRouter = SimpleTestRouter(openAIClient, googleClient),
            fallback = fallback
        )

        // When
        val response = executor.executeStreaming(prompt, AnthropicModels.Sonnet_4)
            .filterTextOnly()
            .toList()

        // Then
        assertEquals(openAIClient.executeStreamingResponse.filterTextOnly().toList(), response)
    }

    @Test
    fun testExecuteStreamingFailsOnClientNotFound() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val googleClient = MockLLMClient(provider = LLMProvider.Google)
        val executor = RoutingLLMPromptExecutor(
            clientRouter = SimpleTestRouter(openAIClient, googleClient),
            fallback = null
        )

        // When, Then
        assertFailsWith<ModelResolutionException> {
            executor.executeStreaming(prompt, AnthropicModels.Sonnet_4).collect()
        }
    }

    @Test
    fun testExecuteMultipleChoices() = runTest {
        // Given
        val client = MockLLMClient(provider = LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(SimpleTestRouter(client))

        // When
        val choices = executor.executeMultipleChoices(prompt, OpenAIModels.Chat.GPT4o, emptyList())

        // Then
        assertEquals(client.executeMultipleChoicesResponse, choices)
    }

    @Test
    fun testExecuteMultipleChoicesWithFallback() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val googleClient = MockLLMClient(provider = LLMProvider.Google)
        val fallback = RoutingLLMPromptExecutor.FallbackPromptExecutorSettings(
            fallbackModel = OpenAIModels.Chat.GPT4o
        )
        val executor = RoutingLLMPromptExecutor(
            clientRouter = SimpleTestRouter(openAIClient, googleClient),
            fallback = fallback
        )

        // When
        val choices = executor.executeMultipleChoices(prompt, AnthropicModels.Sonnet_4, emptyList())

        // Then
        assertEquals(openAIClient.executeMultipleChoicesResponse, choices)
    }

    @Test
    fun testExecuteMultipleChoicesFailsOnClientNotFound() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val googleClient = MockLLMClient(provider = LLMProvider.Google)
        val executor = RoutingLLMPromptExecutor(
            clientRouter = SimpleTestRouter(openAIClient, googleClient),
            fallback = null
        )

        // When, Then
        assertFailsWith<ModelResolutionException> {
            executor.executeMultipleChoices(prompt, AnthropicModels.Sonnet_4, emptyList())
        }
    }

    @Test
    fun testModerate() = runTest {
        // Given
        val client = MockLLMClient(provider = LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(SimpleTestRouter(client))

        // When
        val result = executor.moderate(prompt, OpenAIModels.Chat.GPT4o)

        // Then
        assertEquals(client.moderateResponse, result)
    }

    @Test
    fun testModerateWithFallback() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val googleClient = MockLLMClient(provider = LLMProvider.Google)
        val fallback = RoutingLLMPromptExecutor.FallbackPromptExecutorSettings(
            fallbackModel = OpenAIModels.Chat.GPT4o
        )
        val executor = RoutingLLMPromptExecutor(
            clientRouter = SimpleTestRouter(openAIClient, googleClient),
            fallback = fallback
        )

        // When
        val result = executor.moderate(prompt, AnthropicModels.Sonnet_4)

        // Then
        assertEquals(openAIClient.moderateResponse, result)
    }

    @Test
    fun testModerateFailsOnClientNotFound() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val googleClient = MockLLMClient(provider = LLMProvider.Google)
        val executor = RoutingLLMPromptExecutor(
            clientRouter = SimpleTestRouter(openAIClient, googleClient),
            fallback = null
        )

        // When, Then
        assertFailsWith<ModelResolutionException> {
            executor.moderate(prompt, AnthropicModels.Sonnet_4)
        }
    }

    @Test
    fun testExecuteFailsWhenClientFails() = runTest {
        // Given
        val client = MockLLMClient.failingClientMock(LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(SimpleTestRouter(client))

        // When, Then
        val exception = assertFailsWith<IllegalStateException> {
            executor.execute(prompt, OpenAIModels.Chat.GPT4o)
        }
        assertEquals(client.executeFailure, exception)
    }

    @Test
    fun testExecuteStreamingFailsWhenClientFails() = runTest {
        // Given
        val client = MockLLMClient.failingClientMock(LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(SimpleTestRouter(client))

        // When
        val resultFlow = executor.executeStreaming(prompt, OpenAIModels.Chat.GPT4o)

        // Then
        val exception = assertFailsWith<IllegalStateException> { resultFlow.collect() }
        assertEquals(client.executeStreamingFailure, exception)
    }

    @Test
    fun testExecuteMultipleChoicesFailsWhenClientFails() = runTest {
        // Given
        val client = MockLLMClient.failingClientMock(LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(SimpleTestRouter(client))

        // When, Then
        val exception = assertFailsWith<IllegalStateException> {
            executor.executeMultipleChoices(prompt, OpenAIModels.Chat.GPT4o, emptyList())
        }
        assertEquals(client.executeMultipleChoicesFailure, exception)
    }

    @Test
    fun testModerateFailsWhenClientFails() = runTest {
        // Given
        val client = MockLLMClient.failingClientMock(LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(SimpleTestRouter(client))

        // When, Then
        val exception = assertFailsWith<IllegalStateException> {
            executor.moderate(prompt, OpenAIModels.Chat.GPT4o)
        }
        assertEquals(client.moderateFailure, exception)
    }

    @Test
    fun testModelsReturnsAllModelsFromAllClients() = runTest {
        // Given
        val clients = listOf(
            MockLLMClient(provider = LLMProvider.OpenAI, models = listOf(OpenAIModels.Chat.GPT4o)),
            MockLLMClient(provider = LLMProvider.Anthropic, models = listOf(AnthropicModels.Sonnet_4)),
            MockLLMClient(
                provider = LLMProvider.Google,
                models = listOf(GoogleModels.Gemini2_5Flash, GoogleModels.Gemini2_5Pro)
            )
        )

        // And
        val router = object : LLMClientRouter {
            override val clients = clients
            override fun clientFor(model: LLModel) = error("Not implemented")
        }

        // When
        val executorModels = RoutingLLMPromptExecutor(router).models()

        // Then
        assertEquals(clients.flatMap { it.models() }, executorModels)
    }

    @Test
    fun testCloseClosesAllClients() {
        val googleClient = MockLLMClient(provider = LLMProvider.Google)
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)

        val executor = RoutingLLMPromptExecutor(googleClient, openAIClient)
        executor.close()

        assertTrue(googleClient.wasClosed())
        assertTrue(openAIClient.wasClosed())
    }

    @Test
    fun testResolveModelReturnsRequestedWhenProviderInRouter() = runTest {
        val executor = RoutingLLMPromptExecutor(SimpleTestRouter(MockLLMClient(provider = LLMProvider.OpenAI)))
        val requested = OpenAIModels.Chat.GPT4o

        assertEquals(
            ResolvedModel(requested),
            executor.resolveModel(requested, PromptExecutorOperation.Execute)
        )
    }

    @Test
    fun testResolveModelUsesFallbackWhenProviderUnknown() = runTest {
        val fallbackModel = OpenAIModels.Chat.GPT4o
        val executor = RoutingLLMPromptExecutor(
            clientRouter = SimpleTestRouter(MockLLMClient(provider = LLMProvider.OpenAI)),
            fallback = RoutingLLMPromptExecutor.FallbackPromptExecutorSettings(fallbackModel = fallbackModel),
        )

        assertEquals(
            ResolvedModel(fallbackModel),
            executor.resolveModel(AnthropicModels.Sonnet_4, PromptExecutorOperation.Execute)
        )
    }

    @Test
    fun testResolveModelThrowsWhenNoClientAndNoFallback() = runTest {
        val executor = RoutingLLMPromptExecutor(SimpleTestRouter(MockLLMClient(provider = LLMProvider.OpenAI)))
        val requested = AnthropicModels.Sonnet_4

        assertFailsWith<ModelResolutionException> {
            executor.resolveModel(requested, PromptExecutorOperation.Execute)
        }
    }

    @Test
    fun testConstructorFailsWhenFallbackProviderNotInRouter() {
        val router = SimpleTestRouter(MockLLMClient(provider = LLMProvider.OpenAI))
        val fallback = RoutingLLMPromptExecutor.FallbackPromptExecutorSettings(
            fallbackModel = GoogleModels.Gemini2_5Flash,
        )

        assertFailsWith<IllegalStateException> {
            RoutingLLMPromptExecutor(clientRouter = router, fallback = fallback)
        }
    }

    @Test
    fun testLegacyLLModelOverridesRemainSupported() = runTest {
        val executor = LegacyOverrideRoutingLLMPromptExecutor(
            SimpleTestRouter(MockLLMClient(provider = LLMProvider.OpenAI))
        )
        val model = OpenAIModels.Chat.GPT4o

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
