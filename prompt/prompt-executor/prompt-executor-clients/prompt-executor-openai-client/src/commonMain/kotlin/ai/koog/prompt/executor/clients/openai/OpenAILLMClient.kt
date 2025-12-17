package ai.koog.prompt.executor.clients.openai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAIBaseSettings
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIAudioConfig
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIAudioFormat
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIAudioVoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIContentPart
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIModalities
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStaticContent
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.models.InputContent
import ai.koog.prompt.executor.clients.openai.models.Item
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionRequest
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionRequestSerializer
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionStreamResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIEmbeddingRequest
import ai.koog.prompt.executor.clients.openai.models.OpenAIEmbeddingResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIModelsResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIOutputFormat
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesAPIRequest
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesAPIRequestSerializer
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesAPIResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesTool
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesTool.Function
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesToolChoice
import ai.koog.prompt.executor.clients.openai.models.OpenAIStreamEvent
import ai.koog.prompt.executor.clients.openai.models.OpenAITextConfig
import ai.koog.prompt.executor.clients.openai.models.OutputContent
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.StreamFrameFlowBuilder
import ai.koog.utils.io.SuitableForIO
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import ai.koog.prompt.executor.clients.openai.base.models.Content as OpenAIContent

/**
 * Represents the settings for configuring an OpenAI client.
 *
 * @property baseUrl The base URL of the OpenAI API. Defaults to "https://api.openai.com".
 * @property timeoutConfig Configuration for connection timeouts, including request, connect, and socket timeouts.
 * @property chatCompletionsPath The path of the OpenAI Chat Completions API. Defaults to "v1/chat/completions".
 * @property embeddingsPath The path of the OpenAI Embeddings API. Defaults to "v1/embeddings".
 * @property moderationsPath The path of the OpenAI Moderations API. Defaults to "v1/moderations".
 * @property modelsPath The path of the OpenAI Models API. Defaults to "v1/models".
 */
