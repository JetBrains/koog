package ai.koog.prompt.executor.clients.openai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.openai.OpenAIToolChoice.FunctionName
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents the settings for configuring an OpenAI client.
 *
 * @property baseUrl The base URL of the OpenAI API. Defaults to "https://api.openai.com".
 * @property timeoutConfig Configuration for connection timeouts, including request, connect, and socket timeouts.
 * @property chatCompletionsPath The path of the OpenAI Chat Completions API. Defaults to "v1/chat/completions".
 * @property embeddingsPath The path of the OpenAI Embeddings API. Defaults to "v1/embeddings".
 * @property moderationsPath The path of the OpenAI Moderations API. Defaults to "v1/moderations".
 */
public class OpenAIClientSettings(
    public val baseUrl: String = "https://api.openai.com",
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    public val chatCompletionsPath: String = "v1/chat/completions",
    public val embeddingsPath: String = "v1/embeddings",
    public val moderationsPath: String = "v1/moderations",
)

/**
 * Implementation of [LLMClient] for OpenAI API.
 * Uses Ktor HttpClient to communicate with the OpenAI API.
 *
 * @param apiKey The API key for the OpenAI API
 * @param settings The base URL and timeouts for the OpenAI API, defaults to "https://api.openai.com" and 900 s
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public open class OpenAILLMClient(
    private val apiKey: String,
    private val settings: OpenAIClientSettings = OpenAIClientSettings(),
    baseClient: HttpClient = HttpClient(),
    private val clock: Clock = Clock.System,
) : LLMEmbeddingProvider, LLMClient {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
        // OpenAI API is not polymorphic, it's "dynamic". Don't add polymorphic discriminators
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
    }

    private val httpClient = baseClient.config {
        defaultRequest {
            url(settings.baseUrl)
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
        }
        install(SSE)
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = settings.timeoutConfig.requestTimeoutMillis // Increase timeout to 60 seconds
            connectTimeoutMillis = settings.timeoutConfig.connectTimeoutMillis
            socketTimeoutMillis = settings.timeoutConfig.socketTimeoutMillis
        }
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> =
        processOpenAIResponse(getOpenAIResponse(prompt, model, tools)).first()

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        val request = createOpenAIRequest(prompt, emptyList(), model, true)

        try {
            httpClient.sse(
                urlString = settings.chatCompletionsPath,
                request = {
                    method = HttpMethod.Post
                    accept(ContentType.Text.EventStream)
                    headers {
                        append(HttpHeaders.CacheControl, "no-cache")
                        append(HttpHeaders.Connection, "keep-alive")
                    }
                    setBody(request)
                }
            ) {
                incoming.collect { event ->
                    event
                        .takeIf { it.data != "[DONE]" }
                        ?.data?.trim()?.let { json.decodeFromString<OpenAIStreamResponse>(it) }
                        ?.choices?.forEach { choice -> choice.delta.content?.let { emit(it) } }
                }
            }
        } catch (e: SSEClientException) {
            e.response?.let { response ->
                val body = response.readRawBytes().decodeToString()
                logger.error(e) { "Error from OpenAI API: ${response.status}: ${e.message}.\nBody:\n$body" }
                error("Error from OpenAI API: ${response.status}: ${e.message}")
            }
        } catch (e: Exception) {
            logger.error { "Exception during streaming: $e" }
            error(e.message ?: "Unknown error during streaming")
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> =
        processOpenAIResponse(getOpenAIResponse(prompt, model, tools))

    /**
     * Embeds the given text using the OpenAI embeddings API.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of floating-point values representing the embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    override suspend fun embed(text: String, model: LLModel): List<Double> {
        require(model.capabilities.contains(LLMCapability.Embed)) {
            "Model ${model.id} does not have the Embed capability"
        }
        logger.debug { "Embedding text with model: ${model.id}" }

        val request = OpenAIEmbeddingRequest(
            model = model.id,
            input = text
        )

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post(settings.embeddingsPath) {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val openAIResponse = response.body<OpenAIEmbeddingResponse>()
                if (openAIResponse.data.isNotEmpty()) {
                    openAIResponse.data.first().embedding
                } else {
                    logger.error { "Empty data in OpenAI embedding response" }
                    error("Empty data in OpenAI embedding response")
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from OpenAI API: ${response.status}: $errorBody" }
                error("Error from OpenAI API: ${response.status}: $errorBody")
            }
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

        if (!model.capabilities.contains(LLMCapability.Moderation)) {
            throw IllegalArgumentException("Model ${model.id} does not support moderation")
        }

        require(prompt.messages.isNotEmpty()) {
            "Can't moderate an empty prompt"
        }

        val input = prompt.messages
            .map { message ->
                if (message is Message.WithAttachments) {
                    require(message.attachments.all { it is Attachment.Image }) {
                        "Only image attachments are supported for moderation"
                    }
                }

                message.toOpenAIMessageContent(model)
            }
            .let { contents ->
                /*
                 If all messages contain only text, merge it all in a single text input,
                 to support OpenAI-compatible providers that do not support attachments.

                 Otherwise create a single content instance with all the parts
                */
                if (contents.all { it is Content.Text }) {
                    val text = contents.joinToString(separator = "\n\n") { (it as Content.Text).value }

                    Content.Text(text)
                } else {
                    val parts = contents.flatMap { content ->
                        when (content) {
                            is Content.Parts -> content.value
                            is Content.Text -> listOf(ContentPart.Text(content.value))
                        }
                    }

                    Content.Parts(parts)
                }
            }

        val request = OpenAIModerationRequest(
            input = input,
            model = model.id
        )

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post(settings.moderationsPath) {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val openAIResponse = response.body<OpenAIModerationResponse>()
                if (openAIResponse.results.isNotEmpty()) {
                    val result = openAIResponse.results.first()

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
                            appliedTypes.harassmentThreatening?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
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
                            appliedTypes.selfHarmInstructions?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.SelfHarmInstructions, it) }
                            appliedTypes.illicit?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.Illicit, it) }
                            appliedTypes.illicitViolent?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.IllicitViolent, it) }
                        }
                    } ?: emptyMap()

                    ModerationResult(
                        isHarmful = result.flagged,
                        categories = categories.mapValues { (category, detected) ->
                            ModerationCategoryResult(
                                detected,
                                categoryScores[category],
                                categoryAppliedInputTypes[category] ?: emptyList()
                            )
                        }
                    )
                } else {
                    logger.error { "Empty results in OpenAI moderation response" }
                    error("Empty results in OpenAI moderation response")
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from OpenAI API: ${response.status}: $errorBody" }
                error("Error from OpenAI API: ${response.status}: $errorBody")
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createOpenAIRequest(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        model: LLModel,
        stream: Boolean
    ): OpenAIRequest {
        val messages = mutableListOf<OpenAIMessage>()
        val pendingCalls = mutableListOf<OpenAIToolCall>()

        fun flushCalls() {
            if (pendingCalls.isNotEmpty()) {
                messages += OpenAIMessage(role = "assistant", toolCalls = pendingCalls.toList())
                pendingCalls.clear()
            }
        }

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    flushCalls()
                    messages.add(
                        OpenAIMessage(
                            role = "system",
                            content = Content.Text(message.content)
                        )
                    )
                }

                is Message.User -> {
                    flushCalls()
                    messages.add(
                        OpenAIMessage(
                            role = "user",
                            content = message.toOpenAIMessageContent(model)
                        )
                    )
                }

                is Message.Assistant -> {
                    flushCalls()
                    messages.add(
                        OpenAIMessage(
                            role = "assistant",
                            content = Content.Text(message.content)
                        )
                    )
                }

                is Message.Tool.Result -> {
                    flushCalls()
                    messages.add(
                        OpenAIMessage(
                            role = "tool",
                            content = Content.Text(message.content),
                            toolCallId = message.id
                        )
                    )
                }

                is Message.Tool.Call -> pendingCalls += OpenAIToolCall(
                    id = message.id ?: Uuid.random().toString(),
                    function = OpenAIFunction(message.tool, message.content)
                )
            }
        }
        flushCalls()

        val openAITools = tools.map { tool ->
            val propertiesMap = mutableMapOf<String, JsonElement>()

            // Add required parameters
            tool.requiredParameters.forEach { param ->
                propertiesMap[param.name] = buildOpenAIParam(param)
            }

            // Add optional parameters
            tool.optionalParameters.forEach { param ->
                propertiesMap[param.name] = buildOpenAIParam(param)
            }

            val parametersObject = buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", JsonObject(propertiesMap))
                put("required", buildJsonArray {
                    tool.requiredParameters.forEach { param ->
                        add(JsonPrimitive(param.name))
                    }
                })
            }

            OpenAITool(
                function = OpenAIToolFunction(
                    name = tool.name,
                    description = tool.description,
                    parameters = parametersObject
                )
            )
        }

        val toolChoice = when (val toolChoice = prompt.params.toolChoice) {
            LLMParams.ToolChoice.Auto -> OpenAIToolChoice.Auto
            LLMParams.ToolChoice.None -> OpenAIToolChoice.None
            LLMParams.ToolChoice.Required -> OpenAIToolChoice.Required
            is LLMParams.ToolChoice.Named -> OpenAIToolChoice.Function(function = FunctionName(toolChoice.name))
            null -> null
        }

        val modalities = if (model.capabilities.contains(LLMCapability.Audio)) listOf(
            OpenAIModalities.Text,
            OpenAIModalities.Audio
        ) else null
        // TODO allow passing this externally and actually controlling this behavior
        val audio = modalities?.let {
            OpenAIAudioConfig(
                format = if (stream) OpenAIAudioFormat.PCM16 else OpenAIAudioFormat.WAV,
                voice = OpenAIAudioVoice.Alloy,
            )
        }

        return OpenAIRequest(
            model = model.id,
            messages = messages,
            temperature = if (model.capabilities.contains(LLMCapability.Temperature)) prompt.params.temperature else null,
            numberOfChoices = if (model.capabilities.contains(LLMCapability.MultipleChoices)) prompt.params.numberOfChoices else null,
            tools = if (tools.isNotEmpty()) openAITools else null,
            modalities = modalities,
            audio = audio,
            stream = stream,
            toolChoice = toolChoice,
            user = prompt.params.user,
        )
    }

    private suspend fun getOpenAIResponse(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): OpenAIResponse {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.capabilities.contains(LLMCapability.Tools) || tools.isEmpty()) {
            "Model ${model.id} does not support tools"
        }

        val request = createOpenAIRequest(prompt, tools, model, false)

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post(settings.chatCompletionsPath) {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                response.body<OpenAIResponse>()
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from OpenAI API: ${response.status}: $errorBody" }
                error("Error from OpenAI API: ${response.status}: $errorBody")
            }
        }
    }

    private fun Message.toOpenAIMessageContent(model: LLModel): Content {
        return if (this !is Message.WithAttachments || attachments.isEmpty()) {
            Content.Text(content)
        } else {
            val parts = buildList {
                if (content.isNotEmpty()) {
                    add(ContentPart.Text(content))
                }

                attachments.forEach { attachment ->
                    when (attachment) {
                        is Attachment.Image -> {
                            require(model.capabilities.contains(LLMCapability.Vision.Image)) {
                                "Model ${model.id} does not support images"
                            }

                            val imageUrl: String = when (val content = attachment.content) {
                                is AttachmentContent.URL -> content.url
                                is AttachmentContent.Binary -> "data:${attachment.mimeType};base64,${content.base64}"
                                else -> throw IllegalArgumentException("Unsupported image attachment content: ${content::class}")
                            }

                            add(ContentPart.Image(ContentPart.ImageUrl(imageUrl)))
                        }

                        is Attachment.Audio -> {
                            require(model.capabilities.contains(LLMCapability.Audio)) {
                                "Model ${model.id} does not support audio"
                            }

                            val inputAudio: ContentPart.InputAudio = when (val content = attachment.content) {
                                is AttachmentContent.Binary -> ContentPart.InputAudio(content.base64, attachment.format)
                                else -> throw IllegalArgumentException("Unsupported audio attachment content: ${content::class}")
                            }

                            add(ContentPart.Audio(inputAudio))
                        }

                        is Attachment.File -> {
                            require(model.capabilities.contains(LLMCapability.Document)) {
                                "Model ${model.id} does not support files"
                            }

                            val fileData: ContentPart.FileData = when (val content = attachment.content) {
                                is AttachmentContent.Binary -> ContentPart.FileData(
                                    fileData = "data:${attachment.mimeType};base64,${content.base64}",
                                    filename = attachment.fileName
                                )

                                else -> throw IllegalArgumentException("Unsupported file attachment content: ${content::class}")
                            }

                            add(ContentPart.File(fileData))
                        }

                        else -> throw IllegalArgumentException("Unsupported attachment type: $attachment")
                    }
                }
            }

            Content.Parts(parts)
        }
    }

    private fun buildOpenAIParam(param: ToolParameterDescriptor): JsonObject = buildJsonObject {
        put("description", JsonPrimitive(param.description))
        fillOpenAIParamType(param.type)
    }

    private fun JsonObjectBuilder.fillOpenAIParamType(type: ToolParameterType) {
        when (type) {
            ToolParameterType.Boolean -> put("type", JsonPrimitive("boolean"))
            ToolParameterType.Float -> put("type", JsonPrimitive("number"))
            ToolParameterType.Integer -> put("type", JsonPrimitive("integer"))
            ToolParameterType.String -> put("type", JsonPrimitive("string"))
            is ToolParameterType.Enum -> {
                put("type", JsonPrimitive("string"))
                put("enum", buildJsonArray {
                    type.entries.forEach { entry ->
                        add(JsonPrimitive(entry))
                    }
                })
            }

            is ToolParameterType.List -> {
                put("type", JsonPrimitive("array"))
                put("items", buildJsonObject {
                    fillOpenAIParamType(type.itemsType)
                })
            }

            is ToolParameterType.Object -> {
                put("type", JsonPrimitive("object"))
                type.additionalProperties?.let {
                    put("additionalProperties", type.additionalProperties)
                }
                put("properties", buildJsonObject {
                    type.properties.forEach { property ->
                        put(property.name, buildJsonObject {
                            fillOpenAIParamType(property.type)
                            put("description", property.description)
                        })
                    }
                })
            }
        }
    }

    private fun processOpenAIResponse(response: OpenAIResponse): List<LLMChoice> {
        if (response.choices.isEmpty()) {
            logger.error { "Empty choices in OpenAI response" }
            error("Empty choices in OpenAI response")
        }

        // Extract token count from the response
        val totalTokensCount = response.usage?.totalTokens
        val inputTokensCount = response.usage?.promptTokens
        val outputTokensCount = response.usage?.completionTokens

        val metaInfo = ResponseMetaInfo.create(
            clock,
            totalTokensCount = totalTokensCount,
            inputTokensCount = inputTokensCount,
            outputTokensCount = outputTokensCount
        )

        return response.choices.map { processOpenAIMessage(it, metaInfo) }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun processOpenAIMessage(choice: OpenAIChoice, metaInfo: ResponseMetaInfo): List<Message.Response> {
        val message = choice.message
        return when {
            message.toolCalls != null && message.toolCalls.isNotEmpty() -> {
                message.toolCalls.map { toolCall ->
                    Message.Tool.Call(
                        id = toolCall.id,
                        tool = toolCall.function.name,
                        content = toolCall.function.arguments,
                        metaInfo = metaInfo
                    )
                }
            }

            message.content != null -> {
                listOf(
                    Message.Assistant(
                        content = message.content.text(),
                        finishReason = choice.finishReason,
                        metaInfo = metaInfo
                    )
                )
            }

            message.audio != null -> {
                listOf(
                    Message.Assistant(
                        content = message.audio.transcript ?: "",
                        attachments = listOf(
                            Attachment.Audio(
                                content = AttachmentContent.Binary.Base64(message.audio.data),
                                // FIXME not a proper solution. Seems like there is no data in response about format, need to clarify
                                format = "unknown",
                            )
                        ),
                        finishReason = choice.finishReason,
                        metaInfo = metaInfo
                    )
                )
            }

            else -> {
                logger.error { "Unexpected response from OpenAI: no tool calls and no content" }
                error("Unexpected response from OpenAI: no tool calls and no content")
            }
        }
    }
}
