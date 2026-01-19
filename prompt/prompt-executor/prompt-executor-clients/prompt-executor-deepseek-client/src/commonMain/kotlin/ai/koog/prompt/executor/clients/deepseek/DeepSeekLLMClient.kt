package ai.koog.prompt.executor.clients.deepseek

import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.deepseek.models.DeepSeekChatCompletionRequest
import ai.koog.prompt.executor.clients.deepseek.models.DeepSeekChatCompletionRequestSerializer
import ai.koog.prompt.executor.clients.deepseek.models.DeepSeekChatCompletionResponse
import ai.koog.prompt.executor.clients.deepseek.models.DeepSeekChatCompletionStreamResponse
import ai.koog.prompt.executor.clients.deepseek.models.DeepSeekModelsResponse
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAIBaseSettings
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIResponseFormat
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrameFlowBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.datetime.Clock

/**
 * Configuration settings for connecting to the DeepSeek API.
 *
 * @property baseUrl The base URL of the DeepSeek API. The default is "https://api.deepseek.com".
 * @property chatCompletionsPath The path of the DeepSeek Chat Completions API. The default is "chat/completions".
 * @property modelsPath The path of the DeepSeek Models API. The default is "models".
 * @property timeoutConfig Configuration for connection timeouts including request, connection, and socket timeouts.
 */
public class DeepSeekClientSettings(
    baseUrl: String = "https://api.deepseek.com",
    chatCompletionsPath: String = "chat/completions",
    public val modelsPath: String = "models",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
) : OpenAIBaseSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Implementation of [LLMClient] for DeepSeek API.
 *
 * @param apiKey The API key for the DeepSeek API
 * @param settings The base URL, chat completion path, and timeouts for the DeepSeek API,
 * defaults to "https://api.deepseek.com" and 900s
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public class DeepSeekLLMClient(
    apiKey: String,
    private val settings: DeepSeekClientSettings = DeepSeekClientSettings(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System,
    toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator()
) : AbstractOpenAILLMClient<DeepSeekChatCompletionResponse, DeepSeekChatCompletionStreamResponse>(
    apiKey = apiKey,
    settings = settings,
    baseClient = baseClient,
    clock = clock,
    logger = staticLogger,
    toolsConverter = toolsConverter
) {

    private companion object {
        private val staticLogger = KotlinLogging.logger { }

        init {
            // On class load register custom OpenAI JSON schema generators for structured output.
            registerOpenAIJsonSchemaGenerators(LLMProvider.DeepSeek)
        }
    }

    /**
     * Returns the specific implementation of the `LLMProvider` associated with this client.
     *
     * In this case, it identifies the `DeepSeek` provider as the designated LLM provider
     * for the client.
     *
     * @return The `LLMProvider` instance representing DeepSeek.
     */
    override fun llmProvider(): LLMProvider = LLMProvider.DeepSeek

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean
    ): String {
        val deepSeekParams = params.toDeepSeekParams()
        val responseFormat = createResponseFormat(params.schema, model)

        val preparedMessages = if (params.schema != null) {
            // Add a message having the word `JSON` explicitly
            // it is required by the deepseek api for structured output
            messages + OpenAIMessage.Assistant(Content.Text("Respond with JSON"))
        } else {
            messages
        }

        val request = DeepSeekChatCompletionRequest(
            messages = preparedMessages,
            model = model.id,
            frequencyPenalty = deepSeekParams.frequencyPenalty,
            logprobs = deepSeekParams.logprobs,
            maxTokens = deepSeekParams.maxTokens,
            presencePenalty = deepSeekParams.presencePenalty,
            responseFormat = responseFormat,
            stop = deepSeekParams.stop,
            stream = stream,
            temperature = deepSeekParams.temperature,
            toolChoice = deepSeekParams.toolChoice?.toOpenAIToolChoice(),
            tools = tools,
            topLogprobs = deepSeekParams.topLogprobs,
            topP = deepSeekParams.topP,
            additionalProperties = deepSeekParams.additionalProperties,
        )

        return json.encodeToString(DeepSeekChatCompletionRequestSerializer, request)
    }

    override fun processProviderChatResponse(response: DeepSeekChatCompletionResponse): List<LLMChoice> {
        require(response.choices.isNotEmpty()) { "Empty choices in response" }
        return response.choices.map {
            it.message.toMessageResponses(
                it.finishReason,
                createMetaInfo(response.usage),
            )
        }
    }

    override fun decodeStreamingResponse(data: String): DeepSeekChatCompletionStreamResponse =
        json.decodeFromString(data)

    override fun decodeResponse(data: String): DeepSeekChatCompletionResponse =
        json.decodeFromString(data)

    override suspend fun StreamFrameFlowBuilder.processStreamingChunk(chunk: DeepSeekChatCompletionStreamResponse) {
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

    override fun createResponseFormat(schema: LLMParams.Schema?, model: LLModel): OpenAIResponseFormat? {
        return schema?.let {
            require(it.capability in model.capabilities) {
                "Model ${model.id} does not support structured output schema ${it.name}"
            }
            when (it) {
                is LLMParams.Schema.JSON -> OpenAIResponseFormat.JsonObject()
            }
        }
    }

    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by DeepSeek API" }
        throw UnsupportedOperationException("Moderation is not supported by DeepSeek API.")
    }

    /**
     * Fetches a list of available model identifiers from the DeepSeek service.
     * https://api-docs.deepseek.com/api/list-models
     *
     * @return A list of string identifiers representing the available models.
     */
    public override suspend fun models(): List<String> {
        logger.debug { "Fetching available models from DeepSeek" }

        val openAIResponse = httpClient.get(
            path = settings.modelsPath,
            responseType = DeepSeekModelsResponse::class
        )

        return openAIResponse.data.map { it.id }
    }
}
