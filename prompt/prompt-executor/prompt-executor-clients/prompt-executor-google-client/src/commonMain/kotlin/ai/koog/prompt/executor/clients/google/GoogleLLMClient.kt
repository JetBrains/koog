package ai.koog.prompt.executor.clients.google

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
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
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Configuration settings for the Google AI client.
 *
 * @property baseUrl The base URL for the Google AI API.
 * @property timeoutConfig Timeout configuration for API requests.
 */
public class GoogleClientSettings(
    public val baseUrl: String = "https://generativelanguage.googleapis.com",
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
)

/**
 * Implementation of [LLMClient] for Google's Gemini API.
 *
 * This client supports both standard and streaming text generation with
 * optional tool calling capabilities.
 *
 * @param apiKey The API key for the Google AI API
 * @param settings Custom client settings, defaults to standard API endpoint and timeouts
 * @param baseClient Optional custom HTTP client
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public open class GoogleLLMClient(
    private val apiKey: String,
    private val settings: GoogleClientSettings = GoogleClientSettings(),
    baseClient: HttpClient = HttpClient(),
    private val clock: Clock = Clock.System
) : LLMClient {

    private companion object {
        private val logger = KotlinLogging.logger { }

        private const val DEFAULT_PATH = "v1beta/models"
        private const val DEFAULT_METHOD_GENERATE_CONTENT = "generateContent"
        private const val DEFAULT_METHOD_STREAM_GENERATE_CONTENT = "streamGenerateContent"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val httpClient = baseClient.config {
        defaultRequest {
            url(settings.baseUrl)
            url.parameters.append("key", apiKey)
            contentType(ContentType.Application.Json)
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
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.capabilities.contains(LLMCapability.Tools) || tools.isEmpty()) {
            "Model ${model.id} does not support tools"
        }

        val request = createGoogleRequest(prompt, model, tools)

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post("$DEFAULT_PATH/${model.id}:$DEFAULT_METHOD_GENERATE_CONTENT") {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val googleResponse = response.body<GoogleResponse>()
                processGoogleResponse(googleResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from GoogleAI API: ${response.status}: $errorBody" }
                error("Error from GoogleAI API: ${response.status}: $errorBody")
            }
        }
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        val request = createGoogleRequest(prompt, model, emptyList())

        return flow {
            try {
                httpClient.sse(
                    urlString = "$DEFAULT_PATH/${model.id}:$DEFAULT_METHOD_STREAM_GENERATE_CONTENT",
                    request = {
                        method = HttpMethod.Post
                        parameter("alt", "sse")
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
                            ?.data?.trim()?.let { json.decodeFromString<GoogleResponse>(it) }
                            ?.candidates?.firstOrNull()?.content
                            ?.parts?.forEach { part -> if (part is GooglePart.Text) emit(part.text) }
                    }
                }
            } catch (e: SSEClientException) {
                e.response?.let { response ->
                    logger.error { "Error from GoogleAI API: ${response.status}: ${e.message}" }
                    error("Error from GoogleAI API: ${response.status}: ${e.message}")
                }
            } catch (e: Exception) {
                logger.error { "Exception during streaming: $e" }
                error(e.message ?: "Unknown error during streaming")
            }
        }
    }

    /**
     * Creates a GoogleAI API request from a prompt.
     *
     * @param prompt The prompt to convert
     * @param model The model to use
     * @param tools Tools to include in the request
     * @return A formatted GoogleAI request
     */
    private fun createGoogleRequest(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): GoogleRequest {
        val systemMessageParts = mutableListOf<GooglePart.Text>()
        val contents = mutableListOf<GoogleContent>()
        val pendingCalls = mutableListOf<GooglePart.FunctionCall>()

        fun flushCalls() {
            if (pendingCalls.isNotEmpty()) {
                contents += GoogleContent(role = "model", parts = pendingCalls.toList())
                pendingCalls.clear()
            }
        }

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    systemMessageParts.add(GooglePart.Text(message.content))
                }

                is Message.User -> {
                    flushCalls()
                    // User messages become 'user' role content
                    contents.add(message.toGoogleContent(model))
                }

                is Message.Assistant -> {
                    flushCalls()
                    contents.add(
                        GoogleContent(
                            role = "model",
                            parts = listOf(GooglePart.Text(message.content))
                        )
                    )
                }

                is Message.Tool.Result -> {
                    flushCalls()
                    contents.add(
                        GoogleContent(
                            role = "user",
                            parts = listOf(
                                GooglePart.FunctionResponse(
                                    functionResponse = GoogleData.FunctionResponse(
                                        id = message.id,
                                        name = message.tool,
                                        response = buildJsonObject { put("result", message.content) }
                                    )
                                )
                            )
                        )
                    )
                }

                is Message.Tool.Call -> {
                    pendingCalls += GooglePart.FunctionCall(
                        functionCall = GoogleData.FunctionCall(
                            id = message.id,
                            name = message.tool,
                            args = json.decodeFromString(message.content)
                        )
                    )
                }
            }
        }
        flushCalls()

        val googleTools = tools
            .map { tool ->
                val properties = (tool.requiredParameters + tool.optionalParameters)
                    .associate { it.name to buildGoogleParamType(it) }
                GoogleFunctionDeclaration(
                    name = tool.name,
                    description = tool.description,
                    parameters = buildJsonObject {
                        put("type", "object")
                        put("properties", JsonObject(properties))
                        putJsonArray("required") {
                            addAll(tool.requiredParameters.map { JsonPrimitive(it.name) })
                        }
                    }
                )
            }
            .takeIf { it.isNotEmpty() }
            ?.let { declarations -> listOf(GoogleTool(functionDeclarations = declarations)) }

        val googleSystemInstruction = systemMessageParts
            .takeIf { it.isNotEmpty() }
            ?.let { GoogleContent(parts = it) }

        val generationConfig = GoogleGenerationConfig(
            temperature = if (model.capabilities.contains(LLMCapability.Temperature)) prompt.params.temperature else null,
            maxOutputTokens = 2048,
        )


        val functionCallingConfig = when (val toolChoice = prompt.params.toolChoice) {
            LLMParams.ToolChoice.Auto -> GoogleFunctionCallingConfig(GoogleFunctionCallingMode.AUTO)
            LLMParams.ToolChoice.None -> GoogleFunctionCallingConfig(GoogleFunctionCallingMode.NONE)
            LLMParams.ToolChoice.Required -> GoogleFunctionCallingConfig(GoogleFunctionCallingMode.ANY)
            is LLMParams.ToolChoice.Named -> {
                GoogleFunctionCallingConfig(
                    GoogleFunctionCallingMode.ANY,
                    allowedFunctionNames = listOf(toolChoice.name)
                )
            }

            null -> null
        }

        return GoogleRequest(
            contents = contents,
            systemInstruction = googleSystemInstruction,
            tools = googleTools,
            generationConfig = generationConfig,
            toolConfig = GoogleToolConfig(functionCallingConfig),
        )
    }

    private fun Message.User.toGoogleContent(model: LLModel): GoogleContent {
        val contentParts = buildList {
            if (content.isNotEmpty() || mediaContent.isEmpty()) {
                add(GooglePart.Text(content))
            }
            mediaContent.forEach { media ->
                when (media) {
                    is MediaContent.Image -> {
                        require(model.capabilities.contains(LLMCapability.Vision.Image)) {
                            "Model ${model.id} does not support image"
                        }
                        if (media.isUrl()) {
                            throw IllegalArgumentException("URL images not supported for Gemini models")
                        }
                        require(media.format in listOf("png", "jpg", "jpeg", "webp", "heic", "heif")) {
                            "Image format ${media.format} not supported"
                        }
                        add(
                            GooglePart.InlineData(
                                GoogleData.Blob(
                                    mimeType = media.getMimeType(),
                                    data = media.toBase64()
                                )
                            )
                        )

                    }

                    is MediaContent.Audio -> {
                        require(model.capabilities.contains(LLMCapability.Audio)) {
                            "Model ${model.id} does not support audio"
                        }
                        require(media.format in listOf("wav", "mp3", "aiff", "aac", "ogg", "flac")) {
                            "Audio format ${media.format} not supported"
                        }
                        add(GooglePart.InlineData(GoogleData.Blob(media.getMimeType(), media.toBase64())))
                    }

                    is MediaContent.File -> {
                        if (media.isUrl()) {
                            throw IllegalArgumentException("URL files not supported for Gemini models")
                        }
                        add(
                            GooglePart.InlineData(
                                GoogleData.Blob(
                                    mimeType = media.getMimeType(),
                                    data = media.toBase64()
                                )
                            )
                        )
                    }

                    is MediaContent.Video -> {
                        require(model.capabilities.contains(LLMCapability.Vision.Video)) {
                            "Model ${model.id} does not support video"
                        }
                        add(GooglePart.InlineData(GoogleData.Blob(media.getMimeType(), media.toBase64())))
                    }
                }
            }
        }

        return GoogleContent(role = "user", parts = contentParts)
    }

    /**
     * Builds a parameter type definition for Google tools.
     *
     * @param param The tool parameter descriptor
     * @return A JSON element representing the parameter type
     */
    private fun buildGoogleParamType(param: ToolParameterDescriptor): JsonObject = buildJsonObject {
        put("description", JsonPrimitive(param.description))

        fun JsonObjectBuilder.putType(type: ToolParameterType) {
            when (type) {
                ToolParameterType.Boolean -> put("type", "boolean")
                ToolParameterType.Float -> put("type", "number")
                ToolParameterType.Integer -> put("type", "integer")
                ToolParameterType.String -> put("type", "string")

                is ToolParameterType.Enum -> {
                    put("type", "string")
                    putJsonArray("enum") { type.entries.forEach { add(it) } }
                }

                is ToolParameterType.List -> {
                    put("type", "array")
                    put("items", buildJsonObject { putType(type.itemsType) })
                }

                is ToolParameterType.Object -> {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        type.properties.forEach { property ->
                            put(property.name, buildJsonObject {
                                putType(property.type)
                                put("description", property.description)
                            })
                        }
                    })
                }
            }
        }

        putType(param.type)
    }

    /**
     * Processes the Google AI API response into our internal message format.
     *
     * @param response The raw response from the Google AI API
     * @return A list of response messages
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun processGoogleResponse(response: GoogleResponse): List<Message.Response> {
        if (response.candidates.isEmpty()) {
            logger.error { "Empty candidates in Gemini response" }
            error("Empty candidates in Gemini response")
        }

        val (candidate, parts) = response.candidates
            .firstOrNull()
            ?.let { it to it.content?.parts.orEmpty() }
            ?: throw IllegalArgumentException("No responses found in Gemini response")

        // Extract token count from the response
        val inputTokensCount = response.usageMetadata?.promptTokenCount
        val outputTokensCount = response.usageMetadata?.candidatesTokenCount
        val totalTokensCount = response.usageMetadata?.totalTokenCount

        val responses = parts.map { part ->
            when (part) {
                is GooglePart.Text -> Message.Assistant(
                    content = part.text,
                    finishReason = candidate.finishReason,
                    metaInfo = ResponseMetaInfo.create(
                        clock,
                        totalTokensCount = totalTokensCount,
                        inputTokensCount = inputTokensCount,
                        outputTokensCount = outputTokensCount
                    )
                )

                is GooglePart.FunctionCall -> Message.Tool.Call(
                    id = Uuid.random().toString(),
                    tool = part.functionCall.name,
                    content = part.functionCall.args.toString(),
                    metaInfo = ResponseMetaInfo.create(
                        clock,
                        totalTokensCount = totalTokensCount,
                        inputTokensCount = inputTokensCount,
                        outputTokensCount = outputTokensCount
                    )
                )

                else -> error("Not supported part type: $part")
            }
        }

        return when {
            // Fix the situation when the model decides to both call tools and talk
            responses.any { it is Message.Tool.Call } -> responses.filterIsInstance<Message.Tool.Call>()
            // If no messages where returned, return an empty message and check finishReason
            responses.isEmpty() -> listOf(
                Message.Assistant(
                    content = "",
                    finishReason = candidate.finishReason,
                    metaInfo = ResponseMetaInfo.create(clock, totalTokensCount = totalTokensCount)
                )
            )
            // Just return responses
            else -> responses
        }
    }
}
