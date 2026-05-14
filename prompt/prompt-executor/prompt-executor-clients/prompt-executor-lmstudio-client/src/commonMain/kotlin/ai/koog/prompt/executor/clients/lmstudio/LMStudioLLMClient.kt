package ai.koog.prompt.executor.clients.lmstudio

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlin.jvm.JvmOverloads
import kotlin.time.Clock

/**
 * Configuration settings for connecting to an LM Studio server.
 *
 * LM Studio exposes an OpenAI-compatible REST API (https://lmstudio.ai/docs/app/api/endpoints/openai),
 * so only the base URL typically needs to change from OpenAI defaults.
 *
 * @property baseUrl The base URL of the LM Studio server. Defaults to `"http://localhost:1234"`.
 * @property timeoutConfig Configuration for connection timeouts.
 */
public class LMStudioClientSettings(
    baseUrl: String = "http://localhost:1234",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    chatCompletionsPath: String = "v1/chat/completions",
    responsesAPIPath: String = "v1/responses",
    embeddingsPath: String = "v1/embeddings",
    moderationsPath: String = "v1/moderations",
    modelsPath: String = "v1/models",
) : OpenAIClientSettings(
    baseUrl = baseUrl,
    timeoutConfig = timeoutConfig,
    chatCompletionsPath = chatCompletionsPath,
    responsesAPIPath = responsesAPIPath,
    embeddingsPath = embeddingsPath,
    moderationsPath = moderationsPath,
    modelsPath = modelsPath,
)

/**
 * Implementation of [LLMClient] for a locally running LM Studio server.
 *
 * LM Studio serves an OpenAI-compatible Chat Completions API, so this client extends
 * [OpenAILLMClient] unchanged except for the reported [LLMProvider] and the default base URL.
 *
 * LM Studio does not require authentication; the API key is forwarded in the `Authorization`
 * header but is ignored by the server. Callers may pass any non-empty string (or use the
 * [httpClient]-based constructor to skip the header entirely).
 *
 * @param settings Connection settings, defaults to `http://localhost:1234`.
 * @param httpClient A fully configured [KoogHttpClient] for making API requests.
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public class LMStudioLLMClient @JvmOverloads constructor(
    settings: LMStudioClientSettings = LMStudioClientSettings(),
    httpClient: KoogHttpClient,
    clock: Clock = Clock.System,
    toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
) : OpenAILLMClient(
    settings = settings,
    httpClient = httpClient,
    clock = clock,
    toolsConverter = toolsConverter,
) {

    @JvmOverloads
    public constructor(
        settings: LMStudioClientSettings = LMStudioClientSettings(),
        baseClient: HttpClient = HttpClient(),
        apiKey: String = "lm-studio",
        clock: Clock = Clock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
    ) : this(
        settings = settings,
        httpClient = AbstractOpenAILLMClient.createConfiguredHttpClient(
            apiKey = apiKey,
            settings = settings,
            logger = staticLogger,
            baseClient = baseClient,
            clientName = LMSTUDIO_CLIENT_NAME,
        ),
        clock = clock,
        toolsConverter = toolsConverter,
    )

    override fun llmProvider(): LLMProvider = LLMProvider.LMStudio

    private companion object {
        private const val LMSTUDIO_CLIENT_NAME = "LMStudioLLMClient"
        private val staticLogger = KotlinLogging.logger { }
    }
}

/**
 * Builds an [LLModel] for a model loaded into an LM Studio server.
 *
 * LM Studio speaks the OpenAI Chat Completions protocol, so [LLMCapability.OpenAIEndpoint.Completions]
 * is included by default; callers can override [capabilities] for models that support additional
 * features (e.g. tools, vision, JSON schema output).
 *
 * @param id The model identifier as reported by LM Studio (for example `qwen/qwen3-1.7b`).
 * @param capabilities The capabilities this local model supports.
 * @param contextLength Optional context length in tokens.
 * @param maxOutputTokens Optional cap on generated tokens.
 */
public fun lmStudioModel(
    id: String,
    capabilities: List<LLMCapability> = listOf(
        LLMCapability.Completion,
        LLMCapability.Temperature,
        LLMCapability.OpenAIEndpoint.Completions,
    ),
    contextLength: Long? = null,
    maxOutputTokens: Long? = null,
): LLModel = LLModel(
    provider = LLMProvider.LMStudio,
    id = id,
    capabilities = capabilities,
    contextLength = contextLength,
    maxOutputTokens = maxOutputTokens,
)
