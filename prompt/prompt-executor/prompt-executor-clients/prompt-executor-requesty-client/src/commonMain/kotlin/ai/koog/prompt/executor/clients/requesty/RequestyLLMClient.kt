package ai.koog.prompt.executor.clients.requesty

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.modelsById
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAIBaseSettings
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStaticContent
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.requesty.models.RequestyChatCompletionRequest
import ai.koog.prompt.executor.clients.requesty.models.RequestyChatCompletionRequestSerializer
import ai.koog.prompt.executor.clients.requesty.models.RequestyChatCompletionResponse
import ai.koog.prompt.executor.clients.requesty.models.RequestyChatCompletionStreamResponse
import ai.koog.prompt.executor.clients.requesty.models.RequestyModelsResponse
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.buildStreamFrameFlow
import ai.koog.utils.time.KoogClock
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlin.jvm.JvmOverloads

/**
 * Configuration settings for connecting to the Requesty API.
 *
 * Requesty also offers regional endpoints, for example "https://router.eu.requesty.ai" for EU data residency.
 * Pass one of them as [baseUrl] to route requests through that region.
 *
 * @property baseUrl The base URL of the Requesty API. The default is "https://router.requesty.ai".
 * @property chatCompletionsPath The path of the Requesty Chat Completions API. The default is "v1/chat/completions".
 * @property modelsPath The path of the Requesty Models API. The default is "v1/models".
 * @property timeoutConfig Configuration for connection timeouts including request, connection, and socket timeouts.
 */
