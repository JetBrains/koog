package ai.koog.prompt.executor.clients.openai

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.reflect.KClass

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenAILLMClientTest {

    /** What a user writes for a model Koog has no predefined configuration for: a self-hosted server. */
    private val selfHostedModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = "qwen3-27b",
        capabilities = listOf(LLMCapability.Completion, LLMCapability.Temperature, LLMCapability.Tools),
        contextLength = 262_144,
    )

    //language=json
    private val chatCompletionBody = """
        {
          "id": "chatcmpl-1",
          "object": "chat.completion",
          "created": 1716920005,
          "model": "qwen3-27b",
          "choices": [
            {
              "index": 0,
              "message": { "role": "assistant", "content": "hello from the server" },
              "finish_reason": "stop"
            }
          ]
        }
    """.trimIndent()

    private fun clientRecording(
        requests: MutableList<String>,
        baseUrl: String = "https://api.openai.com",
    ): OpenAILLMClient {
        val engine = MockEngine { request ->
            requests += request.url.encodedPath
            respond(
                content = chatCompletionBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return OpenAILLMClient(
            apiKey = "dummy-key",
            settings = OpenAIClientSettings(baseUrl = baseUrl),
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(engine)),
        )
    }

    fun openAiClientTestCases(): Stream<Arguments> =
        Stream.of(
            Arguments.of(
                LLMParams(),
                OpenAIModels.Chat.GPT4o,
                OpenAIChatParams::class,
            ),
            Arguments.of(
                LLMParams(),
                OpenAIModels.Chat.GPT5_5,
                OpenAIChatParams::class,
            ),
            Arguments.of(
                LLMParams(),
                OpenAIModels.Chat.GPT5_5Pro,
                OpenAIResponsesParams::class,
            ),
            Arguments.of(
                OpenAIChatParams(),
                OpenAIModels.Chat.GPT4o,
                OpenAIChatParams::class,
            ),
            Arguments.of(
                OpenAIResponsesParams(),
                OpenAIModels.Chat.GPT4o,
                OpenAIResponsesParams::class,
            ),
            Arguments.of(
                OpenAIChatParams(),
                OpenAIModels.Audio.GPT4oMiniAudio,
                OpenAIChatParams::class,
            ),
            // A model that declares no endpoint capability at all: chat completions, the API every
            // OpenAI-compatible server implements.
            Arguments.of(
                LLMParams(),
                selfHostedModel,
                OpenAIChatParams::class,
            )
        )

    @ParameterizedTest
    @MethodSource("openAiClientTestCases")
    fun `Should use determine Params by input params and model`(
        inputParams: LLMParams,
        model: LLModel,
        expectedClass: KClass<out OpenAIChatParams>
    ) {
        val client = OpenAILLMClient(apiKey = "dummy-key", httpClientFactory = KtorKoogHttpClient.Factory())
        val result = client.determineParams(
            params = inputParams,
            model = model,
        )

        result::class shouldBe expectedClass
    }

    /**
     * A model without an endpoint capability is what a user writes for a self-hosted server, and it
     * has to work rather than be rejected: Koog ships no predefined models for vLLM, SGLang or LM
     * Studio, and the capability naming an OpenAI API is not something such a model can be expected
     * to know about.
     */
    @Test
    fun testExecuteCallsChatCompletionsForAModelWithoutEndpointCapability() = runTest {
        val requests = mutableListOf<String>()
        val client = clientRecording(requests)

        val response = client.execute(Prompt.build("p") { user("hi") }, selfHostedModel)

        requests.single() shouldBe "/v1/chat/completions"
        response.textContent() shouldBe "hello from the server"
    }

    /**
     * An Azure deployment takes the chat-completions branch of [OpenAILLMClient.determineParams]
     * without declaring the capability. `execute` used to check the capability again afterwards and
     * refuse the model that the same client had just accepted — while `executeStreaming`, which has
     * no second check, ran it happily.
     */
    @Test
    fun testExecuteAcceptsAnAzureDeploymentThatDeclaresNoEndpointCapability() = runTest {
        val requests = mutableListOf<String>()
        val client = clientRecording(requests, baseUrl = "https://my-resource.openai.azure.com")

        val response = client.execute(
            Prompt.build("p") { user("hi") },
            LLModel(
                provider = LLMProvider.OpenAI,
                id = "azure-deployment",
                capabilities = listOf(LLMCapability.Completion, LLMCapability.Temperature),
                contextLength = 128_000,
            ),
        )

        requests.single() shouldBe "/v1/chat/completions"
        response.textContent() shouldBe "hello from the server"
    }

    /**
     * The capability is still required where the request cannot be honoured without it: params of
     * an explicit type name an API, and a model that does not speak it cannot serve them.
     */
    @Test
    fun testExplicitResponsesParamsStillRequireTheResponsesCapability() {
        val client = OpenAILLMClient(apiKey = "dummy-key", httpClientFactory = KtorKoogHttpClient.Factory())

        val failure = shouldThrow<IllegalArgumentException> {
            client.determineParams(params = OpenAIResponsesParams(), model = selfHostedModel)
        }

        failure.message.orEmpty() shouldContain "openai-endpoint-responses"
    }
}
