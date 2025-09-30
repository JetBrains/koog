package ai.koog.prompt.executor.clients.openrouter

import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAIBasedSettings
import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStaticContent
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openrouter.models.OpenRouterChatCompletionRequest
import ai.koog.prompt.executor.clients.openrouter.models.OpenRouterChatCompletionRequestSerializer
import ai.koog.prompt.executor.clients.openrouter.models.OpenRouterChatCompletionResponse
import ai.koog.prompt.executor.clients.openrouter.models.OpenRouterChatCompletionStreamResponse
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrameFlowBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.datetime.Clock

/**
 * Configuration settings for connecting to the OpenRouter API.
 *
 * @property baseUrl The base URL of the OpenRouter API. Default is "https://openrouter.ai/api/v1".
 * @property timeoutConfig Configuration for connection timeouts including request, connection, and socket timeouts.
 */
public class OpenRouterClientSettings(
    baseUrl: String = "https://openrouter.ai",
    chatCompletionsPath: String = "api/v1/chat/completions",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
) : OpenAIBasedSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Implementation of [LLMClient] for OpenRouter API.
 * OpenRouter is an API that routes requests to multiple LLM providers.
 *
 * @param apiKey The API key for the OpenRouter API
 * @param settings The base URL and timeouts for the OpenRouter API, defaults to "https://openrouter.ai" and 900s
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public class OpenRouterLLMClient(
    apiKey: String,
    private val settings: OpenRouterClientSettings = OpenRouterClientSettings(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System
) : AbstractOpenAILLMClient<OpenRouterChatCompletionResponse, OpenRouterChatCompletionStreamResponse>(
    apiKey,
    settings,
    baseClient,
    clock,
    staticLogger
) {

    private companion object {
        private val staticLogger = KotlinLogging.logger { }
    }

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean
    ): String {
        val openRouterParams = params.toOpenRouterParams()
        val responseFormat = createResponseFormat(params.schema, model)

        val request = OpenRouterChatCompletionRequest(
            messages = messages,
            model = model.id,
            stream = stream,
            temperature = openRouterParams.temperature,
            tools = tools,
            toolChoice = openRouterParams.toolChoice?.toOpenAIToolChoice(),
            topP = openRouterParams.topP,
            topLogprobs = openRouterParams.topLogprobs,
            maxTokens = openRouterParams.maxTokens,
            frequencyPenalty = openRouterParams.frequencyPenalty,
            presencePenalty = openRouterParams.presencePenalty,
            responseFormat = responseFormat,
            stop = openRouterParams.stop,
            logprobs = openRouterParams.logprobs,
            topK = openRouterParams.topK,
            repetitionPenalty = openRouterParams.repetitionPenalty,
            minP = openRouterParams.minP,
            topA = openRouterParams.topA,
            prediction = openRouterParams.speculation?.let { OpenAIStaticContent(Content.Text(it)) },
            transforms = openRouterParams.transforms,
            models = openRouterParams.models,
            route = openRouterParams.route,
            provider = openRouterParams.provider,
            user = openRouterParams.user,
            additionalProperties = openRouterParams.additionalProperties,
        )

        return json.encodeToString(OpenRouterChatCompletionRequestSerializer, request)
    }

    override fun processProviderChatResponse(response: OpenRouterChatCompletionResponse): List<LLMChoice> {
        require(response.choices.isNotEmpty()) { "Empty choices in response" }
        return response.choices.map { it.toMessageResponses(createMetaInfo(response.usage)) }
    }

    override fun decodeStreamingResponse(data: String): OpenRouterChatCompletionStreamResponse =
        json.decodeFromString(data)

    override fun decodeResponse(data: String): OpenRouterChatCompletionResponse =
        json.decodeFromString(data)

    override suspend fun StreamFrameFlowBuilder.processStreamingChunk(chunk: OpenRouterChatCompletionStreamResponse) {
        chunk.choices.firstOrNull()?.let { choice ->
            choice.delta.content?.let { emitAppend(it) }
            choice.delta.toolCalls?.forEach { openAIToolCall ->
                val index = openAIToolCall.index
                val id = openAIToolCall.id
                val name = openAIToolCall.function?.name
                val arguments = openAIToolCall.function?.arguments
                upsertToolCall(index, id, name, arguments)
            }
            choice.finishReason?.let { emitEnd(it, createMetaInfo(chunk.usage)) }
        }
    }

    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by OpenRouter API" }
        throw UnsupportedOperationException("Moderation is not supported by OpenRouter API.")
    }
}