public class RequestyClientSettings(
    baseUrl: String = "https://router.requesty.ai",
    chatCompletionsPath: String = "v1/chat/completions",
    public val modelsPath: String = "v1/models",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
) : OpenAIBaseSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Implementation of [LLMClient] for Requesty API.
 * Requesty is an LLM gateway that routes OpenAI compatible requests to multiple upstream providers.
 *
 * @param settings The base URL, chat completion path, and timeouts for the Requesty API,
 * defaults to "https://router.requesty.ai" and 900s
 * @param httpClient A fully configured [KoogHttpClient] for making API requests. Use the secondary constructor
 *   that accepts an API key and a [KoogHttpClient.Factory] to create a client with standard defaults.
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public class RequestyLLMClient @JvmOverloads constructor(
    private val settings: RequestyClientSettings = RequestyClientSettings(),
    httpClient: KoogHttpClient,
    clock: KoogClock = KoogClock.System,
    toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator()
) : AbstractOpenAILLMClient<RequestyChatCompletionResponse, RequestyChatCompletionStreamResponse>(
    settings = settings,
    httpClient = httpClient,
    clock = clock,
    logger = staticLogger,
    toolsConverter = toolsConverter
) {

    @JvmOverloads
    public constructor(
        apiKey: String,
        settings: RequestyClientSettings = RequestyClientSettings(),
        httpClientFactory: KoogHttpClient.Factory,
        clock: KoogClock = KoogClock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator()
    ) : this(
        settings = settings,
        httpClient = createConfiguredHttpClient(apiKey, settings, httpClientFactory, clientName = REQUESTY_CLIENT_NAME),
        clock = clock,
        toolsConverter = toolsConverter
    )

    override val clientName: String = REQUESTY_CLIENT_NAME

    private companion object {
        private const val REQUESTY_CLIENT_NAME = "RequestyLLMClient"
        private val staticLogger = KotlinLogging.logger { }
    }

    /**
     * Returns the specific implementation of the `LLMProvider` associated with this client.
     *
     * In this case, it identifies the `Requesty` provider as the designated LLM provider
     * for the client.
     *
     * @return The `LLMProvider` instance representing Requesty.
     */
    override fun llmProvider(): LLMProvider = LLMProvider.Requesty

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean
    ): String {
        val requestyParams = params.toRequestyParams()
        val responseFormat = createResponseFormat(params.schema, model)

        val request = RequestyChatCompletionRequest(
            messages = messages,
            model = model.id,
            frequencyPenalty = requestyParams.frequencyPenalty,
            logprobs = requestyParams.logprobs,
            maxTokens = requestyParams.maxTokens,
            presencePenalty = requestyParams.presencePenalty,
            responseFormat = responseFormat,
            stop = requestyParams.stop,
            stream = stream,
            temperature = requestyParams.temperature,
            toolChoice = requestyParams.toolChoice?.toOpenAIToolChoice(),
            tools = tools,
            topLogprobs = requestyParams.topLogprobs,
            topP = requestyParams.topP,
            prediction = requestyParams.speculation?.let { OpenAIStaticContent(Content.Text(it)) },
            user = requestyParams.user,
            additionalProperties = requestyParams.additionalProperties,
        )

        return json.encodeToString(RequestyChatCompletionRequestSerializer, request)
    }

    override fun processProviderChatResponse(response: RequestyChatCompletionResponse): List<Message.Assistant> {
        require(response.choices.isNotEmpty()) { "Empty choices in response" }
        return response.choices.map {
            it.message.toMessageResponse(
                it.finishReason,
                createMetaInfo(response.usage),
            )
        }
    }

    override fun decodeStreamingResponse(data: String): RequestyChatCompletionStreamResponse =
        json.decodeFromString(data)

    override fun decodeResponse(data: String): RequestyChatCompletionResponse =
        json.decodeFromString(data)

    override fun processStreamingResponse(
        response: Flow<RequestyChatCompletionStreamResponse>
    ): Flow<StreamFrame> = buildStreamFrameFlow {
        var finishReason: String? = null
        var metaInfo: ResponseMetaInfo? = null

        response.collect { chunk ->
            chunk.choices.firstOrNull()?.let { choice ->
                choice.delta.content?.let { emitTextDelta(it) }

                choice.delta.toolCalls?.forEach { toolCall ->
                    val id = toolCall.id
                    val name = toolCall.function?.name
                    val arguments = toolCall.function?.arguments
                    val index = toolCall.index
                    emitToolCallDelta(id, name, arguments, index)
                }

                choice.delta.reasoningContent
                    ?.takeIf { it.isNotBlank() }
                    ?.let { emitReasoningDelta(text = it, index = choice.index) }

                choice.finishReason?.let { finishReason = it }
            }

            chunk.usage?.let { metaInfo = createMetaInfo(chunk.usage) }
        }

        emitEnd(finishReason, metaInfo)
    }

    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by Requesty API" }
        throw UnsupportedOperationException("Moderation is not supported by Requesty API.")
    }

    /**
     * Fetches the list of available models from the Requesty service.
     * https://docs.requesty.ai
     *
     * Known models are returned with their predefined capabilities from [RequestyModels],
     * other models are returned as plain [LLModel] instances with only the id set.
     *
     * @return A list of models available from Requesty.
     */
    public override suspend fun models(): List<LLModel> {
        logger.debug { "Fetching available models from Requesty" }

        val models = httpClient.get(
            path = settings.modelsPath,
            responseType = RequestyModelsResponse::class
        )

        val modelsById = RequestyModels.modelsById()

        return models.data.map { modelsById[it.id] ?: LLModel(provider = llmProvider(), id = it.id) }
    }

    /**
     * Embedding is not supported by this client.
     *
     * @throws UnsupportedOperationException Always thrown.
     */
    override suspend fun embed(
        text: String,
        model: LLModel
    ): List<Double> {
        logger.warn { "Embedding is not supported by Requesty client" }
        throw UnsupportedOperationException("Embedding is not supported by Requesty client.")
    }

    /**
     * Batch embedding is not supported by this client.
     *
     * @throws UnsupportedOperationException Always thrown.
     */
    override suspend fun embed(
        inputs: List<String>,
        model: LLModel
    ): List<List<Double>> {
        logger.warn { "Embedding is not supported by Requesty client" }
        throw UnsupportedOperationException("Embedding is not supported by Requesty client.")
    }
}
