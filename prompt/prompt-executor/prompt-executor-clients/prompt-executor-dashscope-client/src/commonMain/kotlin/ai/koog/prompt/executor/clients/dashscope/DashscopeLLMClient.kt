package ai.koog.prompt.executor.clients.dashscope

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.dashscope.models.DashscopeChatCompletionRequest
import ai.koog.prompt.executor.clients.dashscope.models.DashscopeChatCompletionRequestSerializer
import ai.koog.prompt.executor.clients.dashscope.models.DashscopeChatCompletionResponse
import ai.koog.prompt.executor.clients.dashscope.models.DashscopeChatCompletionStreamResponse
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAIBaseSettings
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.buildStreamFrameFlow
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlin.jvm.JvmOverloads
import kotlin.time.Clock

/**
 * Configuration settings for connecting to the DashScope API using OpenAI-compatible endpoints.
 *
 * @property baseUrl The base URL of the DashScope API.
 * For international: "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
 * For China mainland: "https://dashscope.aliyuncs.com/compatible-mode/v1"
 * @property chatCompletionsPath The path for chat completions (default: "/chat/completions")
 * @property timeoutConfig Configuration for connection timeouts including request, connection, and socket timeouts.
 */
public class DashscopeClientSettings(
    baseUrl: String = "https://dashscope-intl.aliyuncs.com/",
    chatCompletionsPath: String = "compatible-mode/v1/chat/completions",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
) : OpenAIBaseSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Implementation of [AbstractOpenAILLMClient] for DashScope API using OpenAI-compatible endpoints.
 *
 * @param settings The base URL, chat completion path, and timeouts for the DashScope API,
 * defaults to "https://dashscope-intl.aliyuncs.com/compatible-mode/v1" and 900s
 * @param httpClient A fully configured [KoogHttpClient] for making API requests. Use the secondary constructor
 *   to create a Ktor-backed client configured with an API key.
 * @param clock Clock instance used for tracking response metadata timestamps
 */
public class DashscopeLLMClient @JvmOverloads constructor(
    private val settings: DashscopeClientSettings = DashscopeClientSettings(),
    httpClient: KoogHttpClient,
    clock: Clock = Clock.System,
    toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator()
) : AbstractOpenAILLMClient<DashscopeChatCompletionResponse, DashscopeChatCompletionStreamResponse>(
    settings = settings,
    httpClient = httpClient,
    clock = clock,
    logger = staticLogger,
    toolsConverter = toolsConverter
) {

    @JvmOverloads
    public constructor(
        apiKey: String,
        settings: DashscopeClientSettings = DashscopeClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator()
    ) : this(
        settings = settings,
        httpClient = createConfiguredHttpClient(apiKey, settings, staticLogger, baseClient, clientName = DASHSCOPE_CLIENT_NAME),
        clock = clock,
        toolsConverter = toolsConverter
    )

    override val clientName: String = DASHSCOPE_CLIENT_NAME

    private companion object {
        private const val DASHSCOPE_CLIENT_NAME = "DashscopeLLMClient"
        private val staticLogger = KotlinLogging.logger { }
    }

    override fun llmProvider(): LLMProvider = LLMProvider.Alibaba

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean
    ): String {
        val dashscopeParams = params.toDashscopeParams()
        val responseFormat = createResponseFormat(params.schema, model)

        val request = DashscopeChatCompletionRequest(
            messages = messages,
            model = model.id,
            maxTokens = dashscopeParams.maxTokens,
            responseFormat = responseFormat,
            stream = stream,
            temperature = dashscopeParams.temperature,
            toolChoice = dashscopeParams.toolChoice?.toOpenAIToolChoice(),
            tools = tools?.takeIf { it.isNotEmpty() },
            logprobs = dashscopeParams.logprobs,
            topLogprobs = dashscopeParams.topLogprobs,
            topP = dashscopeParams.topP,
            frequencyPenalty = dashscopeParams.frequencyPenalty,
            presencePenalty = dashscopeParams.presencePenalty,
            stop = dashscopeParams.stop,
            enableSearch = dashscopeParams.enableSearch,
            parallelToolCalls = dashscopeParams.parallelToolCalls,
            enableThinking = dashscopeParams.enableThinking,
        )

        return json.encodeToString(DashscopeChatCompletionRequestSerializer, request)
    }

    override fun processProviderChatResponse(response: DashscopeChatCompletionResponse): List<LLMChoice> {
        require(response.choices.isNotEmpty()) { "Empty choices in response" }
        return response.choices.map {
            it.message.toMessageResponses(
                it.finishReason,
                createMetaInfo(response.usage),
            )
        }
    }

    override fun decodeStreamingResponse(data: String): DashscopeChatCompletionStreamResponse =
        json.decodeFromString(data)

    override fun decodeResponse(data: String): DashscopeChatCompletionResponse =
        json.decodeFromString(data)

    override fun processStreamingResponse(
        response: Flow<DashscopeChatCompletionStreamResponse>
    ): Flow<StreamFrame> = buildStreamFrameFlow {
        var finishReason: String? = null
        var metaInfo: ResponseMetaInfo? = null

        response.collect { chunk ->
            chunk.choices.firstOrNull()?.let { choice ->
                choice.delta.content?.let { emitTextDelta(it) }

                choice.delta.toolCalls?.forEach { toolCall ->
                    val id = toolCall.id?.takeIf { it.isNotEmpty() }
                    val name = toolCall.function?.name
                    val arguments = toolCall.function?.arguments
                    val index = toolCall.index
                    emitToolCallDelta(id, name, arguments, index)
                }

                choice.finishReason?.let { finishReason = it }
            }

            chunk.usage?.let { metaInfo = createMetaInfo(chunk.usage) }
        }

        emitEnd(finishReason, metaInfo)
    }

    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by DashScope API" }
        throw UnsupportedOperationException("Moderation is not supported by DashScope API.")
    }

    /**
     * Embedding is not supported by the DashScope API.
     *
     * @throws UnsupportedOperationException Always thrown.
     */
    override suspend fun embed(
        text: String,
        model: LLModel
    ): List<Double> {
        logger.warn { "Embedding is not supported by DashScope API" }
        throw UnsupportedOperationException("Embedding is not supported by DashScope API.")
    }

    /**
     * Batch embedding is not supported by the DashScope API.
     *
     * @throws UnsupportedOperationException Always thrown.
     */
    override suspend fun embed(
        inputs: List<String>,
        model: LLModel
    ): List<List<Double>> {
        logger.warn { "Embedding is not supported by DashScope API" }
        throw UnsupportedOperationException("Embedding is not supported by DashScope API.")
    }
}
