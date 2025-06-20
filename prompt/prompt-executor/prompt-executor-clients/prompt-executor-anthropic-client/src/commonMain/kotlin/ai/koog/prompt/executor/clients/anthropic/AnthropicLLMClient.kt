package ai.koog.prompt.executor.clients.anthropic

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.utils.SuitableForIO
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

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow {
        logger.debug { "Executing streaming prompt: $prompt with model: $model without tools" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        val request = createAnthropicRequest(prompt, emptyList(), model, true)

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

    @OptIn(ExperimentalUuidApi::class)
    private fun createAnthropicRequest(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        model: LLModel,
        stream: Boolean
    ): AnthropicMessageRequest {
        val systemMessage = mutableListOf<SystemAnthropicMessage>()
        val messages = mutableListOf<AnthropicMessage>()

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    systemMessage.add(SystemAnthropicMessage(message.content))
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

        // Always include max_tokens as it's required by the API
        return AnthropicMessageRequest(
            model = settings.modelVersionsMap[model]
                ?: throw IllegalArgumentException("Unsupported model: $model"),
            messages = messages,
            maxTokens = 2048, // This is required by the API
            // TODO why 0.7 and not 0.0?
            temperature = prompt.params.temperature ?: 0.7, // Default temperature if not provided
            system = systemMessage,
            tools = if (tools.isNotEmpty()) anthropicTools else emptyList(), // Always provide a list for tools
            stream = stream,
            toolChoice = toolChoice,
        )
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
                            is AttachmentContent.Binary -> ImageSource.Base64(content.base64, attachment.mimeType)
                            else -> throw IllegalArgumentException("Unsupported image attachment content: ${content::class}")
                        }

                        add(AnthropicContent.Image(imageSource))
                    }

                    is Attachment.File -> {
                        require(model.capabilities.contains(LLMCapability.Document)) {
                            "Model ${model.id} does not support files"
                        }

                        val documentSource: DocumentSource = when (val content = attachment.content) {
                            is AttachmentContent.URL-> DocumentSource.Url(content.url)
                            is AttachmentContent.Binary -> DocumentSource.Base64(content.base64, attachment.mimeType)
                            is AttachmentContent.PlainText -> DocumentSource.PlainText(content.text, attachment.mimeType)
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
