package ai.koog.prompt.executor.clients.siliconflow

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.modelsById
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAIBaseSettings
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStaticContent
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.siliconflow.models.SiliconFlowChatCompletionRequest
import ai.koog.prompt.executor.clients.siliconflow.models.SiliconFlowChatCompletionRequestSerializer
import ai.koog.prompt.executor.clients.siliconflow.models.SiliconFlowChatCompletionResponse
import ai.koog.prompt.executor.clients.siliconflow.models.SiliconFlowChatCompletionStreamResponse
import ai.koog.prompt.executor.clients.siliconflow.models.SiliconFlowEmbeddingRequest
import ai.koog.prompt.executor.clients.siliconflow.models.SiliconFlowEmbeddingResponse
import ai.koog.prompt.executor.clients.siliconflow.models.SiliconFlowModelsResponse
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.buildStreamFrameFlow
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlin.jvm.JvmOverloads
import kotlin.time.Clock

/**
 * Configuration settings for connecting to the SiliconFlow API.
 *
 * @property baseUrl The base URL of the SiliconFlow API. Default is "https://api.siliconflow.cn/".
 * @property chatCompletionsPath The path of the SiliconFlow Chat Completions API. Default is "api/v1/chat/completions".
 * @property modelsPath The path of the SiliconFlow Models API. Default is "api/v1/models".
 * @property embeddingsPath The path of the SiliconFlow Embeddings API. Default is "api/v1/embeddings".
 * @property timeoutConfig Configuration for connection timeouts including request, connection, and socket timeouts.
 */
