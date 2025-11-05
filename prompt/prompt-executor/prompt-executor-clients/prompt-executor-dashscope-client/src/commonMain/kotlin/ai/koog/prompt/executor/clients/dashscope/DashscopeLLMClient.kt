package ai.koog.prompt.executor.clients.dashscope

import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.dashscope.models.DashscopeChatCompletionRequest
import ai.koog.prompt.executor.clients.dashscope.models.DashscopeChatCompletionRequestSerializer
import ai.koog.prompt.executor.clients.dashscope.models.DashscopeChatCompletionResponse
import ai.koog.prompt.executor.clients.dashscope.models.DashscopeChatCompletionStreamResponse
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAIBaseSettings
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrameFlowBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.datetime.Clock

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
 * @param apiKey The API key for the DashScope API
 * @param settings The base URL, chat completion path, and timeouts for the DashScope API,
 * defaults to "https://dashscope-intl.aliyuncs.com/compatible-mode/v1" and 900s
 * @param baseClient HTTP client for making requests
 * @param clock Clock instance used for tracking response metadata timestamps
 */
public class DashscopeLLMClient(
    apiKey: String,
    private val settings: DashscopeClientSettings = DashscopeClientSettings(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System
) : AbstractOpenAILLMClient<DashscopeChatCompletionResponse, DashscopeChatCompletionStreamResponse>(
    apiKey,
    settings,
    baseClient,
    clock,
    staticLogger
) {

    private companion object {
        private val staticLogger = KotlinLogging.logger { }

        init {
            // On class load register custom OpenAI JSON schema generators for structured output.
            registerOpenAIJsonSchemaGenerators(LLMProvider.Alibaba)
        }
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

    override suspend fun StreamFrameFlowBuilder.processStreamingChunk(chunk: DashscopeChatCompletionStreamResponse) {
        chunk.choices.firstOrNull()?.let { choice ->
            choice.delta.content?.let { emitAppend(it) }
            choice.delta.toolCalls?.forEach { toolCall ->
                val index = toolCall.index
                val id = toolCall.id
                val name = toolCall.function?.name
                val arguments = toolCall.function?.arguments
                upsertToolCall(index, id, name, arguments)
            }
            choice.finishReason?.let { emitEnd(it, createMetaInfo(chunk.usage)) }
        }
    }

    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by DashScope API" }
        throw UnsupportedOperationException("Moderation is not supported by DashScope API.")
    }
}
