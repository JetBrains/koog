package ai.koog.prompt.executor.clients.anthropic

import ai.koog.agents.core.tools.ToolDescriptor
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents the settings for configuring an Anthropic client, including model mapping, base URL, and API version.
 *
 * @property modelVersionsMap Maps specific `LLModel` instances to their corresponding model version strings.
 * This determines which Anthropic model versions are used for operations.
 * @property baseUrl The base URL for accessing the Anthropic API. Defaults to "https://api.anthropic.com".
 * @property apiVersion The version of the Anthropic API to be used. Defaults to "2023-06-01".
 */
public class AnthropicClientSettings(
    public val modelVersionsMap: Map<LLModel, String> = DEFAULT_ANTHROPIC_MODEL_VERSIONS_MAP,
    public val baseUrl: String = "https://api.anthropic.com",
    public val apiVersion: String = "2023-06-01",
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
)

/**
 * A client implementation for interacting with Anthropic's API in a suspendable and direct manner.
 *
 * This class supports functionalities for executing text prompts and streaming interactions with the Anthropic API.
 * It leverages Kotlin Coroutines to handle asynchronous operations and provides full support for configuring HTTP
 * requests, including timeout handling and JSON serialization.
 *
 * @constructor Creates an instance of the AnthropicSuspendableDirectClient.
 * @param apiKey The API key required to authenticate with the Anthropic service.
 * @param settings Configurable settings for the Anthropic client, which include the base URL and other options.
 * @param baseClient An optional custom configuration for the underlying HTTP client, defaulting to a Ktor client.
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public open class AnthropicLLMClient(
    private val apiKey: String,
    private val settings: AnthropicClientSettings = AnthropicClientSettings(),
    baseClient: HttpClient = HttpClient(),
    private val clock: Clock = Clock.System
) : LLMClient {

    private companion object {
        private val logger = KotlinLogging.logger { }

        private const val DEFAULT_MESSAGE_PATH = "v1/messages"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true // Ensure default values are included in serialization
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    private val httpClient = baseClient.config {
        defaultRequest {
            url(settings.baseUrl)
            contentType(ContentType.Application.Json)
            header("x-api-key", apiKey)
            header("anthropic-version", settings.apiVersion)
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
        require(model.capabilities.contains(LLMCapability.Tools)) {
            "Model ${model.id} does not support tools"
        }

        val request = createAnthropicRequest(prompt, tools, model, false)

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post(DEFAULT_MESSAGE_PATH) {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val anthropicResponse = response.body<AnthropicResponse>()
                processAnthropicResponse(anthropicResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from Anthropic API: ${response.status}: $errorBody" }
                error("Error from Anthropic API: ${response.status}: $errorBody")
            }
        }
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model without tools" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        val request = createAnthropicRequest(prompt, emptyList(), model, true)

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
                            .takeIf { it.event == "content_block_delta" }
                            ?.data?.trim()?.let { json.decodeFromString<AnthropicStreamResponse>(it) }
                            ?.delta?.text?.let { emit(it) }
                    }
                }
            } catch (e: SSEClientException) {
                e.response?.let { response ->
                    logger.error { "Error from Anthropic API: ${response.status}: ${e.message}" }
                    error("Error from Anthropic API: ${response.status}: ${e.message}")
                }
            } catch (e: Exception) {
                logger.error { "Exception during streaming: $e" }
                error(e.message ?: "Unknown error during streaming")
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createAnthropicRequest(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        model: LLModel,
        stream: Boolean
    ): AnthropicMessageRequest {
        val (systemMessages, convMessages) = prompt.messages.partition { it is Message.System }

        val messages = convMessages.fold(mutableListOf<AnthropicMessage>()) { acc, message ->
            when (message) {
                is Message.User -> acc.add(message.toAnthropicUserMessage(model))
                is Message.Assistant -> {
                    acc.add(
                        AnthropicMessage(
                            role = "assistant",
                            content = listOf(AnthropicContent.Text(message.content))
                        )
                    )
                }

                is Message.Tool.Result -> {
                    val toolResult = AnthropicContent.ToolResult(
                        toolUseId = message.id.orEmpty(),
                        content = message.content
                    )
                    acc.lastOrNull { it.role == "user" }?.let { lastUserMessage ->
                        acc[acc.lastIndex] = lastUserMessage.copy(content = lastUserMessage.content + toolResult)
                    } ?: acc.add(AnthropicMessage(role = "user", content = listOf(toolResult)))
                }

                is Message.Tool.Call -> {
                    val toolUse = AnthropicContent.ToolUse(
                        id = message.id ?: Uuid.random().toString(),
                        name = message.tool,
                        input = Json.parseToJsonElement(message.content).jsonObject
                    )
                    acc.lastOrNull { it.role == "assistant" }?.let { lastAssistantMessage ->
                        acc[acc.lastIndex] = lastAssistantMessage.copy(content = lastAssistantMessage.content + toolUse)
                    } ?: acc.add(AnthropicMessage(role = "assistant", content = listOf(toolUse)))
                }

                is Message.System -> {
                    logger.warn { "System messages already prepares for Anthropic. Ignoring: ${message.content}" }
                }
            }
            acc
        }

        val anthropicTools = tools.map { tool ->
            val properties = mutableMapOf<String, JsonElement>()

            (tool.requiredParameters + tool.optionalParameters).forEach { param ->
                val typeMap = getTypeMapForParameter(param.type)

                properties[param.name] = JsonObject(
                    mapOf("description" to JsonPrimitive(param.description)) + typeMap
                )
            }

            AnthropicTool(
                name = tool.name,
                description = tool.description,
                inputSchema = AnthropicToolSchema(
                    properties = JsonObject(properties),
                    required = tool.requiredParameters.map { it.name }
                )
            )
        }

        val toolChoice = when (val toolChoice = prompt.params.toolChoice) {
            LLMParams.ToolChoice.Auto -> AnthropicToolChoice.Auto
            LLMParams.ToolChoice.None -> AnthropicToolChoice.None
            LLMParams.ToolChoice.Required -> AnthropicToolChoice.Any
            is LLMParams.ToolChoice.Named -> AnthropicToolChoice.Tool(name = toolChoice.name)
            null -> null
        }

        // Always include max_tokens as it's required by the API
        return AnthropicMessageRequest(
            model = settings.modelVersionsMap[model]
                ?: throw IllegalArgumentException("Unsupported model: $model"),
            messages = messages,
            maxTokens = 2048, // This is required by the API
            // TODO why 0.7 and not 0.0?
            temperature = prompt.params.temperature ?: 0.7, // Default temperature if not provided
            system = systemMessages.map { SystemAnthropicMessage(it.content) },
            tools = if (tools.isNotEmpty()) anthropicTools else emptyList(), // Always provide a list for tools
            stream = stream,
            toolChoice = toolChoice,
        )
    }

    private fun Message.User.toAnthropicUserMessage(model: LLModel): AnthropicMessage {
        val listOfContent = buildList {
            if (content.isNotEmpty() || mediaContent.isEmpty()) {
                add(AnthropicContent.Text(content))
            }

            mediaContent.forEach { media ->
                when (media) {
                    is MediaContent.Image -> {
                        require(model.capabilities.contains(LLMCapability.Vision.Image)) {
                            "Model ${model.id} does not support image"
                        }

                        if (media.isUrl()) {
                            add(AnthropicContent.Image(ImageSource.Url(media.source)))
                        } else {
                            require(media.format in listOf("png", "jpg", "jpeg", "webp", "gif")) {
                                "Image format ${media.format} not supported"
                            }
                            add(
                                AnthropicContent.Image(
                                    ImageSource.Base64(
                                        data = media.toBase64(),
                                        mediaType = media.getMimeType()
                                    )
                                )
                            )
                        }
                    }

                    is MediaContent.File -> {
                        require(model.capabilities.contains(LLMCapability.Vision.Image)) {
                            "Model ${model.id} does not support files"
                        }

                        val docSource = when {
                            media.isUrl() -> DocumentSource.PDFUrl(media.source)
                            media.format == "pdf" -> DocumentSource.PDFBase64(media.toBase64())
                            media.format == "txt" || media.format == "md" -> DocumentSource.PlainText(media.readText())
                            else -> throw IllegalArgumentException("File format ${media.format} not supported. Supported formats: `pdf`, `text`")
                        }
                        add(AnthropicContent.Document(docSource))
                    }

                    else -> throw IllegalArgumentException("Media content not supported: $media")
                }
            }
        }

        return AnthropicMessage(role = "user", content = listOfContent)
    }

    private fun processAnthropicResponse(response: AnthropicResponse): List<Message.Response> {
        // Extract token count from the response
        val inputTokensCount = response.usage?.inputTokens
        val outputTokensCount = response.usage?.outputTokens
        val totalTokensCount = response.usage?.let { it.inputTokens + it.outputTokens }

        val responses = response.content.map { content ->
            when (content) {
                is AnthropicResponseContent.Text -> {
                    Message.Assistant(
                        content = content.text,
                        finishReason = response.stopReason,
                        metaInfo = ResponseMetaInfo.create(
                            clock,
                            totalTokensCount = totalTokensCount,
                            inputTokensCount = inputTokensCount,
                            outputTokensCount = outputTokensCount,
                        )
                    )
                }

                is AnthropicResponseContent.ToolUse -> {
                    Message.Tool.Call(
                        id = content.id,
                        tool = content.name,
                        content = content.input.toString(),
                        metaInfo = ResponseMetaInfo.create(
                            clock,
                            totalTokensCount = totalTokensCount,
                            inputTokensCount = inputTokensCount,
                            outputTokensCount = outputTokensCount,
                        )
                    )
                }
            }
        }

        return when {
            // Fix the situation when the model decides to both call tools and talk
            responses.any { it is Message.Tool.Call } -> responses.filterIsInstance<Message.Tool.Call>()
            // If no messages where returned, return an empty message and check stopReason
            responses.isEmpty() -> listOf(
                Message.Assistant(
                    content = "",
                    finishReason = response.stopReason,
                    metaInfo = ResponseMetaInfo.create(
                        clock,
                        totalTokensCount = totalTokensCount,
                        inputTokensCount = inputTokensCount,
                        outputTokensCount = outputTokensCount,
                    )
                )
            )
            // Just return responses
            else -> responses
        }
    }

    /**
     * Helper function to get the type map for a parameter type without using smart casting
     */
    private fun getTypeMapForParameter(type: ToolParameterType): JsonObject {
        return when (type) {
            ToolParameterType.Boolean -> JsonObject(mapOf("type" to JsonPrimitive("boolean")))
            ToolParameterType.Float -> JsonObject(mapOf("type" to JsonPrimitive("number")))
            ToolParameterType.Integer -> JsonObject(mapOf("type" to JsonPrimitive("integer")))
            ToolParameterType.String -> JsonObject(mapOf("type" to JsonPrimitive("string")))
            is ToolParameterType.Enum -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("string"),
                    "enum" to JsonArray(type.entries.map { JsonPrimitive(it.lowercase()) })
                )
            )

            is ToolParameterType.List -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("array"),
                    "items" to getTypeMapForParameter(type.itemsType)
                )
            )

            is ToolParameterType.Object -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(type.properties.associate {
                        it.name to JsonObject(
                            mapOf(
                                "type" to getTypeMapForParameter(it.type),
                                "description" to JsonPrimitive(it.description)
                            )
                        )
                    })
                )
            )
        }
    }
}
