package ai.koog.prompt.executor.clients.openrouter

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterToolChoice.FunctionName
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Configuration settings for connecting to the OpenRouter API.
 *
 * @property baseUrl The base URL of the OpenRouter API. Default is "https://openrouter.ai/api/v1".
 * @property timeoutConfig Configuration for connection timeouts including request, connection, and socket timeouts.
 */
public class OpenRouterClientSettings(
    public val baseUrl: String = "https://openrouter.ai",
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
)

/**
 * Implementation of [LLMClient] for OpenRouter API.
 * OpenRouter is an API that routes requests to multiple LLM providers.
 *
 * @param apiKey The API key for the OpenRouter API
 * @param settings The base URL and timeouts for the OpenRouter API, defaults to "https://openrouter.ai" and 900s
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public class OpenRouterLLMClient(
    private val apiKey: String,
    private val settings: OpenRouterClientSettings = OpenRouterClientSettings(),
    baseClient: HttpClient = HttpClient(),
    private val clock: Clock = Clock.System
) : LLMClient {

    private companion object {
        private val logger = KotlinLogging.logger { }

        private const val DEFAULT_MESSAGE_PATH = "api/v1/chat/completions"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
        // OpenRouter API is not polymorphic, it's "dynamic". Don't add polymorphic discriminators
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
    }

    private val httpClient = baseClient.config {
        defaultRequest {
            url(settings.baseUrl)
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            // OpenRouter requires HTTP_REFERER header to be set
            header("HTTP-Referer", "https://jetbrains.com")
            // Set custom user agent for OpenRouter
            header("User-Agent", "JetBrains/1.0")
        }
        install(SSE)
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = settings.timeoutConfig.requestTimeoutMillis
            connectTimeoutMillis = settings.timeoutConfig.connectTimeoutMillis
            socketTimeoutMillis = settings.timeoutConfig.socketTimeoutMillis
        }
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.capabilities.contains(LLMCapability.Tools)) {
            "Model ${model.id} does not support tools"
        }
        logger.debug { "Executing prompt: $prompt with tools: $tools" }

        val request = createOpenRouterRequest(prompt, model, tools, false)

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post(DEFAULT_MESSAGE_PATH) {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val openRouterResponse = response.body<OpenRouterResponse>()
                processOpenRouterResponse(openRouterResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from OpenRouter API: ${response.status}: $errorBody" }
                error("Error from OpenRouter API: ${response.status}: $errorBody")
            }
        }
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        logger.debug { "Executing streaming prompt: $prompt" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        val request = createOpenRouterRequest(prompt, model, emptyList(), true)

        return flow {
            try {
                httpClient.sse(
                    urlString = DEFAULT_MESSAGE_PATH,
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
                            ?.data?.trim()?.let { json.decodeFromString<OpenRouterStreamResponse>(it) }
                            ?.choices?.forEach { choice -> choice.delta.content?.let { emit(it) } }
                    }
                }
            } catch (e: SSEClientException) {
                e.response?.let { response ->
                    logger.error { "Error from OpenRouter API: ${response.status}: ${e.message}" }
                    error("Error from OpenRouter API: ${response.status}: ${e.message}")
                }
            } catch (e: Exception) {
                logger.error { "Exception during streaming: $e" }
                error(e.message ?: "Unknown error during streaming")
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createOpenRouterRequest(
        prompt: Prompt,
        model: LLModel, tools: List<ToolDescriptor>,
        stream: Boolean
    ): OpenRouterRequest {
        val messages = mutableListOf<OpenRouterMessage>()
        val pendingCalls = mutableListOf<OpenRouterToolCall>()

        fun flushCalls() {
            if (pendingCalls.isNotEmpty()) {
                messages += OpenRouterMessage(role = "assistant", toolCalls = pendingCalls.toList())
                pendingCalls.clear()
            }
        }

        prompt.messages.forEach { message ->
            if (message is Message.Tool.Call) {
                pendingCalls += OpenRouterToolCall(
                    id = message.id ?: Uuid.random().toString(),
                    function = OpenRouterFunction(message.tool, message.content)
                )
            } else {
                flushCalls()
                messages += message.toOpenRouterMessage(model) ?: return@forEach
            }
        }
        flushCalls()

        val openRouterTools = tools.map { tool ->
            val propertiesMap = mutableMapOf<String, JsonElement>()

            // Add required parameters
            tool.requiredParameters.forEach { param ->
                propertiesMap[param.name] = buildOpenRouterParam(param)
            }

            // Add optional parameters
            tool.optionalParameters.forEach { param ->
                propertiesMap[param.name] = buildOpenRouterParam(param)
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

            OpenRouterTool(
                function = OpenRouterToolFunction(
                    name = tool.name,
                    description = tool.description,
                    parameters = parametersObject
                )
            )
        }

        val toolChoice = when (val toolChoice = prompt.params.toolChoice) {
            LLMParams.ToolChoice.Auto -> OpenRouterToolChoice.Auto
            LLMParams.ToolChoice.None -> OpenRouterToolChoice.None
            LLMParams.ToolChoice.Required -> OpenRouterToolChoice.Required
            is LLMParams.ToolChoice.Named -> OpenRouterToolChoice.Function(name = FunctionName(toolChoice.name))
            null -> null
        }

        return OpenRouterRequest(
            model = model.id,
            messages = messages,
            temperature = if (model.capabilities.contains(LLMCapability.Temperature)) prompt.params.temperature else null,
            tools = if (tools.isNotEmpty()) openRouterTools else null,
            stream = stream,
            toolChoice = toolChoice,
        )
    }

    private fun Message.toOpenRouterMessage(model: LLModel): OpenRouterMessage? = when (this) {
        is Message.System -> OpenRouterMessage(role = "system", content = Content.Text(content))
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

            OpenRouterMessage(role = "user", content = Content.Parts(listOfContent))
        }

        is Message.Assistant -> OpenRouterMessage(role = "assistant", content = Content.Text(content))
        is Message.Tool.Result -> OpenRouterMessage(role = "tool", content = Content.Text(content), toolCallId = id)
        is Message.Tool.Call -> null
    }

    private fun buildOpenRouterParam(param: ToolParameterDescriptor): JsonObject = buildJsonObject {
        put("description", JsonPrimitive(param.description))
        fillOpenRouterParamType(param.type)
    }

    private fun JsonObjectBuilder.fillOpenRouterParamType(type: ToolParameterType) {
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
                    fillOpenRouterParamType(type.itemsType)
                })
            }

            is ToolParameterType.Object -> {
                put("type", JsonPrimitive("object"))
                put("properties", buildJsonObject {
                    type.properties.forEach { property ->
                        put(property.name, buildJsonObject {
                            fillOpenRouterParamType(property.type)
                            put("description", property.description)
                        })
                    }
                }
                )
            }
        }
    }

    private fun processOpenRouterResponse(response: OpenRouterResponse): List<Message.Response> {
        if (response.choices.isEmpty()) {
            logger.error { "Empty choices in OpenRouter response" }
            error("Empty choices in OpenRouter response")
        }

        val (choice, message) = response.choices
            .firstOrNull()
            ?.let { it to it.message } ?: throw IllegalStateException("No choice found in OpenRouter response")

        // Extract token count from the response
        val inputTokens = response.usage?.promptTokens
        val outputTokens = response.usage?.completionTokens
        val totalTokensCount = response.usage?.totalTokens

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
                            inputTokensCount = inputTokens,
                            outputTokensCount = outputTokens
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
                            clock,
                            totalTokensCount = totalTokensCount,
                            inputTokensCount = inputTokens,
                            outputTokensCount = outputTokens
                        )
                    )
                )
            }

            else -> {
                logger.error { "Unexpected response from OpenRouter: no tool calls and no content" }
                error("Unexpected response from OpenRouter: no tool calls and no content")
            }
        }
    }
}
