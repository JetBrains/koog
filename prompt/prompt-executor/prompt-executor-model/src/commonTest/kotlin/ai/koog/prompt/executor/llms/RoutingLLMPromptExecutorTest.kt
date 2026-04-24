package ai.koog.prompt.executor.llms

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.ExecutionArgOverrides
import ai.koog.prompt.executor.model.InitialExecutionIntent
import ai.koog.prompt.executor.model.PromptExecutorHooks
import ai.koog.prompt.executor.model.ResolvedExecutionIntent
import ai.koog.prompt.executor.model.SimpleExecutorHook
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.filterTextOnly
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    private class ModelCapturingClient(private val provider: LLMProvider) : LLMClient() {
        var lastModelUsedForExecute: LLModel? = null

        override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ai.koog.agents.core.tools.ToolDescriptor>): List<Message.Response> {
            lastModelUsedForExecute = model
            return listOf(Message.Assistant("ok", ResponseMetaInfo.Empty))
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ai.koog.agents.core.tools.ToolDescriptor>
        ): Flow<StreamFrame> = flowOf(StreamFrame.TextDelta("ok"))

        override suspend fun executeMultipleChoices(
            prompt: Prompt,
            model: LLModel,
            tools: List<ai.koog.agents.core.tools.ToolDescriptor>
        ): List<LLMChoice> = emptyList()

        override suspend fun moderate(prompt: Prompt, model: LLModel) = ai.koog.prompt.dsl.ModerationResult(false, emptyMap())

        override suspend fun models(): List<LLModel> = emptyList()

        override fun llmProvider(): LLMProvider = provider

        override fun close() {}
    }

    @Test
    fun testExecute() = runTest {
        // Given
        val client = MockLLMClient(provider = LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(SimpleTestRouter(client))

        // When
        val response = executor.execute(prompt, OpenAIModels.Chat.GPT4o)

        // Then
        assertEquals(client.executeResponse.single().content, response.single().content)
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
        assertEquals(openAIClient.executeResponse.single().content, response.single().content)
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
        assertFailsWith<IllegalArgumentException> {
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
        assertFailsWith<IllegalArgumentException> {
            executor.executeStreaming(prompt, AnthropicModels.Sonnet_4).collect()
        }
    }

    @Test
    fun testExecuteMultipleChoices() = runTest {
        // Given
        val client = MockLLMClient(provider = LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(SimpleTestRouter(client))

        // When
        val choices = executor.executeMultipleChoices(prompt, OpenAIModels.Chat.GPT4o)

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
        val choices = executor.executeMultipleChoices(prompt, AnthropicModels.Sonnet_4)

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
        assertFailsWith<IllegalArgumentException> {
            executor.executeMultipleChoices(prompt, AnthropicModels.Sonnet_4)
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
        assertFailsWith<IllegalArgumentException> {
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
            executor.executeMultipleChoices(prompt, OpenAIModels.Chat.GPT4o)
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
                models = listOf(GoogleModels.Gemini2_0Flash, GoogleModels.Gemini2_5Pro)
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
    fun testHooksExposeFallbackAsEffectiveModel() = runTest {
        // Given
        val openAIClient = ModelCapturingClient(LLMProvider.OpenAI)
        val fallbackModel = OpenAIModels.Chat.GPT4o
        val executor = RoutingLLMPromptExecutor(
            clientRouter = SimpleTestRouter(openAIClient),
            fallback = RoutingLLMPromptExecutor.FallbackPromptExecutorSettings(fallbackModel)
        )
        var beforeIntent: InitialExecutionIntent? = null
        var beforeEffectiveModel: LLModel? = null
        var completedIntent: ResolvedExecutionIntent? = null
        var completedEffectiveModel: LLModel? = null

        // And
        val hooks = object : PromptExecutorHooks {
            override val execute = object : SimpleExecutorHook<List<Message.Response>> {
                override suspend fun beforeExecution(
                    intent: InitialExecutionIntent,
                    effectiveModel: LLModel
                ): ExecutionArgOverrides {
                    beforeIntent = intent
                    beforeEffectiveModel = effectiveModel
                    return ExecutionArgOverrides.NoOverrides
                }

                override suspend fun onCompleted(
                    intent: ResolvedExecutionIntent,
                    effectiveModel: LLModel,
                    result: List<Message.Response>
                ) {
                    completedIntent = intent
                    completedEffectiveModel = effectiveModel
                }
            }
        }

        // When
        val requestedModel = AnthropicModels.Sonnet_4
        executor.execute(prompt, requestedModel, hooks = hooks)

        // Then
        assertEquals(requestedModel, beforeIntent?.model)
        assertEquals(fallbackModel, beforeEffectiveModel)
        assertEquals(requestedModel, completedIntent?.model)
        assertEquals(fallbackModel, completedEffectiveModel)
        assertEquals(fallbackModel, openAIClient.lastModelUsedForExecute)
    }

    @Test
    fun testHooksReceiveModelChoiceFailure() = runTest {
        // Given
        val openAIClient = MockLLMClient(provider = LLMProvider.OpenAI)
        val executor = RoutingLLMPromptExecutor(clientRouter = SimpleTestRouter(openAIClient), fallback = null)
        var modelChoiceFailedIntent: InitialExecutionIntent? = null
        var modelChoiceError: Throwable? = null

        // And
        val hooks = object : PromptExecutorHooks {
            override val execute = object : SimpleExecutorHook<List<Message.Response>> {
                override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) {
                    modelChoiceFailedIntent = intent
                    modelChoiceError = error
                }
            }
        }

        // When
        val requestedModel = AnthropicModels.Sonnet_4
        val thrown = assertFailsWith<IllegalArgumentException> {
            executor.execute(prompt, requestedModel, hooks = hooks)
        }

        // Then
        assertEquals(requestedModel, modelChoiceFailedIntent?.model)
        assertEquals(thrown, modelChoiceError)
    }
}
