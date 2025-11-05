package ai.koog.prompt.executor.clients.mistralai

import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.mistralai.models.MistralAIChatCompletionRequest
import ai.koog.prompt.executor.clients.mistralai.models.MistralAIChatCompletionRequestSerializer
import ai.koog.prompt.executor.clients.mistralai.models.MistralAIChatCompletionResponse
import ai.koog.prompt.executor.clients.mistralai.models.MistralAIChatCompletionStreamResponse
import ai.koog.prompt.executor.clients.mistralai.models.MistralAIEmbeddingRequest
import ai.koog.prompt.executor.clients.mistralai.models.MistralAIEmbeddingResponse
import ai.koog.prompt.executor.clients.mistralai.models.MistralAIModerationRequest
import ai.koog.prompt.executor.clients.mistralai.models.MistralAIModerationResponse
import ai.koog.prompt.executor.clients.mistralai.models.MistralAIModerationResult
import ai.koog.prompt.executor.clients.mistralai.models.MistralModelsResponse
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAIBaseSettings
import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIContentPart
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStaticContent
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrameFlowBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.datetime.Clock

/**
 * Represents the settings for configuring a Mistral AI client.
 *
 * @property baseUrl The base URL of the Mistral AI API. Defaults to "https://api.mistral.ai".
 * @property chatCompletionsPath The path of the Mistral AI Chat Completions API. Defaults to "v1/chat/completions".
 * @property timeoutConfig Configuration for connection timeouts, including request, connect, and socket timeouts.
 * @property modelsPath The path of the Mistral AI Models API. Defaults to "v1/models".
 */
public class MistralAIClientSettings(
    baseUrl: String = "https://api.mistral.ai",
    chatCompletionsPath: String = "v1/chat/completions",
    public val embeddingsPath: String = "v1/embeddings",
    public val moderationPath: String = "v1/moderations",
    public val modelsPath: String = "v1/models",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
) : OpenAIBaseSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Implementation of [LLMClient] for Mistral AI.
 *
 * @param apiKey The API key for the Mistral AI API
 * @param settings The base URL, chat completion path, and timeouts for the Mistral AI
 * @param clock Clock instance used for tracking response metadata timestamps
 */
