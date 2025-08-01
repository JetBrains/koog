package ai.koog.prompt.executor.clients.openai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.models.Content
import ai.koog.prompt.executor.clients.openai.models.ContentPart
import ai.koog.prompt.executor.clients.openai.models.OpenAIAudioConfig
import ai.koog.prompt.executor.clients.openai.models.OpenAIAudioFormat
import ai.koog.prompt.executor.clients.openai.models.OpenAIAudioVoice
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionRequest
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionStreamResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIChoice
import ai.koog.prompt.executor.clients.openai.models.OpenAIFunction
import ai.koog.prompt.executor.clients.openai.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.models.OpenAIModalities
import ai.koog.prompt.executor.clients.openai.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.models.OpenAIToolCall
import ai.koog.prompt.executor.clients.openai.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.models.OpenAIToolFunction
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import io.github.oshai.kotlinlogging.KLogger
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
import io.ktor.client.statement.readRawBytes
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
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public abstract class OpenAIBasedSettings(
    public val baseUrl: String,
    public val chatCompletionsPath: String,
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
)

public abstract class AbstractOpenAILLMClient(
    private val apiKey: String,
    settings: OpenAIBasedSettings,
    baseClient: HttpClient = HttpClient(),
    private val clock: Clock = Clock.System
) : LLMClient {

    protected abstract val logger: KLogger

    protected open val clientName: String = this::class.simpleName ?: "UnknownClient"

    private val chatCompletionsPath: String = settings.chatCompletionsPath

    protected val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    protected val httpClient: HttpClient = baseClient.config {
        defaultRequest {
            url(settings.baseUrl)
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
        }
        install(SSE)
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = settings.timeoutConfig.requestTimeoutMillis // Increase timeout to 60 seconds
            connectTimeoutMillis = settings.timeoutConfig.connectTimeoutMillis
            socketTimeoutMillis = settings.timeoutConfig.socketTimeoutMillis
        }
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        val response = getOpenAIResponse(prompt, model, tools)
        return processOpenAIResponse(response).first()
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        model.requireCapability(LLMCapability.Completion)

        val request = createOpenAIRequest(prompt, emptyList(), model, true)

        try {
            httpClient.sse(
                urlString = chatCompletionsPath,
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
                        ?.data?.trim()
                        ?.let { json.decodeFromString<OpenAIChatCompletionStreamResponse>(it) }
                        ?.choices
                        ?.forEach { choice -> choice.delta.content?.let { emit(it) } }
                }
            }
        } catch (e: SSEClientException) {
            e.response?.let { response ->
                val body = response.readRawBytes().decodeToString()
                logger.error(e) { "Error from $clientName API: ${response.status}: ${e.message}.\nBody:\n$body" }
                error("Error from $clientName API: ${response.status}: ${e.message}")
            }
        } catch (e: Exception) {
            logger.error { "Exception during streaming from $clientName: $e" }
            error(e.message ?: "Unknown error during streaming from $clientName: $e")
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> = processOpenAIResponse(getOpenAIResponse(prompt, model, tools))

    @OptIn(ExperimentalUuidApi::class)
    private fun createOpenAIRequest(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        model: LLModel,
        stream: Boolean
    ): OpenAIChatCompletionRequest {
        val messages = mutableListOf<OpenAIMessage>()
        val pendingCalls = mutableListOf<OpenAIToolCall>()

        fun flushPendingCalls() {
            if (pendingCalls.isNotEmpty()) {
                messages += OpenAIMessage.Assistant(toolCalls = pendingCalls.toList())
                pendingCalls.clear()
            }
        }

        with(messages) {
            prompt.messages.forEach { message ->
                when (message) {
                    is Message.System -> {
                        flushPendingCalls()
                        add(OpenAIMessage.System(content = Content.Text(message.content)))
                    }

                    is Message.User -> {
                        flushPendingCalls()
                        add(OpenAIMessage.User(content = message.toOpenAIMessageContent(model)))
                    }

                    is Message.Assistant -> {
                        flushPendingCalls()
                        add(OpenAIMessage.Assistant(content = Content.Text(message.content)))
                    }

                    is Message.Tool.Result -> {
                        flushPendingCalls()
                        add(
                            OpenAIMessage.Tool(
                                content = Content.Text(message.content),
                                toolCallId = message.id ?: Uuid.random().toString()
                            )
                        )
                    }

                    is Message.Tool.Call -> {
                        pendingCalls += OpenAIToolCall(
                            message.id ?: Uuid.random().toString(),
                            function = OpenAIFunction(message.tool, message.content)
                        )
                    }
                }
            }
        }
        flushPendingCalls()

        val openAITools = tools.takeIf { it.isNotEmpty() }?.map { it.toOpenAITool() }
        val toolChoice = prompt.params.toolChoice?.toOpenAIToolChoice()
        val (modalities, audio) = buildAudionConfig(model, stream)

        return OpenAIChatCompletionRequest(
            messages = messages,
            model = model.id,
            audio = audio,
            modalities = modalities,
            numberOfChoices = model.takeIf { it.supports(LLMCapability.MultipleChoices) }
                ?.let { prompt.params.numberOfChoices },
            stream = stream,
            temperature = model.takeIf { it.supports(LLMCapability.Temperature) }
                ?.let { prompt.params.temperature },
            toolChoice = toolChoice,
            tools = openAITools,
            user = prompt.params.user,
        )
    }

    private suspend fun getOpenAIResponse(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): OpenAIChatCompletionResponse {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        model.requireCapability(LLMCapability.Completion)
        if (tools.isNotEmpty()) {
            model.requireCapability(LLMCapability.Tools)
        }

        val request = createOpenAIRequest(prompt, tools, model, false)

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post(chatCompletionsPath) {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                response.body<OpenAIChatCompletionResponse>()
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from $clientName API: ${response.status}: $errorBody" }
                error("Error from $clientName API: ${response.status}: $errorBody")
            }
        }
    }

    private fun processOpenAIResponse(response: OpenAIChatCompletionResponse): List<LLMChoice> {
        if (response.choices.isEmpty()) {
            logger.error { "Empty choices in response" }
            error("Empty choices in response")
        }

        val metaInfo = ResponseMetaInfo.create(
            clock,
            totalTokensCount = response.usage?.totalTokens,
            inputTokensCount = response.usage?.promptTokens,
            outputTokensCount = response.usage?.completionTokens
        )

        return response.choices.map { it.toMessageResponses(metaInfo) }
    }

    protected fun Message.toOpenAIMessageContent(model: LLModel): Content {
        if (this !is Message.WithAttachments || attachments.isEmpty()) {
            return Content.Text(content)
        }

        val parts = buildList {
            if (content.isNotEmpty()) {
                add(ContentPart.Text(content))
            }

            attachments.forEach { attachment ->
                when (attachment) {
                    is Attachment.Image -> {
                        model.requireCapability(LLMCapability.Vision.Image)

                        val imageUrl: String = when (val content = attachment.content) {
                            is AttachmentContent.URL -> content.url
                            is AttachmentContent.Binary -> "data:${attachment.mimeType};base64,${content.base64}"
                            else -> throw IllegalArgumentException("Unsupported image attachment content: ${content::class}")
                        }

                        add(ContentPart.Image(ContentPart.ImageUrl(imageUrl)))
                    }

                    is Attachment.Audio -> {
                        model.requireCapability(LLMCapability.Audio)

                        val inputAudio: ContentPart.InputAudio = when (val content = attachment.content) {
                            is AttachmentContent.Binary -> ContentPart.InputAudio(content.base64, attachment.format)
                            else -> throw IllegalArgumentException("Unsupported audio attachment content: ${content::class}")
                        }

                        add(ContentPart.Audio(inputAudio))
                    }

                    is Attachment.File -> {
                        model.requireCapability(LLMCapability.Document)

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

        return Content.Parts(parts)
    }

    private fun ToolDescriptor.toOpenAITool(): OpenAITool {
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                requiredParameters.forEach { param ->
                    put(param.name, param.toJsonSchema())
                }
                optionalParameters.forEach { param ->
                    put(param.name, param.toJsonSchema())
                }
            }
            putJsonArray("required") {
                requiredParameters.forEach { param -> add(param.name) }
            }
        }
        return OpenAITool(
            function = OpenAIToolFunction(
                name = name,
                description = description,
                parameters = parameters
            )
        )
    }

    private fun LLMParams.ToolChoice.toOpenAIToolChoice(): OpenAIToolChoice = when (this) {
        LLMParams.ToolChoice.Auto -> OpenAIToolChoice.Auto
        LLMParams.ToolChoice.None -> OpenAIToolChoice.None
        LLMParams.ToolChoice.Required -> OpenAIToolChoice.Required
        is LLMParams.ToolChoice.Named -> OpenAIToolChoice.Function(
            function = OpenAIToolChoice.FunctionName(name)
        )
    }

    private fun ToolParameterDescriptor.toJsonSchema(): JsonObject = buildJsonObject {
        put("description", JsonPrimitive(description))
        fillJsonSchema(type)
    }

    private fun JsonObjectBuilder.fillJsonSchema(type: ToolParameterType) {
        when (type) {
            ToolParameterType.Boolean -> put("type", "boolean")
            ToolParameterType.Float -> put("type", "number")
            ToolParameterType.Integer -> put("type", "integer")
            ToolParameterType.String -> put("type", "string")
            is ToolParameterType.Enum -> {
                put("type", "string")
                putJsonArray("enum") {
                    type.entries.forEach { entry -> add(entry) }
                }
            }

            is ToolParameterType.List -> {
                put("type", "array")
                putJsonObject("items") { fillJsonSchema(type.itemsType) }
            }

            is ToolParameterType.Object -> {
                put("type", "object")
                type.additionalProperties?.let { put("additionalProperties", it) }
                putJsonObject("properties") {
                    type.properties.forEach { property ->
                        putJsonObject(property.name) {
                            fillJsonSchema(property.type)
                            put("description", property.description)
                        }
                    }
                }
            }
        }
    }

    // TODO: allow passing this externally and actually controlling this behavior
    private fun buildAudionConfig(model: LLModel, stream: Boolean): Pair<List<OpenAIModalities>?, OpenAIAudioConfig?> {
        if (!model.supports(LLMCapability.Audio)) return null to null

        val modalities = listOf(OpenAIModalities.Text, OpenAIModalities.Audio)
        val audio = OpenAIAudioConfig(
            format = if (stream) OpenAIAudioFormat.PCM16 else OpenAIAudioFormat.WAV,
            voice = OpenAIAudioVoice.Alloy,
        )
        return modalities to audio
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun OpenAIChoice.toMessageResponses(metaInfo: ResponseMetaInfo): List<Message.Response> {
        return when {
            message is OpenAIMessage.Assistant && !message.toolCalls.isNullOrEmpty() -> {
                message.toolCalls.map { toolCall ->
                    Message.Tool.Call(
                        id = toolCall.id,
                        tool = toolCall.function.name,
                        content = toolCall.function.arguments,
                        metaInfo = metaInfo
                    )
                }
            }

            message.content != null -> listOf(
                Message.Assistant(
                    content = message.content!!.text(),
                    finishReason = finishReason,
                    metaInfo = metaInfo
                )
            )

            message is OpenAIMessage.Assistant && message.audio?.data != null -> listOf(
                Message.Assistant(
                    content = message.audio.transcript.orEmpty(),
                    attachments = listOf(
                        Attachment.Audio(
                            content = AttachmentContent.Binary.Base64(message.audio.data),
                            format = "unknown", // FIXME: clarify format from response
                        )
                    ),
                    finishReason = finishReason,
                    metaInfo = metaInfo
                )
            )

            else -> {
                logger.error { "Unexpected response from $clientName: no tool calls and no content" }
                error("Unexpected response from $clientName: no tool calls and no content")
            }
        }
    }

    protected fun LLModel.requireCapability(capability: LLMCapability) {
        require(supports(capability)) { "Model $id does not support ${capability.id}" }
    }
}