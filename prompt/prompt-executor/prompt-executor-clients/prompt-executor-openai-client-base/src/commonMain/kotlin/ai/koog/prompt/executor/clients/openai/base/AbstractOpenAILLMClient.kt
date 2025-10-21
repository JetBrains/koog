package ai.koog.prompt.executor.clients.openai.base

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.fromKtorClient
import ai.koog.http.client.post
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.JsonSchemaObject
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMStreamResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIContentPart
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIFunction
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIResponseFormat
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolCall
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolFunction
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import ai.koog.prompt.executor.clients.openai.base.structure.OpenAIBasicJsonSchemaGenerator
import ai.koog.prompt.executor.clients.openai.base.structure.OpenAIStandardJsonSchemaGenerator
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.StreamFrameFlowBuilder
import ai.koog.prompt.streaming.buildStreamFrameFlow
import ai.koog.prompt.structure.RegisteredBasicJsonSchemaGenerators
import ai.koog.prompt.structure.RegisteredStandardJsonSchemaGenerators
import ai.koog.prompt.structure.annotations.InternalStructuredOutputApi
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Base settings class for OpenAI-based API clients.
 *
 * @property baseUrl The base URL for the API endpoint.
 * @property chatCompletionsPath The path for chat completions API endpoints.
 * @property timeoutConfig Configuration for connection timeouts, including request, connect, and socket timeouts.
 */
public abstract class OpenAIBasedSettings(
    public val baseUrl: String,
    public val chatCompletionsPath: String,
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
)

/**
 * Abstract base class for OpenAI-compatible LLM clients.
 * Provides common functionality for communicating with OpenAI and OpenAI-compatible APIs.
 *
 * @param apiKey The API key for authentication with the OpenAI-compatible API.
 * @param settings Configuration settings including base URL, API paths, and timeout configuration.
 * @param baseClient The HTTP client to use for API requests. Defaults to a new HttpClient instance.
 * @param clock Clock instance used for tracking response metadata timestamps. Defaults to Clock.System.
 */