public open class MistralAILLMClient(
    apiKey: String,
    private val settings: MistralAIClientSettings = MistralAIClientSettings(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System
) : AbstractOpenAILLMClient<MistralAIChatCompletionResponse, MistralAIChatCompletionStreamResponse>(
    apiKey = apiKey,
    settings = settings,
    baseClient = baseClient,
    clock = clock,
    logger = staticLogger
),
    LLMEmbeddingProvider {

    private companion object {
        private val staticLogger = KotlinLogging.logger { }

        init {
            // On class load register custom OpenAI JSON schema generators for structured output.
            registerOpenAIJsonSchemaGenerators(LLMProvider.DeepSeek)
        }
    }

    /**
     * Returns the specific implementation of the `LLMProvider` associated with this client
     *
     * In this case, it identifies the `MistralAI` provider as the designated LLM provider for the client.
     *
     * @return the `LLMProvider` instance representing MistralAI
     */
    override fun llmProvider(): LLMProvider = LLMProvider.MistralAI

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean
    ): String {
        val mistralAIParams = params.toMistralAIParams()
        val responseFormat = createResponseFormat(params.schema, model)

        val request = MistralAIChatCompletionRequest(
            model = model.id,
            temperature = mistralAIParams.temperature,
            topP = mistralAIParams.topP,
            maxTokens = mistralAIParams.maxTokens,
            stream = stream,
            stop = mistralAIParams.stop,
            randomSeed = mistralAIParams.randomSeed,
            messages = messages,
            responseFormat = responseFormat,
            tools = tools,
            toolChoice = mistralAIParams.toolChoice?.toOpenAIToolChoice(),
            presencePenalty = mistralAIParams.presencePenalty,
            frequencyPenalty = mistralAIParams.frequencyPenalty,
            numberOfChoices = mistralAIParams.numberOfChoices,
            prediction = mistralAIParams.speculation?.let { OpenAIStaticContent(Content.Text(it)) },
            parallelToolCalls = mistralAIParams.parallelToolCalls,
            promptMode = mistralAIParams.promptMode,
            safePrompt = mistralAIParams.safePrompt,
        )

        return json.encodeToString(MistralAIChatCompletionRequestSerializer, request)
    }

    override fun processProviderChatResponse(response: MistralAIChatCompletionResponse): List<LLMChoice> {
        require(response.choices.isNotEmpty()) { "Empty choices in response" }
        val usageInfo = OpenAIUsage(
            promptTokens = response.usage.promptTokens,
            completionTokens = response.usage.completionTokens,
            totalTokens = response.usage.totalTokens,
        )
        return response.choices.map {
            it.message.toMessageResponses(
                it.finishReason,
                createMetaInfo(usageInfo),
            )
        }
    }

    override fun decodeStreamingResponse(data: String): MistralAIChatCompletionStreamResponse =
        json.decodeFromString(data)

    override fun decodeResponse(data: String): MistralAIChatCompletionResponse =
        json.decodeFromString(data)

    override suspend fun StreamFrameFlowBuilder.processStreamingChunk(chunk: MistralAIChatCompletionStreamResponse) {
        chunk.choices.firstOrNull()?.let { choice ->
            choice.delta.content?.let { emitAppend(it) }
            choice.delta.toolCalls?.forEach { toolCall ->
                val index = toolCall.index
                val id = toolCall.id
                val name = toolCall.function?.name
                val arguments = toolCall.function?.arguments
                upsertToolCall(index, id, name, arguments)
            }
            val usageInfo = OpenAIUsage(
                promptTokens = chunk.usage?.promptTokens,
                completionTokens = chunk.usage?.completionTokens,
                totalTokens = chunk.usage?.totalTokens,
            )
            choice.finishReason?.let { emitEnd(it, createMetaInfo(usageInfo)) }
        }
    }

    /**
     * Embeds the given text using the MistralAI embeddings API.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of floating-point values representing the embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    override suspend fun embed(text: String, model: LLModel): List<Double> {
        model.requireCapability(LLMCapability.Embed)

        logger.debug { "Embedding text with model: ${model.id}" }

        val request = MistralAIEmbeddingRequest(model = model.id, input = text)

        val mistralAIResponse = httpClient.post(
            path = settings.embeddingsPath,
            request = request,
            requestBodyType = MistralAIEmbeddingRequest::class,
            responseType = MistralAIEmbeddingResponse::class
        )

        return mistralAIResponse.data.firstOrNull()?.embedding ?: run {
            logger.error { "Empty data in MistralAI embedding response" }
            error("Empty data in MistralAI embedding response")
        }
    }

    /**
     * Moderates text and image content based on the provided model's capabilities.
     *
     * @param prompt The prompt containing text messages and optional attachments to be moderated.
     * @param model The language model to use for moderation. Must have the `Moderation` capability.
     * @return The moderation result, including flagged content, categories, scores, and associated metadata.
     * @throws IllegalArgumentException If the specified model does not support moderation.
     */
    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.debug { "Moderating text and image content with model: $model" }

        model.requireCapability(LLMCapability.Moderation)
        require(prompt.messages.isNotEmpty()) { "Can't moderate an empty prompt" }

        val input = prompt.messages
            .map { message ->
                require(!message.hasAttachments()) {
                    "Only text input is supported for MistralAI moderation"
                }
                message.toMessageContent(model)
            }
            .let { contents ->
                when {
                    contents.all { it is Content.Text } -> {
                        val text = contents.joinToString(separator = "\n\n") { (it as Content.Text).value }
                        Content.Text(text)
                    }

                    else -> {
                        val parts = contents.flatMap { content ->
                            when (content) {
                                is Content.Parts -> content.value
                                is Content.Text -> listOf(OpenAIContentPart.Text(content.value))
                            }
                        }
                        Content.Parts(parts)
                    }
                }
            }

        val request = MistralAIModerationRequest(model = model.id, input = input)

        val response = httpClient.post(
            path = settings.moderationPath,
            request = request,
            requestBodyType = MistralAIModerationRequest::class,
            responseType = MistralAIModerationResponse::class
        )

        val result = response.results.firstOrNull() ?: run {
            logger.error { "Empty results in MistralAI moderation response" }
            error("Empty results in MistralAI moderation response")
        }

        return result.toModerationResult()
    }

    /**
     * Fetches the list of available model IDs from the MistralAI service.
     * https://docs.mistral.ai/api/endpoint/models
     *
     * @return A list of model IDs as strings.
     * @throws Exception if the HTTP request fails or the response cannot be processed.
     */
    override suspend fun models(): List<String> {
        val response = httpClient.get(
            path = settings.modelsPath,
            responseType = MistralModelsResponse::class
        )
        return response.data.map { it.id }
    }

    private fun MistralAIModerationResult.toModerationResult(): ModerationResult {
        val categoryMapping = buildCategoryMapping()

        return ModerationResult(
            isHarmful = categoryMapping.any { it.value.detected },
            categories = categoryMapping
        )
    }

    private fun MistralAIModerationResult.buildCategoryMapping(): Map<ModerationCategory, ModerationCategoryResult> =
        listOf(
            ModerationCategory.Sexual to (categories.sexual to categoryScores.sexual),
            ModerationCategory.Hate to (categories.hateAndDiscrimination to categoryScores.hateAndDiscrimination),
            ModerationCategory.Violence to (categories.violenceAndThreats to categoryScores.violenceAndThreats),
            ModerationCategory.Illicit to (categories.dangerousAndCriminalContent to categoryScores.dangerousAndCriminalContent),
            ModerationCategory.SelfHarm to (categories.selfharm to categoryScores.selfharm),
            ModerationCategory.SpecializedAdvice to (categories.health to categoryScores.health),
            ModerationCategory.SpecializedAdvice to (categories.financial to categoryScores.financial),
            ModerationCategory.SpecializedAdvice to (categories.law to categoryScores.law),
            ModerationCategory.Privacy to (categories.pii to categoryScores.pii)
        ).groupBy({ it.first }, { it.second })
            .mapValues { (_, values) ->
                ModerationCategoryResult(
                    detected = values.any { it.first },
                    confidenceScore = values.maxOfOrNull { it.second }
                )
            }
}
