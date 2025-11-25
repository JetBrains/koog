package ai.koog.prompt.executor.clients.anthropic

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.fromKtorClient
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicContent
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicMessage
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicMessageRequest
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicMessageRequestSerializer
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicResponse
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicStreamDeltaContentType
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicStreamEventType
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicStreamResponse
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicTool
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicToolChoice
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicToolSchema
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicUsage
import ai.koog.prompt.executor.clients.anthropic.models.DocumentSource
import ai.koog.prompt.executor.clients.anthropic.models.ImageSource
import ai.koog.prompt.executor.clients.anthropic.models.SystemAnthropicMessage
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.buildStreamFrameFlow
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.Flow
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
    public val messagesPath: String = "v1/messages",
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
    private val baseClient: HttpClient = HttpClient(),
    private val clock: Clock = Clock.System
) : LLMClient {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true // Ensure default values are included in serialization
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    // Configures HTTP client with timeouts, headers, and JSON handling
    protected val httpClient: KoogHttpClient = KoogHttpClient.fromKtorClient(
        clientName = clientName,
        logger = logger,
        baseClient = baseClient
    ) {
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

    /**
     * Provides the specific Large Language Model (LLM) provider used by the client.
     *
     * This method returns the LLM provider that the client is configured to use,
     * allowing identification and configuration of provider-specific features.
     *
     * @return The LLM provider associated with this client, specifically `LLMProvider.Anthropic`.
     */
    override fun llmProvider(): LLMProvider = LLMProvider.Anthropic

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.capabilities.contains(LLMCapability.Tools)) {
            "Model ${model.id} does not support tools"
        }

        val request = createAnthropicRequest(prompt, tools, model, false)

        return try {
            httpClient.post(
                path = settings.messagesPath,
                request = request,
                requestBodyType = String::class,
                responseType = AnthropicResponse::class,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException(
                clientName = clientName,
                message = e.message,
                cause = e
            )
        }.let(::processAnthropicResponse)
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
        return buildStreamFrameFlow {
            var inputTokens: Int? = null
            var outputTokens: Int? = null

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

            try {
                httpClient.sse(
                    path = settings.messagesPath,
                    request = request,
                    requestBodyType = String::class,
                    decodeStreamingResponse = { json.decodeFromString<AnthropicStreamResponse>(it) },
                    processStreamingChunk = { it }
                ).collect { response ->
                    when (response.eventType) {
                        AnthropicStreamEventType.MESSAGE_START.value -> {
                            response.message?.usage?.let(::updateUsage)
                        }

                        AnthropicStreamEventType.CONTENT_BLOCK_START.value -> {
                            when (val contentBlock = response.contentBlock) {
                                is AnthropicContent.Text -> {
                                    emitAppend(contentBlock.text)
                                }

                                is AnthropicContent.ToolUse -> {
                                    upsertToolCall(
                                        index = response.index
                                            ?: throw LLMClientException(clientName, "Tool index is missing"),
                                        id = contentBlock.id,
                                        name = contentBlock.name,
                                    )
                                }

                                else -> {
                                    contentBlock?.let { logger.warn { "Unknown Anthropic stream content block type: ${it::class}" } }
                                        ?: logger.warn { "Anthropic stream content block is missing" }
                                }
                            }
                        }

                        AnthropicStreamEventType.CONTENT_BLOCK_DELTA.value -> {
                            response.delta?.let { delta ->
                                // Handles deltas for tool calls and text

                                when (delta.deltaType) {
                                    AnthropicStreamDeltaContentType.INPUT_JSON_DELTA.value -> {
                                        upsertToolCall(
                                            index = response.index
                                                ?: throw LLMClientException(clientName, "Tool index is missing"),
                                            args = delta.partialJson
                                                ?: throw LLMClientException(clientName, "Tool args are missing")
                                        )
                                    }

                                    AnthropicStreamDeltaContentType.TEXT_DELTA.value -> {
                                        emitAppend(
                                            delta.text
                                                ?: throw LLMClientException(clientName, "Text delta is missing")
                                        )
                                    }

                                    else -> {
                                        logger.warn { "Unknown Anthropic stream delta type: ${delta.deltaType}" }
                                    }
                                }
                            }
                        }

                        AnthropicStreamEventType.CONTENT_BLOCK_STOP.value -> {
                            tryEmitPendingToolCall()
                        }

                        AnthropicStreamEventType.MESSAGE_DELTA.value -> {
                            response.usage?.let(::updateUsage)
                            emitEnd(
                                finishReason = response.delta?.stopReason,
                                metaInfo = getMetaInfo()
                            )
                        }

                        AnthropicStreamEventType.MESSAGE_STOP.value -> {
                            logger.debug { "Received stop message event from Anthropic" }
                        }

                        AnthropicStreamEventType.ERROR.value -> {
                            throw LLMClientException(clientName, "Anthropic error: ${response.error}")
                        }

                        AnthropicStreamEventType.PING.value -> {
                            logger.debug { "Received ping from Anthropic" }
                        }
                    }
                }
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
    }

    @OptIn(ExperimentalUuidApi::class)
    internal fun createAnthropicRequest(
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
                        AnthropicMessage.Assistant(
                            content = listOf(AnthropicContent.Text(message.content))
                        )
                    )
                }

                is Message.Reasoning -> {
                    messages.add(
                        AnthropicMessage.Assistant(
                            content = listOf(
                                AnthropicContent.Thinking(
                                    signature = message.encrypted
                                        ?: throw IllegalArgumentException("Encrypted signature is required for reasoning messages but was null"),
                                    thinking = message.content
                                )
                            )
                        )
                    )
                }

                is Message.Tool.Result -> {
                    messages.add(
                        AnthropicMessage.User(
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
                        AnthropicMessage.Assistant(
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

        return serializeAnthropicMessageRequest(
            messages,
            systemMessage,
            model,
            anthropicTools,
            prompt.params,
            stream
        )
    }

    private fun serializeAnthropicMessageRequest(
        messages: List<AnthropicMessage>,
        systemMessages: List<SystemAnthropicMessage>,
        model: LLModel,
        tools: List<AnthropicTool>,
        params: LLMParams,
        stream: Boolean
    ): String {
        val anthropicParams = params.toAnthropicParams()

        val toolChoice = when (val toolChoice = anthropicParams.toolChoice) {
            LLMParams.ToolChoice.Auto -> AnthropicToolChoice.Auto
            LLMParams.ToolChoice.None -> AnthropicToolChoice.None
            LLMParams.ToolChoice.Required -> AnthropicToolChoice.Any
            is LLMParams.ToolChoice.Named -> AnthropicToolChoice.Tool(name = toolChoice.name)
            null -> null
        }

        require(anthropicParams.schema == null) {
            "Anthropic does not currently support native structured output."
        }

        // Always include max_tokens as it's required by the API
        val request = AnthropicMessageRequest(
            model = settings.modelVersionsMap[model] ?: throw IllegalArgumentException("Unsupported model: $model"),
            messages = messages,
            maxTokens = anthropicParams.maxTokens ?: AnthropicMessageRequest.MAX_TOKENS_DEFAULT,
            container = anthropicParams.container,
            mcpServers = anthropicParams.mcpServers,
            serviceTier = anthropicParams.serviceTier,
            stopSequence = anthropicParams.stopSequences,
            stream = stream,
            system = systemMessages,
            temperature = anthropicParams.temperature,
            thinking = anthropicParams.thinking,
            toolChoice = toolChoice,
            tools = tools, // Always provide a list for tools
            topK = anthropicParams.topK,
            topP = anthropicParams.topP,
            additionalProperties = anthropicParams.additionalProperties
        )

        return json.encodeToString(AnthropicMessageRequestSerializer, request)
    }

    private fun Message.User.toAnthropicUserMessage(model: LLModel): AnthropicMessage {
        val listOfContent = buildList {
            parts.forEach { part ->
                when (part) {
                    is ContentPart.Text -> add(AnthropicContent.Text(part.text))

                    is ContentPart.Image -> {
                        require(model.capabilities.contains(LLMCapability.Vision.Image)) {
                            "Model ${model.id} does not support images"
                        }

                        val imageSource: ImageSource = when (val content = part.content) {
                            is AttachmentContent.URL -> ImageSource.Url(content.url)
                            is AttachmentContent.Binary -> ImageSource.Base64(content.asBase64(), part.mimeType)
                            else -> throw LLMClientException(
                                clientName,
                                "Unsupported image attachment content: ${content::class}"
                            )
                        }

                        add(AnthropicContent.Image(imageSource))
                    }

                    is ContentPart.File -> {
                        require(model.capabilities.contains(LLMCapability.Document)) {
                            "Model ${model.id} does not support files"
                        }

                        val documentSource: DocumentSource = when (val content = part.content) {
                            is AttachmentContent.URL -> DocumentSource.Url(content.url)
                            is AttachmentContent.Binary -> DocumentSource.Base64(
                                content.asBase64(),
                                part.mimeType
                            )

                            is AttachmentContent.PlainText -> DocumentSource.PlainText(
                                content.text,
                                part.mimeType
                            )
                        }

                        add(AnthropicContent.Document(documentSource))
                    }

                    else -> throw LLMClientException(
                        clientName,
                        "Unsupported attachment type: $part"
                    )
                }
            }
        }

        return AnthropicMessage.User(content = listOfContent)
    }

    private fun processAnthropicResponse(response: AnthropicResponse): List<Message.Response> {
        // Extract token count from the response
        val inputTokensCount = response.usage?.inputTokens
        val outputTokensCount = response.usage?.outputTokens
        val totalTokensCount = response.usage?.let { it.inputTokens?.plus(it.outputTokens ?: 0) ?: it.outputTokens }
        val metaInfo = ResponseMetaInfo.create(
            clock,
            totalTokensCount = totalTokensCount,
            inputTokensCount = inputTokensCount,
            outputTokensCount = outputTokensCount,
        )

        val responses = response.content.map { content ->
            when (content) {
                is AnthropicContent.Text -> {
                    Message.Assistant(
                        content = content.text,
                        finishReason = response.stopReason,
                        metaInfo = metaInfo
                    )
                }

                is AnthropicContent.Thinking -> {
                    Message.Reasoning(
                        encrypted = content.signature,
                        content = content.thinking,
                        metaInfo = metaInfo
                    )
                }

                is AnthropicContent.ToolUse -> {
                    Message.Tool.Call(
                        id = content.id,
                        tool = content.name,
                        content = content.input.toString(),
                        metaInfo = metaInfo
                    )
                }

                else -> throw LLMClientException(
                    clientName,
                    "Unhandled AnthropicContent type. Content: $content"
                )
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
            ToolParameterType.Null -> JsonObject(mapOf("type" to JsonPrimitive("null")))
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

            is ToolParameterType.AnyOf -> throw LLMClientException(
                clientName,
                "AnyOf type is not supported"
            )
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

    override fun close() {
        baseClient.close()
    }
}
