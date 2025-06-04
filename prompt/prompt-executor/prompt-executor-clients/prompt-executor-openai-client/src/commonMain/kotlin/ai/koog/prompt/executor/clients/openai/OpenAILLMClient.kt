package ai.koog.prompt.executor.clients.openai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.openai.OpenAIToolChoice.FunctionName
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.MediaContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
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
 */
public class OpenAIClientSettings(
    public val baseUrl: String = "https://api.openai.com",
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    public val chatCompletionsPath: String = "v1/chat/completions",
    public val embeddingsPath: String = "v1/embeddings",
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

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
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
                val openAIResponse = response.body<OpenAIResponse>()
                processOpenAIResponse(openAIResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from OpenAI API: ${response.status}: $errorBody" }
                error("Error from OpenAI API: ${response.status}: $errorBody")
            }
        }
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        val request = createOpenAIRequest(prompt, emptyList(), model, true)

        return flow {
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
                    logger.error { "Error from OpenAI API: ${response.status}: ${e.message}" }
                    error("Error from OpenAI API: ${response.status}: ${e.message}")
                }
            } catch (e: Exception) {
                logger.error { "Exception during streaming: $e" }
                error(e.message ?: "Unknown error during streaming")
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

        prompt.messages.forEach { message ->
            if (message is Message.Tool.Call) {
                pendingCalls += OpenAIToolCall(
                    id = message.id ?: Uuid.random().toString(),
                    function = OpenAIFunction(message.tool, message.content)
                )
            } else {
                flushCalls()
                messages += message.toOpenAIMessage(model) ?: return@forEach
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

        val modalities = if (model.capabilities.contains(LLMCapability.Audio)) listOf("text", "audio") else null

        return OpenAIRequest(
            model = model.id,
            messages = messages,
            temperature = if (model.capabilities.contains(LLMCapability.Temperature)) prompt.params.temperature else null,
            tools = if (tools.isNotEmpty()) openAITools else null,
            modalities = modalities,
            audio = modalities?.let { OpenAIAudioConfig() },
            stream = stream,
            toolChoice = toolChoice,
        )
    }

    private fun Message.toOpenAIMessage(model: LLModel): OpenAIMessage? = when (this) {
        is Message.System -> OpenAIMessage(role = "system", content = Content.Text(content))
        is Message.User -> {
            val listOfContent = buildList {
                if (content.isNotEmpty() || mediaContent.isEmpty()) {
                    add(ContentPart.Text(content))
                }

                mediaContent.forEach { media ->
                    when (media) {
                        is MediaContent.Image -> {
                            require(model.capabilities.contains(LLMCapability.Vision.Image)) {
                                "Model ${model.id} does not support image"
                            }
                            val imageUrl = if (media.isUrl()) {
                                media.source
                            } else {
                                require(media.format in listOf("png", "jpg", "jpeg", "webp", "gif")) {
                                    "Image format ${media.format} not supported"
                                }
                                "data:${media.getMimeType()};base64,${media.toBase64()}"
                            }
                            add(ContentPart.Image(ContentPart.ImageUrl(imageUrl)))
                        }

                        is MediaContent.Audio -> {
                            require(model.capabilities.contains(LLMCapability.Audio)) {
                                "Model ${model.id} does not support audio"
                            }

                            require(media.format in listOf("wav", "mp3")) {
                                "Audio format ${media.format} not supported"
                            }
                            add(ContentPart.Audio(ContentPart.InputAudio(media.toBase64(), media.format)))
                        }

                        is MediaContent.File -> {
                            require(model.capabilities.contains(LLMCapability.Vision.Image)) {
                                "Model ${model.id} does not support files"
                            }

                            require(media.format == "pdf") {
                                "File format ${media.format} not supported. Supported formats: `pdf`"
                            }
                            val fileData = "data:${media.getMimeType()};base64,${media.toBase64()}"
                            add(
                                ContentPart.File(
                                    ContentPart.FileData(
                                        fileData = fileData,
                                        filename = media.fileName()
                                    )
                                )
                            )
                        }

                        else -> {
                            logger.warn { "Media content type not supported: $mediaContent" }
                            return null
                        }
                    }
                }
            }
            OpenAIMessage(role = "user", content = Content.Parts(listOfContent))
        }

        is Message.Assistant -> OpenAIMessage(role = "assistant", content = Content.Text(content))
        is Message.Tool.Result -> OpenAIMessage(role = "tool", content = Content.Text(content), toolCallId = id)
        is Message.Tool.Call -> null
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
                put("properties", buildJsonObject {
                    type.properties.forEach { property ->
                        put(property.name, buildJsonObject {
                            fillOpenAIParamType(property.type)
                            put("description", property.description)
                        })
                    }
                }
                )
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun processOpenAIResponse(response: OpenAIResponse): List<Message.Response> {
        if (response.choices.isEmpty()) {
            logger.error { "Empty choices in OpenAI response" }
            error("Empty choices in OpenAI response")
        }

        val (choice, message) = response.choices
            .firstOrNull()
            ?.let { it to it.message } ?: throw IllegalStateException("No choice found in OpenAI response")

        // Extract token count from the response
        val totalTokensCount = response.usage?.totalTokens
        val inputTokensCount = response.usage?.inputTokens
        val outputTokensCount = response.usage?.outputTokens

        return when {
            message.toolCalls != null && message.toolCalls.isNotEmpty() -> {
                message.toolCalls.map { toolCall ->
                    Message.Tool.Call(
                        id = toolCall.id,
                        tool = toolCall.function.name,
                        content = toolCall.function.arguments,
                        metaInfo = ResponseMetaInfo.create(
                            clock,
                            totalTokensCount = totalTokensCount,
                            inputTokensCount = inputTokensCount,
                            outputTokensCount = outputTokensCount
                        )
                    )
                }
            }

            message.content != null -> {
                listOf(
                    Message.Assistant(
                        content = message.content.text(),
                        finishReason = choice.finishReason,
                        metaInfo = ResponseMetaInfo.create(
                            clock, totalTokensCount = totalTokensCount,
                            inputTokensCount = inputTokensCount,
                            outputTokensCount = outputTokensCount
                        )
                    )
                )
            }

            message.audio != null -> {
                val audio = Base64.decode(message.audio.data)
                listOf(
                    Message.Assistant(
                        content = message.audio.transcript ?: "",
                        mediaContent = MediaContent.Audio(audio, format = ""),
                        finishReason = choice.finishReason,
                        metaInfo = ResponseMetaInfo.create(
                            clock, totalTokensCount = totalTokensCount,
                            inputTokensCount = inputTokensCount,
                            outputTokensCount = outputTokensCount
                        )
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
