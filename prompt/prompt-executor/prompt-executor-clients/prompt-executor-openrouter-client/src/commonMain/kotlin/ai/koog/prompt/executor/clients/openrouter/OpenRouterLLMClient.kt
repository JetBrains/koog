package ai.koog.prompt.executor.clients.openrouter

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterToolChoice.FunctionName
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

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow {
        logger.debug { "Executing streaming prompt: $prompt" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        val request = createOpenRouterRequest(prompt, model, emptyList(), true)

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

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    flushCalls()
                    messages.add(
                        OpenRouterMessage(
                            role = "system",
                            content = Content.Text(message.content)
                        )
                    )
                }

                is Message.User -> {
                    flushCalls()
                    messages.add(message.toOpenRouterMessage(model))
                }

                is Message.Assistant -> {
                    flushCalls()
                    messages.add(
                        OpenRouterMessage(
                            role = "assistant",
                            content = Content.Text(message.content)
                        )
                    )
                }

                is Message.Tool.Result -> {
                    flushCalls()
                    messages.add(
                        OpenRouterMessage(
                            role = "tool",
                            content = Content.Text(message.content),
                            toolCallId = message.id
                        )
                    )
                }

                is Message.Tool.Call -> pendingCalls += OpenRouterToolCall(
                    id = message.id ?: Uuid.random().toString(),
                    function = OpenRouterFunction(message.tool, message.content)
                )
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

    private fun Message.User.toOpenRouterMessage(model: LLModel): OpenRouterMessage {
        val messageContent: Content = if (attachments.isEmpty()) {
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

        return OpenRouterMessage(role = "user", content = messageContent)
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

    /**
     * Executes a moderation action on the given prompt using the specified language model.
     * This method is not supported by the OpenRouter API and will always throw an `UnsupportedOperationException`.
     *
     * @param prompt The [Prompt] object to be moderated, containing the messages and respective context.
     * @param model The [LLModel] to be used for the moderation process.
     * @return This method does not return a valid result as it always throws an exception.
     * @throws UnsupportedOperationException Always thrown because moderation is not supported by the OpenRouter API.
     */
    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by OpenRouter API" }
        throw UnsupportedOperationException("Moderation is not supported by OpenRouter API.")
    }
}
