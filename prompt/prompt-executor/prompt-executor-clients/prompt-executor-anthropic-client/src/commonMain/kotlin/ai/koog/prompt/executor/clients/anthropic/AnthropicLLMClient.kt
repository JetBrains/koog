package ai.koog.prompt.executor.clients.anthropic

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.buildStreamFrameFlow
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
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model with tools: ${tools.map { it.name }}" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        val request = createAnthropicRequest(prompt, tools, model, true)

        try {
            return buildStreamFrameFlow {
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
                    var inputTokens: Int? = null
                    var outputTokens: Int? = null

                    fun decodeResponse(event: ServerSentEvent): AnthropicStreamResponse? =
                        event.data?.trim()?.let { json.decodeFromString<AnthropicStreamResponse>(it) }

                    fun updateUsage(usage: AnthropicUsage) {
                        inputTokens = usage.inputTokens ?: inputTokens
                        outputTokens = usage.outputTokens ?: outputTokens
                    }

                    fun getMetaInfo(): ResponseMetaInfo = ResponseMetaInfo.create(
                        clock = clock,
                        totalTokensCount = inputTokens?.plus(outputTokens ?: 0) ?: outputTokens,
                        inputTokensCount = inputTokens,
                        outputTokensCount = outputTokens,
                    )

                    incoming.collect { event ->

                        when (event.event) {
                            "message_start" -> {
                                decodeResponse(event)?.message?.usage?.let(::updateUsage)
                            }

                            "content_block_start" -> {
                                decodeResponse(event)?.let { response ->
                                    when (val contentBlock = response.contentBlock) {
                                        is AnthropicContent.Text -> {
                                            emitAppend(contentBlock.text)
                                        }

                                        is AnthropicContent.ToolUse -> {
                                            upsertToolCall(
                                                index = response.index ?: error("Tool index is missing"),
                                                id = contentBlock.id,
                                                name = contentBlock.name,
                                            )
                                        }

                                        else -> Unit
                                    }
                                }
                            }

                            "content_block_delta" -> {
                                decodeResponse(event)?.let { response ->
                                    response.delta?.let { delta ->
                                        when (delta.type) {
                                            "input_json_delta" -> {
                                                upsertToolCall(
                                                    index = response.index ?: error("Tool index is missing"),
                                                    args = delta.partialJson ?: error("Tool args are missing")
                                                )
                                            }

                                            "text_delta" -> {
                                                emitAppend(delta.text ?: error("Text delta is missing"))
                                            }
                                        }
                                    }
                                }
                            }

                            "content_block_stop" -> {
                                tryEmitPendingToolCall()
                            }

                            "message_delta" -> {
                                decodeResponse(event)?.let { response ->
                                    response.usage?.let(::updateUsage)
                                    emitEnd(
                                        finishReason = response.delta?.stopReason,
                                        metaInfo = getMetaInfo()
                                    )
                                }
                            }

                            "error" -> {
                                error("Anthropic error: ${decodeResponse(event)?.error}")
                            }
                        }
                    }
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
        return emptyFlow()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createAnthropicRequest(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        model: LLModel,
        stream: Boolean
    ): String {
        val systemMessage = mutableListOf<SystemAnthropicMessage>()
        val messages = mutableListOf<AnthropicMessage>()

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    if (!message.content.isEmpty()) {
                        systemMessage.add(SystemAnthropicMessage(message.content))
                    }
                }

                is Message.User -> {
                    messages.add(message.toAnthropicUserMessage(model))
                }

                is Message.Assistant -> {
                    messages.add(
                        AnthropicMessage(
                            role = "assistant",
                            content = listOf(AnthropicContent.Text(message.content))
                        )
                    )
                }

                is Message.Tool.Result -> {
                    messages.add(
                        AnthropicMessage(
                            role = "user",
                            content = listOf(
                                AnthropicContent.ToolResult(
                                    toolUseId = message.id ?: "",
                                    content = message.content
                                )
                            )
                        )
                    )
                }

                is Message.Tool.Call -> {
                    // Create a new assistant message with the tool call
                    messages.add(
                        AnthropicMessage(
                            role = "assistant",
                            content = listOf(
                                AnthropicContent.ToolUse(
                                    id = message.id ?: Uuid.random().toString(),
                                    name = message.tool,
                                    input = Json.parseToJsonElement(message.content).jsonObject
                                )
                            )
                        )
                    )
                }
            }
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

        require(prompt.params.schema == null) {
            "Anthropic does not currently support native structured output."
        }

        // Always include max_tokens as it's required by the API
        val request = AnthropicMessageRequest(
            model = settings.modelVersionsMap[model]
                ?: throw IllegalArgumentException("Unsupported model: $model"),
            messages = messages,
            maxTokens = prompt.params.maxTokens ?: AnthropicMessageRequest.MAX_TOKENS_DEFAULT,
            temperature = prompt.params.temperature,
            system = systemMessage,
            tools = if (tools.isNotEmpty()) anthropicTools else emptyList(), // Always provide a list for tools
            stream = stream,
            toolChoice = toolChoice,
            additionalProperties = prompt.params.additionalProperties
        )
        return json.encodeToString(AnthropicMessageRequestSerializer, request)
    }

    private fun Message.User.toAnthropicUserMessage(model: LLModel): AnthropicMessage {
        val listOfContent = buildList {
            if (content.isNotEmpty() || attachments.isEmpty()) {
                add(AnthropicContent.Text(content))
            }

            attachments.forEach { attachment ->
                when (attachment) {
                    is Attachment.Image -> {
                        require(model.capabilities.contains(LLMCapability.Vision.Image)) {
                            "Model ${model.id} does not support images"
                        }

                        val imageSource: ImageSource = when (val content = attachment.content) {
                            is AttachmentContent.URL -> ImageSource.Url(content.url)
                            is AttachmentContent.Binary -> ImageSource.Base64(content.asBase64(), attachment.mimeType)
                            else -> throw IllegalArgumentException(
                                "Unsupported image attachment content: ${content::class}"
                            )
                        }

                        add(AnthropicContent.Image(imageSource))
                    }

                    is Attachment.File -> {
                        require(model.capabilities.contains(LLMCapability.Document)) {
                            "Model ${model.id} does not support files"
                        }

                        val documentSource: DocumentSource = when (val content = attachment.content) {
                            is AttachmentContent.URL -> DocumentSource.Url(content.url)
                            is AttachmentContent.Binary -> DocumentSource.Base64(content.asBase64(), attachment.mimeType)
                            is AttachmentContent.PlainText -> DocumentSource.PlainText(
                                content.text,
                                attachment.mimeType
                            )
                        }

                        add(AnthropicContent.Document(documentSource))
                    }

                    else -> throw IllegalArgumentException("Unsupported attachment type: $attachment")
                }
            }
        }

        return AnthropicMessage(role = "user", content = listOfContent)
    }

    private fun processAnthropicResponse(response: AnthropicResponse): List<Message.Response> {
        // Extract token count from the response
        val inputTokensCount = response.usage?.inputTokens
        val outputTokensCount = response.usage?.outputTokens
        val totalTokensCount = response.usage?.let { it.inputTokens?.plus(it.outputTokens ?: 0) ?: it.outputTokens }

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

            is ToolParameterType.Object -> {
                // Create properties map with proper type information
                val propertiesMap = mutableMapOf<String, JsonElement>()

                for (prop in type.properties) {
                    // Get type information for the property
                    val typeInfo = getTypeMapForParameter(prop.type)

                    // Create a map with all type properties and description
                    val propMap = mutableMapOf<String, JsonElement>()
                    for (entry in typeInfo.entries) {
                        propMap[entry.key] = entry.value
                    }
                    propMap["description"] = JsonPrimitive(prop.description)

                    // Add to properties map
                    propertiesMap[prop.name] = JsonObject(propMap)
                }

                // Create the final object schema
                val objectMap = mutableMapOf<String, JsonElement>()
                objectMap["type"] = JsonPrimitive("object")
                objectMap["properties"] = JsonObject(propertiesMap)

                // Add required field if requiredProperties is not empty
                if (type.requiredProperties.isNotEmpty()) {
                    objectMap["required"] = JsonArray(type.requiredProperties.map { JsonPrimitive(it) })
                }

                // Add additionalProperties for strict validation
                objectMap["additionalProperties"] = JsonPrimitive(type.additionalProperties ?: false)

                JsonObject(objectMap)
            }
        }
    }

    /**
     * Attempts to moderate the content of a given prompt using a specific language model.
     * This method is not supported by the Anthropic API and will always throw an exception.
     *
     * @param prompt The prompt to be moderated, containing messages and optional configuration parameters.
     * @param model The language model to use for moderation.
     * @return This method does not return a value as it always throws an exception.
     * @throws UnsupportedOperationException Always thrown, as moderation is not supported by the Anthropic API.
     */
    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by Anthropic API" }
        throw UnsupportedOperationException("Moderation is not supported by Anthropic API.")
    }
}