public class OpenAIClientSettings(
    baseUrl: String = "https://api.openai.com",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    chatCompletionsPath: String = "v1/chat/completions",
    public val responsesAPIPath: String = "v1/responses",
    public val embeddingsPath: String = "v1/embeddings",
    public val moderationsPath: String = "v1/moderations",
    public val modelsPath: String = "v1/models",
) : OpenAIBaseSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Implementation of [LLMClient] for OpenAI API.
 * Uses Ktor HttpClient to communicate with the OpenAI API.
 *
 * @param apiKey The API key for the OpenAI API
 * @param settings The base URL and timeouts for the OpenAI API, defaults to "https://api.openai.com" and 900 s
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
@OptIn(ExperimentalAtomicApi::class)
public open class OpenAILLMClient(
    apiKey: String,
    private val settings: OpenAIClientSettings = OpenAIClientSettings(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System,
    private val toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
) : AbstractOpenAILLMClient<OpenAIChatCompletionResponse, OpenAIChatCompletionStreamResponse>(
    apiKey,
    settings,
    baseClient,
    clock,
    staticLogger,
    toolsConverter
),
    LLMEmbeddingProvider {

    /**
     * Returns the specific implementation of the `LLMProvider` associated with this client.
     *
     * In this case, it identifies the `OpenAI` provider as the designated LLM provider
     * for the client.
     *
     * @return The `LLMProvider` instance representing OpenAI.
     */
    override fun llmProvider(): LLMProvider = LLMProvider.OpenAI

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean
    ): String {
        val chatParams = params.toOpenAIChatParams()
        val modalities = if (model.supports(LLMCapability.Audio)) {
            listOf(OpenAIModalities.Text, OpenAIModalities.Audio)
        } else {
            null
        }
        val audioConfig = if (chatParams.audio == null && model.supports(LLMCapability.Audio)) {
            OpenAIAudioConfig(OpenAIAudioFormat.MP3, OpenAIAudioVoice.Alloy)
        } else {
            chatParams.audio
        }

        val responseFormat = createResponseFormat(chatParams.schema, model)

        val request = OpenAIChatCompletionRequest(
            messages = messages,
            model = model.id,
            audio = audioConfig,
            frequencyPenalty = chatParams.frequencyPenalty,
            logprobs = chatParams.logprobs,
            maxCompletionTokens = chatParams.maxTokens,
            modalities = modalities,
            numberOfChoices = model.takeIf { it.supports(LLMCapability.MultipleChoices) }
                ?.let { chatParams.numberOfChoices },
            parallelToolCalls = chatParams.parallelToolCalls,
            prediction = chatParams.speculation?.let { OpenAIStaticContent(OpenAIContent.Text(it)) },
            presencePenalty = chatParams.presencePenalty,
            promptCacheKey = chatParams.promptCacheKey,
            reasoningEffort = chatParams.reasoningEffort,
            responseFormat = responseFormat,
            safetyIdentifier = chatParams.safetyIdentifier,
            serviceTier = chatParams.serviceTier,
            stop = chatParams.stop,
            store = chatParams.store,
            stream = stream,
            temperature = chatParams.temperature,
            toolChoice = toolChoice,
            tools = tools,
            topLogprobs = chatParams.topLogprobs,
            topP = chatParams.topP,
            user = chatParams.user,
            webSearchOptions = chatParams.webSearchOptions,
            additionalProperties = chatParams.additionalProperties,
        )

        return json.encodeToString(OpenAIChatCompletionRequestSerializer, request)
    }

    private fun serializeResponsesAPIRequest(
        messages: List<Item>,
        model: LLModel,
        tools: List<OpenAIResponsesTool>?,
        toolChoice: OpenAIResponsesToolChoice?,
        params: OpenAIResponsesParams,
        stream: Boolean
    ): String {
        val responseFormat = params.schema?.let { schema ->
            require(schema.capability in model.capabilities) {
                "Model ${model.id} does not support structured output schema ${schema.name}"
            }
            when (schema) {
                is LLMParams.Schema.JSON -> OpenAITextConfig(
                    format = OpenAIOutputFormat.JsonSchema(
                        name = schema.name,
                        schema = schema.schema,
                        strict = true
                    )
                )
            }
        }

        val request = OpenAIResponsesAPIRequest(
            background = params.background,
            include = params.include,
            input = messages,
            maxOutputTokens = params.maxTokens,
            maxToolCalls = params.maxToolCalls,
            model = model.id,
            parallelToolCalls = params.parallelToolCalls,
            promptCacheKey = params.promptCacheKey,
            reasoning = params.reasoning,
            safetyIdentifier = params.safetyIdentifier,
            serviceTier = params.serviceTier,
            store = params.store,
            stream = stream,
            temperature = params.temperature,
            text = responseFormat,
            toolChoice = toolChoice,
            tools = tools,
            topLogprobs = params.topLogprobs,
            topP = params.topP,
            truncation = params.truncation,
            user = params.user,
            additionalProperties = params.additionalProperties,
        )

        return json.encodeToString(OpenAIResponsesAPIRequestSerializer, request)
    }

    private companion object {
        private val staticLogger = KotlinLogging.logger { }

        init {
            // On class load register custom OpenAI JSON schema generators for structured output.
            registerOpenAIJsonSchemaGenerators(LLMProvider.OpenAI)
        }
    }

    override fun processProviderChatResponse(response: OpenAIChatCompletionResponse): List<LLMChoice> {
        require(response.choices.isNotEmpty()) { "Empty choices in response" }
        return response.choices.map {
            it.message.toMessageResponses(
                it.finishReason,
                createMetaInfo(response.usage),
            )
        }
    }

    override fun decodeStreamingResponse(data: String): OpenAIChatCompletionStreamResponse =
        json.decodeFromString(data)

    override fun decodeResponse(data: String): OpenAIChatCompletionResponse =
        json.decodeFromString(data)

    override suspend fun StreamFrameFlowBuilder.processStreamingChunk(chunk: OpenAIChatCompletionStreamResponse) {
        chunk.choices.firstOrNull()?.let { choice ->
            choice.delta.content?.let { emitAppend(it) }
            choice.delta.toolCalls?.forEach { openAIToolCall ->
                val index = openAIToolCall.index
                val id = openAIToolCall.id
                val functionName = openAIToolCall.function?.name
                val functionArgs = openAIToolCall.function?.arguments
                upsertToolCall(index, id, functionName, functionArgs)
            }
            choice.finishReason?.let { emitEnd(it, createMetaInfo(chunk.usage)) }
        }
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        return selectExecutionStrategy(prompt, model) { params ->
            when (params) {
                is OpenAIResponsesParams -> {
                    val response = getResponseWithResponsesAPI(prompt, params, model, tools)
                    processResponsesAPIResponse(response)
                }

                is OpenAIChatParams -> super.execute(prompt, model, tools)
            }
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = selectExecutionStrategy(prompt, model) { params ->
        when (params) {
            is OpenAIResponsesParams -> executeResponsesStreaming(prompt, model, tools, params)
            is OpenAIChatParams -> super.executeStreaming(prompt, model, tools)
        }
    }

    private fun executeResponsesStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        params: OpenAIResponsesParams
    ): Flow<StreamFrame> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }

        val llmTools = tools.takeIf { it.isNotEmpty() }?.map {
            Function(
                name = it.name,
                parameters = toolsConverter.generate(it),
                description = it.description
            )
        }

        val messages = convertPromptToInput(prompt, model)
        val request = serializeResponsesAPIRequest(
            messages = messages,
            model = model,
            tools = llmTools,
            toolChoice = prompt.params.toolChoice?.toOpenAIResponseToolChoice(),
            params = params,
            stream = true
        )

        return try {
            httpClient.sse(
                path = settings.responsesAPIPath,
                request = request,
                requestBodyType = String::class,
                decodeStreamingResponse = { json.decodeFromString<OpenAIStreamEvent>(it) },
                processStreamingChunk = {
                    when (it) {
                        is OpenAIStreamEvent.ResponseOutputItemDone -> {
                            when (val item = it.item) {
                                is Item.FunctionToolCall -> StreamFrame.ToolCall(item.id, item.name, item.arguments)
                                else -> null
                            }
                        }

                        is OpenAIStreamEvent.ResponseCompleted -> {
                            StreamFrame.End(
                                finishReason = null,
                                metaInfo = it.response.usage.let { usage ->
                                    ResponseMetaInfo.create(
                                        clock = clock,
                                        totalTokensCount = usage?.totalTokens,
                                        inputTokensCount = usage?.inputTokens,
                                        outputTokensCount = usage?.outputTokens
                                    )
                                }
                            )
                        }

                        is OpenAIStreamEvent.ResponseOutputTextDelta -> {
                            StreamFrame.Append(it.delta)
                        }

                        else -> null
                    }
                }
            ).filterNotNull()
        } catch (e: Exception) {
            throw LLMClientException(
                clientName = clientName,
                message = e.message,
                cause = e
            )
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> = selectExecutionStrategy(prompt, model) { params ->
        when (params) {
            is OpenAIChatParams -> super.executeMultipleChoices(prompt, model, tools)

            is OpenAIResponsesParams -> {
                /*
                Responses API does not currently expose a native "n" parameter,
                 so we issue multiple independent responses and aggregate them.
                 This path is required for models like gpt-5.1-codex that only
                 support the Responses endpoint and return 404 on Chat Completions.
                 */
                val choices = (params.numberOfChoices ?: 1).coerceAtLeast(1)
                coroutineScope {
                    List(choices) {
                        async {
                            val response = getResponseWithResponsesAPI(prompt, params, model, tools)
                            processResponsesAPIResponse(response)
                        }
                    }.awaitAll()
                }
            }
        }
    }

    /**
     * Embeds the given text using the OpenAI embeddings API.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of floating-point values representing the embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    override suspend fun embed(text: String, model: LLModel): List<Double> {
        model.requireCapability(LLMCapability.Embed)

        logger.debug { "Embedding text with model: ${model.id}" }

        val request = OpenAIEmbeddingRequest(
            model = model.id,
            input = text
        )

        val openAIResponse = try {
            httpClient.post(
                path = settings.embeddingsPath,
                request = request,
                requestBodyType = OpenAIEmbeddingRequest::class,
                responseType = OpenAIEmbeddingResponse::class
            )
        } catch (e: Exception) {
            throw LLMClientException(
                clientName = clientName,
                message = e.message,
                cause = e
            )
        }
        if (openAIResponse.data.isEmpty()) {
            val exception = LLMClientException(clientName, "Empty data in OpenAI embedding response")
            logger.error(exception) { exception.message }
            throw exception
        }
        return openAIResponse.data.first().embedding
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
                require(message.parts.all { it is ContentPart.Text || it is ContentPart.Image }) {
                    "Only image attachments are supported for moderation"
                }

                message.toMessageContent(model)
            }
            .let { contents ->
                /*
                 If all messages contain only text, merge it all in a single text input,
                 to support OpenAI-compatible providers that do not support attachments.

                 Otherwise create a single content instance with all the parts
                 */
                if (contents.all { it is OpenAIContent.Text }) {
                    val text = contents.joinToString(separator = "\n\n") { (it as OpenAIContent.Text).value }

                    OpenAIContent.Text(text)
                } else {
                    val parts = contents.flatMap { content ->
                        when (content) {
                            is OpenAIContent.Parts -> content.value
                            is OpenAIContent.Text -> listOf(OpenAIContentPart.Text(content.value))
                        }
                    }

                    OpenAIContent.Parts(parts)
                }
            }

        val request = OpenAIModerationRequest(
            input = input,
            model = model.id
        )

        val openAIResponse = withContext(Dispatchers.SuitableForIO) {
            try {
                httpClient.post(
                    path = settings.moderationsPath,
                    request = request,
                    requestBodyType = OpenAIModerationRequest::class,
                    responseType = OpenAIModerationResponse::class
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw LLMClientException(
                    clientName = clientName,
                    message = e.message,
                    cause = e
                )
            }
        }

        if (openAIResponse.results.isEmpty()) {
            val exception = LLMClientException(clientName, "Empty results in OpenAI moderation response")
            logger.error(exception) { exception.message }
            throw exception
        }
        val result = openAIResponse.results.first()

        // Convert OpenAI categories to a map
        return convertModerationResult(result)
    }

    /**
     * Retrieves the list of available models from OpenAI.
     * https://platform.openai.com/docs/api-reference/models/list
     *
     * @return A list of model identifiers available from OpenAI.
     */
    override suspend fun models(): List<String> {
        logger.debug { "Fetching available models from OpenAI" }

        val openAIResponse = try {
            httpClient.get(
                path = settings.modelsPath,
                responseType = OpenAIModelsResponse::class
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException(
                clientName = clientName,
                message = e.message,
                cause = e
            )
        }

        return openAIResponse.data.map { it.id }
    }

    private fun convertModerationResult(result: OpenAIModerationResult): ModerationResult {
        // Convert OpenAI categories to a map
        val categories = mapOf(
            ModerationCategory.Harassment to result.categories.harassment,
            ModerationCategory.HarassmentThreatening to result.categories.harassmentThreatening,
            ModerationCategory.Hate to result.categories.hate,
            ModerationCategory.HateThreatening to result.categories.hateThreatening,
            ModerationCategory.Sexual to result.categories.sexual,
            ModerationCategory.SexualMinors to result.categories.sexualMinors,
            ModerationCategory.Violence to result.categories.violence,
            ModerationCategory.ViolenceGraphic to result.categories.violenceGraphic,
            ModerationCategory.SelfHarm to result.categories.selfHarm,
            ModerationCategory.SelfHarmIntent to result.categories.selfHarmIntent,
            ModerationCategory.SelfHarmInstructions to result.categories.selfHarmInstructions,
            ModerationCategory.Illicit to (result.categories.illicit ?: false),
            ModerationCategory.IllicitViolent to (result.categories.illicitViolent ?: false)
        )

        // Convert OpenAI category scores to a map
        val categoryScores = mapOf(
            ModerationCategory.Harassment to result.categoryScores.harassment,
            ModerationCategory.HarassmentThreatening to result.categoryScores.harassmentThreatening,
            ModerationCategory.Hate to result.categoryScores.hate,
            ModerationCategory.HateThreatening to result.categoryScores.hateThreatening,
            ModerationCategory.Sexual to result.categoryScores.sexual,
            ModerationCategory.SexualMinors to result.categoryScores.sexualMinors,
            ModerationCategory.Violence to result.categoryScores.violence,
            ModerationCategory.ViolenceGraphic to result.categoryScores.violenceGraphic,
            ModerationCategory.SelfHarm to result.categoryScores.selfHarm,
            ModerationCategory.SelfHarmIntent to result.categoryScores.selfHarmIntent,
            ModerationCategory.SelfHarmInstructions to result.categoryScores.selfHarmInstructions,
            ModerationCategory.Illicit to (result.categoryScores.illicit ?: 0.0),
            ModerationCategory.IllicitViolent to (result.categoryScores.illicitViolent ?: 0.0)
        )

        // Convert category applied input types if available
        val categoryAppliedInputTypes = result.categoryAppliedInputTypes?.let { appliedTypes ->
            buildMap {
                appliedTypes.harassment?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                    ?.let { put(ModerationCategory.Harassment, it) }
                appliedTypes.harassmentThreatening?.map {
                    ModerationResult.InputType.valueOf(it.uppercase())
                }
                    ?.let { put(ModerationCategory.HarassmentThreatening, it) }
                appliedTypes.hate?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                    ?.let { put(ModerationCategory.Hate, it) }
                appliedTypes.hateThreatening?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                    ?.let { put(ModerationCategory.HateThreatening, it) }
                appliedTypes.sexual?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                    ?.let { put(ModerationCategory.Sexual, it) }
                appliedTypes.sexualMinors?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                    ?.let { put(ModerationCategory.SexualMinors, it) }
                appliedTypes.violence?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                    ?.let { put(ModerationCategory.Violence, it) }
                appliedTypes.violenceGraphic?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                    ?.let { put(ModerationCategory.ViolenceGraphic, it) }
                appliedTypes.selfHarm?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                    ?.let { put(ModerationCategory.SelfHarm, it) }
                appliedTypes.selfHarmIntent?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                    ?.let { put(ModerationCategory.SelfHarmIntent, it) }
                appliedTypes.selfHarmInstructions?.map {
                    ModerationResult.InputType.valueOf(it.uppercase())
                }
                    ?.let { put(ModerationCategory.SelfHarmInstructions, it) }
                appliedTypes.illicit?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                    ?.let { put(ModerationCategory.Illicit, it) }
                appliedTypes.illicitViolent?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                    ?.let { put(ModerationCategory.IllicitViolent, it) }
            }
        } ?: emptyMap()

        return ModerationResult(
            isHarmful = result.flagged,
            categories = categories.mapValues { (category, detected) ->
                ModerationCategoryResult(
                    detected,
                    categoryScores[category],
                    categoryAppliedInputTypes[category] ?: emptyList()
                )
            }
        )
    }

    private suspend fun getResponseWithResponsesAPI(
        prompt: Prompt,
        params: OpenAIResponsesParams,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): OpenAIResponsesAPIResponse {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        if (tools.isNotEmpty()) {
            model.requireCapability(LLMCapability.Tools)
        }

        val llmTools = tools.takeIf { it.isNotEmpty() }?.map {
            Function(
                name = it.name,
                parameters = toolsConverter.generate(it),
                description = it.description
            )
        }

        val messages = convertPromptToInput(prompt, model)

        val request = serializeResponsesAPIRequest(
            messages,
            model,
            llmTools,
            prompt.params.toolChoice?.toOpenAIResponseToolChoice(),
            params,
            false
        )

        return httpClient.post(
            path = settings.responsesAPIPath,
            request = request,
            requestBodyType = String::class,
            responseType = OpenAIResponsesAPIResponse::class
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun convertPromptToInput(prompt: Prompt, model: LLModel): List<Item> {
        val messages = mutableListOf<Item>()
        val pendingCalls = mutableListOf<Item.FunctionToolCall>()

        fun flushPendingCalls() {
            if (pendingCalls.isNotEmpty()) {
                messages += pendingCalls
                pendingCalls.clear()
            }
        }

        with(messages) {
            prompt.messages.forEach { message ->
                when (message) {
                    is Message.System -> {
                        flushPendingCalls()
                        add(
                            Item.InputMessage(
                                role = "developer",
                                content = listOf(InputContent.Text(message.content))
                            )
                        )
                    }

                    is Message.User -> {
                        flushPendingCalls()
                        add(Item.InputMessage(role = "user", content = message.toInputMessage(model)))
                    }

                    is Message.Assistant -> {
                        flushPendingCalls()
                        add(
                            Item.OutputMessage(
                                content = listOf(
                                    OutputContent.Text(text = message.content, annotations = emptyList())
                                ),
                            )
                        )
                    }

                    is Message.Reasoning -> {
                        flushPendingCalls()
                        add(
                            Item.Reasoning(
                                id = message.id ?: Uuid.random().toString(),
                                encryptedContent = message.encrypted,
                                summary = listOf(Item.Reasoning.Summary(message.content))
                            )
                        )
                    }

                    is Message.Tool.Result -> {
                        flushPendingCalls()
                        add(
                            Item.FunctionToolCallOutput(
                                callId = message.id ?: Uuid.random().toString(),
                                output = message.content
                            )
                        )
                    }

                    is Message.Tool.Call -> {
                        pendingCalls += Item.FunctionToolCall(
                            callId = message.id ?: Uuid.random().toString(),
                            name = message.tool,
                            arguments = message.content
                        )
                    }
                }
            }
        }
        flushPendingCalls()

        return messages
    }

    private fun Message.toInputMessage(model: LLModel): List<InputContent> {
        return buildList {
            parts.forEach { part ->
                when (part) {
                    is ContentPart.Text -> {
                        add(InputContent.Text(part.text))
                    }

                    is ContentPart.Image -> {
                        model.requireCapability(LLMCapability.Vision.Image)

                        val imageUrl: String = when (val content = part.content) {
                            is AttachmentContent.URL -> content.url
                            is AttachmentContent.Binary -> "data:${part.mimeType};base64,${content.asBase64()}"
                            else -> throw IllegalArgumentException("Unsupported image attachment content: ${content::class}")
                        }

                        add(InputContent.Image(imageUrl = imageUrl))
                    }

                    is ContentPart.File -> {
                        model.requireCapability(LLMCapability.Document)

                        val fileData = when (val content = part.content) {
                            is AttachmentContent.Binary -> "data:${part.mimeType};base64,${content.asBase64()}"
                            else -> null
                        }

                        val fileUrl = when (val content = part.content) {
                            is AttachmentContent.URL -> content.url
                            else -> null
                        }

                        add(InputContent.File(fileData = fileData, fileUrl = fileUrl, filename = part.fileName))
                    }

                    else -> throw LLMClientException(clientName, "Unsupported attachment type: $part, for model: $model with Responses API")
                }
            }
        }
    }

    private fun processResponsesAPIResponse(response: OpenAIResponsesAPIResponse): List<Message.Response> {
        require(response.output.isNotEmpty()) { "Empty output in response" }

        val metaInfo = ResponseMetaInfo.create(
            clock,
            totalTokensCount = response.usage?.totalTokens,
            inputTokensCount = response.usage?.inputTokens,
            outputTokensCount = response.usage?.outputTokens
        )

        return response.output
            .map { output ->
                when (output) {
                    is Item.FunctionToolCall -> Message.Tool.Call(
                        id = output.callId,
                        tool = output.name,
                        content = output.arguments,
                        metaInfo = metaInfo
                    )

                    is Item.OutputMessage -> {
                        val text = output.text().ifBlank { response.outputText.orEmpty() }
                        Message.Assistant(
                            content = text,
                            finishReason = output.status?.name,
                            metaInfo = metaInfo
                        )
                    }

                    is Item.Reasoning -> Message.Reasoning(
                        id = output.id,
                        encrypted = output.encryptedContent,
                        content = output.summary.joinToString(separator = "\n") { it.text },
                        metaInfo = metaInfo
                    )

                    else -> throw LLMClientException(clientName, "Unexpected response from $clientName: no tool calls and no content")
                }
            }
    }

    private fun LLMParams.ToolChoice.toOpenAIResponseToolChoice() = when (this) {
        LLMParams.ToolChoice.Auto -> OpenAIResponsesToolChoice.Mode("auto")
        LLMParams.ToolChoice.None -> OpenAIResponsesToolChoice.Mode("none")
        LLMParams.ToolChoice.Required -> OpenAIResponsesToolChoice.Mode("required")
        is LLMParams.ToolChoice.Named -> OpenAIResponsesToolChoice.FunctionTool(name = name)
    }

    internal fun determineParams(params: LLMParams, model: LLModel): OpenAIParams = when {
        "openai.azure.com" in settings.baseUrl -> params.toOpenAIChatParams() // TODO: create a separate Azure Client
        params is OpenAIResponsesParams -> {
            model.requireCapability(
                LLMCapability.OpenAIEndpoint.Responses,
                message = "Must be supported to use OpenAI responses params."
            )
            params
        }

        params is OpenAIChatParams -> {
            model.requireCapability(
                LLMCapability.OpenAIEndpoint.Completions,
                message = "Must be supported to use OpenAI chat params."
            )
            params
        }

        model.supports(LLMCapability.OpenAIEndpoint.Completions) -> params.toOpenAIChatParams()
        model.supports(LLMCapability.OpenAIEndpoint.Responses) -> params.toOpenAIResponsesParams()
        else -> throw LLMClientException(clientName, "Cannot determine proper LLM params for OpenAI model: ${model.id}")
    }

    private inline fun <T> selectExecutionStrategy(
        prompt: Prompt,
        model: LLModel,
        action: (OpenAIParams) -> T
    ): T = action(determineParams(prompt.params, model))
}