public class SiliconFlowSettings(
    baseUrl: String = "https://api.siliconflow.cn/",
    chatCompletionsPath: String = "v1/chat/completions",
    public val modelsPath: String = "v1/models",
    public val embeddingsPath: String = "v1/embeddings",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
) : OpenAIBaseSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Implementation of [LLMClient] for SiliconFlow API.
 * SiliconFlow is an API that routes requests to multiple LLM providers.
 *
 * @param settings The base URL and timeouts for the SiliconFlow API, defaults to "https://api.siliconflow.cn" and 900s
 * @param httpClient A fully configured [KoogHttpClient] for making API requests. Use the secondary constructor
 *   to create a Ktor-backed client configured with an API key.
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public class SiliconFlowLLMClient @JvmOverloads constructor(
    private val settings: SiliconFlowSettings = SiliconFlowSettings(),
    httpClient: KoogHttpClient,
    clock: Clock = Clock.System,
    toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
) : AbstractOpenAILLMClient<SiliconFlowChatCompletionResponse, SiliconFlowChatCompletionStreamResponse>(
    settings = settings,
    httpClient = httpClient,
    clock = clock,
    logger = staticLogger,
    toolsConverter = toolsConverter
),
    LLMEmbeddingProvider {

    @JvmOverloads
    public constructor(
        apiKey: String,
        settings: SiliconFlowSettings = SiliconFlowSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
    ) : this(
        settings = settings,
        httpClient = AbstractOpenAILLMClient.createConfiguredHttpClient(apiKey, settings, staticLogger, baseClient, clientName = SILICON_FLOW_CLIENT_NAME),
        clock = clock,
        toolsConverter = toolsConverter
    )

    override val clientName: String = SILICON_FLOW_CLIENT_NAME

    private companion object Companion {
        private const val SILICON_FLOW_CLIENT_NAME = "SiliconFlowLLMClient"
        private val staticLogger = KotlinLogging.logger { }
    }

    /**
     * Returns the specific implementation of the `LLMProvider` associated with this client.
     *
     * In this case, it identifies the `SiliconFlow` provider as the designated LLM provider
     * for the client.
     *
     * @return The `LLMProvider` instance representing SiliconFlow.
     */
    override fun llmProvider(): LLMProvider = LLMProvider.SiliconFlow

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean
    ): String {
        val siliconFlowParams = params.toSiliconFlowParams()
        val responseFormat = createResponseFormat(params.schema, model)

        val request = SiliconFlowChatCompletionRequest(
            messages = messages,
            model = model.id,
            stream = stream,
            temperature = siliconFlowParams.temperature,
            tools = tools,
            toolChoice = siliconFlowParams.toolChoice?.toOpenAIToolChoice(),
            topP = siliconFlowParams.topP,
            topLogprobs = siliconFlowParams.topLogprobs,
            maxTokens = siliconFlowParams.maxTokens,
            frequencyPenalty = siliconFlowParams.frequencyPenalty,
            presencePenalty = siliconFlowParams.presencePenalty,
            responseFormat = responseFormat,
            stop = siliconFlowParams.stop,
            logprobs = siliconFlowParams.logprobs,
            topK = siliconFlowParams.topK,
            repetitionPenalty = siliconFlowParams.repetitionPenalty,
            minP = siliconFlowParams.minP,
            topA = siliconFlowParams.topA,
            prediction = siliconFlowParams.speculation?.let { OpenAIStaticContent(Content.Text(it)) },
            transforms = siliconFlowParams.transforms,
            models = siliconFlowParams.models,
            route = siliconFlowParams.route,
            provider = siliconFlowParams.provider,
            user = siliconFlowParams.user,
            additionalProperties = siliconFlowParams.additionalProperties,
        )

        return json.encodeToString(SiliconFlowChatCompletionRequestSerializer, request)
    }

    override fun processProviderChatResponse(response: SiliconFlowChatCompletionResponse): List<LLMChoice> {
        // Handle error responses
        response.error?.let { error ->
            throw LLMClientException(
                clientName = clientName,
                message = "SiliconFlow API error: ${error.message}${error.type?.let { " (type: $it)" } ?: ""}${error.code?.let { " (code: $it)" } ?: ""}",
                cause = null
            )
        }

        require(response.choices.isNotEmpty()) { "Empty choices in response" }
        return response.choices.map {
            it.message.toMessageResponses(
                it.finishReason,
                createMetaInfo(response.usage),
            )
        }
    }

    override fun decodeStreamingResponse(data: String): SiliconFlowChatCompletionStreamResponse =
        json.decodeFromString(data)

    override fun decodeResponse(data: String): SiliconFlowChatCompletionResponse =
        json.decodeFromString(data)

    override fun processStreamingResponse(
        response: Flow<SiliconFlowChatCompletionStreamResponse>
    ): Flow<StreamFrame> = buildStreamFrameFlow {
        var finishReason: String? = null
        var metaInfo: ResponseMetaInfo? = null

        response.collect { chunk ->
            chunk.choices.firstOrNull()?.let { choice ->
                choice.delta.content?.let { emitTextDelta(it) }

                choice.delta.toolCalls?.forEachIndexed { index, openAIToolCall ->
                    val id = openAIToolCall.id
                    val name = openAIToolCall.function.name
                    val arguments = openAIToolCall.function.arguments
                    emitToolCallDelta(id, name, arguments, index)
                }

                choice.finishReason?.let { finishReason = it }
            }

            chunk.usage?.let { metaInfo = createMetaInfo(chunk.usage) }
        }

        emitEnd(finishReason, metaInfo)
    }

    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by SiliconFlow API" }
        throw UnsupportedOperationException("Moderation is not supported by SiliconFlow API.")
    }

    /**
     * Fetches the list of available models from the SiliconFlow service.
     * https://docs.siliconflow.cn/cn/api-reference/models/get-model-list
     *
     * @return A list of model IDs available from SiliconFlow.
     */
    override suspend fun models(): List<LLModel> {
        logger.debug { "Fetching available models from SiliconFlow" }
        val models = httpClient.get(
            path = settings.modelsPath,
            responseType = SiliconFlowModelsResponse::class
        )

        val modelsById = SiliconFlowModels.modelsById()
        return models.data.map { modelsById[it.id] ?: LLModel(provider = llmProvider(), id = it.id) }
    }

    override suspend fun embed(text: String, model: LLModel): List<Double> {
        model.requireCapability(LLMCapability.Embed)
        logger.debug { "Embedding text (${text.length} chars) with model: ${model.id}" }

        val request = SiliconFlowEmbeddingRequest(model = model.id, input = text)

        val response = try {
            httpClient.post(
                path = settings.embeddingsPath,
                request = request,
                requestBodyType = SiliconFlowEmbeddingRequest::class,
                responseType = SiliconFlowEmbeddingResponse::class
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException(clientName, e.message, e)
        }

        response.error?.let { error ->
            throw LLMClientException(
                clientName,
                "SiliconFlow API error: ${error.message}${error.type?.let { " (type: $it)" } ?: ""}${error.code?.let { " (code: $it)" } ?: ""}"
            )
        }

        if (response.data.isEmpty()) {
            throw LLMClientException(clientName, "Empty data in SiliconFlow embedding response")
        }

        val embedding = response.data.first().embedding
        logger.debug { "Received embedding with ${embedding.size} dimensions" }
        return embedding
    }
}