public abstract class AbstractOpenAILLMClient<TResponse : OpenAIBaseLLMResponse, TStreamResponse : OpenAIBaseLLMStreamResponse>(
    private val apiKey: String,
    settings: OpenAIBasedSettings,
    private val baseClient: HttpClient = HttpClient(),
    protected val clock: Clock = Clock.System,
    protected val logger: KLogger
) : LLMClient {

    protected companion object {

        /**
         * Register basic and standard openai json schema generator for given provider
         */
        @Suppress("RedundantVisibilityModifier") // it is required here due to explicitApi
        @OptIn(InternalStructuredOutputApi::class)
        public fun registerOpenAIJsonSchemaGenerators(llmProvider: LLMProvider) {
            RegisteredBasicJsonSchemaGenerators[llmProvider] = OpenAIBasicJsonSchemaGenerator
            RegisteredStandardJsonSchemaGenerators[llmProvider] = OpenAIStandardJsonSchemaGenerator
        }
    }

    protected open val clientName: String = this::class.simpleName ?: "UnknownClient"

    private val chatCompletionsPath: String = settings.chatCompletionsPath

    protected val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    protected val httpClient: KoogHttpClient = KoogHttpClient.fromKtorClient(
        clientName = clientName,
        logger = logger,
        baseClient = baseClient
    ) {
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

    /**
     * Creates a provider-specific request from the common parameters.
     * Must be implemented by concrete client classes.
     */
    @Suppress("LongParameterList")
    protected abstract fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean,
    ): String

    /**
     * Processes a provider-specific response into a common message format.
     * Must be implemented by concrete client classes.
     */
    protected abstract fun processProviderChatResponse(response: TResponse): List<LLMChoice>

    /**
     * Decodes a streaming response from JSON string.
     * Must be implemented by concrete client classes.
     */
    protected abstract fun decodeStreamingResponse(data: String): TStreamResponse

    /**
     * Decodes a regular response from JSON string.
     * Must be implemented by concrete client classes.
     */
    protected abstract fun decodeResponse(data: String): TResponse

    /**
     * Processes a provider-specific streaming response chunk.
     * Must be implemented by concrete client classes.
     */
    protected abstract suspend fun StreamFrameFlowBuilder.processStreamingChunk(chunk: TStreamResponse)

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        val response = getResponse(prompt, model, tools)
        return processProviderChatResponse(response).first()
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        model.requireCapability(LLMCapability.Completion)

        val messages = convertPromptToMessages(prompt, model)
        val request = serializeProviderChatRequest(
            messages = messages,
            model = model,
            tools = tools.map { it.toOpenAIChatTool() },
            toolChoice = prompt.params.toolChoice?.toOpenAIToolChoice(),
            params = prompt.params,
            stream = true
        )

        return buildStreamFrameFlow {
            httpClient.sse(
                path = chatCompletionsPath,
                request = request,
                requestBodyType = String::class,
                dataFilter = { it != "[DONE]" },
                decodeStreamingResponse = ::decodeStreamingResponse,
                processStreamingChunk = { it }
            ).collect {
                processStreamingChunk(it)
            }
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> {
        model.requireCapability(LLMCapability.MultipleChoices)

        return processProviderChatResponse(getResponse(prompt, model, tools))
    }

    private suspend fun getResponse(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): TResponse {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        model.requireCapability(LLMCapability.Completion)
        if (tools.isNotEmpty()) {
            model.requireCapability(LLMCapability.Tools)
        }

        val llmTools = tools.takeIf { it.isNotEmpty() }?.map { it.toOpenAIChatTool() }
        val messages = convertPromptToMessages(prompt, model)
        val request = serializeProviderChatRequest(
            messages = messages,
            model = model,
            tools = llmTools,
            toolChoice = prompt.params.toolChoice?.toOpenAIToolChoice(),
            params = prompt.params,
            stream = false
        )

        return httpClient.post<String, String>(
            path = chatCompletionsPath,
            request = request
        ).let(::decodeResponse)
    }

    @OptIn(ExperimentalUuidApi::class)
    protected fun convertPromptToMessages(prompt: Prompt, model: LLModel): List<OpenAIMessage> {
        val messages = mutableListOf<OpenAIMessage>()
        val pendingCalls = mutableListOf<OpenAIToolCall>()

        fun flushPendingCalls() {
            if (pendingCalls.isNotEmpty()) {
                messages += OpenAIMessage.Assistant(toolCalls = pendingCalls.toList())
                pendingCalls.clear()
            }
        }

        prompt.messages.forEach { message ->
            when (message) {
                is Message.System -> {
                    flushPendingCalls()
                    messages += OpenAIMessage.System(content = Content.Text(message.content))
                }

                is Message.User -> {
                    flushPendingCalls()
                    messages += OpenAIMessage.User(content = message.toMessageContent(model))
                }

                is Message.Assistant -> {
                    flushPendingCalls()
                    messages += OpenAIMessage.Assistant(content = Content.Text(message.content))
                }

                is Message.Tool.Result -> {
                    flushPendingCalls()
                    messages += OpenAIMessage.Tool(
                        content = Content.Text(message.content),
                        toolCallId = message.id ?: Uuid.random().toString()
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
        flushPendingCalls()

        return messages
    }

    protected fun Message.toMessageContent(model: LLModel): Content {
        if (this !is Message.WithAttachments || attachments.isEmpty()) {
            return Content.Text(content)
        }

        val parts = buildList {
            if (content.isNotEmpty()) {
                add(OpenAIContentPart.Text(content))
            }
            attachments.forEach { attachment -> add(attachment.toContentPart(model)) }
        }

        return Content.Parts(parts)
    }

    private fun Attachment.toContentPart(model: LLModel): OpenAIContentPart = when (this) {
        is Attachment.Image -> {
            model.requireCapability(LLMCapability.Vision.Image)
            val imageUrl = when (val attachmentContent = content) {
                is AttachmentContent.URL -> attachmentContent.url
                is AttachmentContent.Binary -> "data:$mimeType;base64,${attachmentContent.asBase64()}"
                else -> throw IllegalArgumentException("Unsupported image attachment content: ${attachmentContent::class}")
            }
            OpenAIContentPart.Image(OpenAIContentPart.ImageUrl(imageUrl))
        }

        is Attachment.Audio -> {
            model.requireCapability(LLMCapability.Audio)
            val inputAudio = when (val attachmentContent = content) {
                is AttachmentContent.Binary -> OpenAIContentPart.InputAudio(attachmentContent.asBase64(), format)
                else -> throw IllegalArgumentException("Unsupported audio attachment content: ${attachmentContent::class}")
            }
            OpenAIContentPart.Audio(inputAudio)
        }

        is Attachment.File -> {
            model.requireCapability(LLMCapability.Document)
            when (val attachmentContent = content) {
                is AttachmentContent.Binary -> {
                    val fileData = OpenAIContentPart.FileData(
                        fileData = "data:$mimeType;base64,${attachmentContent.asBase64()}",
                        filename = fileName
                    )
                    OpenAIContentPart.File(fileData)
                }

                is AttachmentContent.PlainText -> {
                    OpenAIContentPart.Text(attachmentContent.text)
                }

                else -> throw IllegalArgumentException("Unsupported file attachment content: ${attachmentContent::class}")
            }
        }

        else -> throw IllegalArgumentException("Unsupported attachment type: $this")
    }

    protected fun ToolDescriptor.toOpenAIChatTool(): OpenAITool = OpenAITool(
        function = OpenAIToolFunction(
            name = name,
            description = description,
            parameters = paramsToJsonObject()
        )
    )

    protected fun ToolDescriptor.paramsToJsonObject(): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                (requiredParameters + optionalParameters).forEach { param ->
                    put(param.name, param.toJsonSchema())
                }
            }
            putJsonArray("required") {
                requiredParameters.forEach { param -> add(param.name) }
            }
        }

    protected fun LLMParams.ToolChoice.toOpenAIToolChoice(): OpenAIToolChoice = when (this) {
        LLMParams.ToolChoice.Auto -> OpenAIToolChoice.Auto
        LLMParams.ToolChoice.None -> OpenAIToolChoice.None
        LLMParams.ToolChoice.Required -> OpenAIToolChoice.Required
        is LLMParams.ToolChoice.Named -> OpenAIToolChoice.Function(
            function = OpenAIToolChoice.FunctionName(name)
        )
    }

    protected fun ToolParameterDescriptor.toJsonSchema(): JsonObject = buildJsonObject {
        put("description", description)
        fillJsonSchema(type)
    }

    private fun JsonObjectBuilder.fillJsonSchema(type: ToolParameterType) {
        when (type) {
            ToolParameterType.Boolean -> put("type", "boolean")
            ToolParameterType.Float -> put("type", "number")
            ToolParameterType.Integer -> put("type", "integer")
            ToolParameterType.String -> put("type", "string")
            ToolParameterType.Null -> put("type", "null")
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

            is ToolParameterType.AnyOf -> {
                putJsonArray("anyOf") {
                    addAll(
                        type.types.map { parameterType ->
                            parameterType.toJsonSchema()
                        }
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    protected fun OpenAIMessage.toMessageResponses(finishReason: String?, metaInfo: ResponseMetaInfo): List<Message.Response> {
        return when {
            this is OpenAIMessage.Assistant && !this.toolCalls.isNullOrEmpty() -> {
                this.toolCalls.map { toolCall ->
                    Message.Tool.Call(
                        id = toolCall.id,
                        tool = toolCall.function.name,
                        /*
                         If the tool has no arguments, OpenRouter puts an empty string in the arguments instead of an empty object
                         But we always expect arguments to be a JSON object. Fixing this.
                         */
                        content = toolCall.function.arguments
                            .takeIf { it.isNotEmpty() }
                            ?: "{}",
                        metaInfo = metaInfo
                    )
                }
            }

            this.content != null -> listOf(
                Message.Assistant(
                    content = this.content!!.text(),
                    finishReason = finishReason,
                    metaInfo = metaInfo
                )
            )

            this is OpenAIMessage.Assistant && this.audio?.data != null -> listOf(
                Message.Assistant(
                    content = this.audio.transcript.orEmpty(),
                    attachments = listOf(
                        Attachment.Audio(
                            content = AttachmentContent.Binary.Base64(this.audio.data),
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

    protected fun LLModel.requireCapability(capability: LLMCapability, message: String? = null) {
        require(supports(capability)) {
            "Model $id does not support ${capability.id}" + (message?.let { ": $it" } ?: "")
        }
    }

    /**
     * Creates ResponseMetaInfo from usage data.
     * Should be used by concrete implementations when processing responses.
     */
    protected fun createMetaInfo(usage: OpenAIUsage?): ResponseMetaInfo = ResponseMetaInfo.create(
        clock,
        totalTokensCount = usage?.totalTokens,
        inputTokensCount = usage?.promptTokens,
        outputTokensCount = usage?.completionTokens
    )

    protected fun createResponseFormat(schema: LLMParams.Schema?, model: LLModel): OpenAIResponseFormat? {
        return schema?.let {
            require(it.capability in model.capabilities) {
                "Model ${model.id} does not support structured output schema ${it.name}"
            }
            when (it) {
                is LLMParams.Schema.JSON -> OpenAIResponseFormat.JsonSchema(
                    JsonSchemaObject(
                        name = it.name,
                        schema = it.schema,
                        strict = true
                    )
                )
            }
        }
    }

    override fun close() {
        baseClient.close()
    }
}
