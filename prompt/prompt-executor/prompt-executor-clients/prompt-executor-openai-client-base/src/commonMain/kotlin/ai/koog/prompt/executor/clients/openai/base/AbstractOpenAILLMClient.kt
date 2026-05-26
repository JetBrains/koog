package ai.koog.prompt.executor.clients.openai.base

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.post
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
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
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.requireEndFrame
import ai.koog.utils.time.KoogClock
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.JvmOverloads
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import ai.koog.prompt.executor.clients.openai.base.models.Content as OpenAIContent

/**
 * Base settings class for OpenAI-based API clients.
 *
 * @property baseUrl The base URL for the API endpoint.
 * @property chatCompletionsPath The path for chat completions API endpoints.
 * @property timeoutConfig Configuration for connection timeouts, including request, connect, and socket timeouts.
 */
public abstract class OpenAIBaseSettings(
    public val baseUrl: String,
    public val chatCompletionsPath: String,
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
)

/**
 * Abstract base class for OpenAI-compatible LLM clients.
 * Provides common functionality for communicating with OpenAI and OpenAI-compatible APIs.
 *
 * @param settings Configuration settings including base URL, API paths, and timeout configuration.
 * @param httpClient A fully configured [KoogHttpClient] for making API requests. Must have authentication
 *   and other request defaults (base URL, timeouts, headers) already embedded. To use a factory-backed client
 *   with standard OpenAI-compatible defaults, use the secondary constructor that accepts a [KoogHttpClient.Factory]
 *   and an API key.
 * @param clock [KoogClock] used for tracking response metadata timestamps. Defaults to [KoogClock.System].
 */
public abstract class AbstractOpenAILLMClient<TResponse : OpenAIBaseLLMResponse, TStreamResponse : OpenAIBaseLLMStreamResponse>(
    settings: OpenAIBaseSettings,
    protected val httpClient: KoogHttpClient,
    protected val clock: KoogClock = KoogClock.System,
    protected val logger: KLogger,
    private val toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator,
) : LLMClient() {

    override fun getBasicJsonSchemaGenerator(): OpenAIBasicJsonSchemaGenerator {
        return OpenAIBasicJsonSchemaGenerator
    }

    override fun getStandardJsonSchemaGenerator(): OpenAIStandardJsonSchemaGenerator {
        return OpenAIStandardJsonSchemaGenerator
    }

    private val chatCompletionsPath: String = settings.chatCompletionsPath

    protected val json: Json = defaultJson

    /**
     * Secondary constructor for creating a client backed by a [KoogHttpClient.Factory].
     * Configures authentication, base URL, timeouts, and JSON serialization automatically from [apiKey] and [settings].
     */
    @JvmOverloads
    public constructor(
        apiKey: String,
        settings: OpenAIBaseSettings,
        httpClientFactory: KoogHttpClient.Factory,
        clientName: String,
        clock: KoogClock = KoogClock.System,
        logger: KLogger,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator,
    ) : this(
        settings = settings,
        httpClient = createConfiguredHttpClient(apiKey, settings, httpClientFactory, clientName),
        clock = clock,
        logger = logger,
        toolsConverter = toolsConverter
    )

    public companion object {

        private val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
            namingStrategy = JsonNamingStrategy.SnakeCase
        }

        /**
         * Creates a configured HTTP client for OpenAI-compatible provider client constructors.
         */
        public fun createConfiguredHttpClient(
            apiKey: String,
            settings: OpenAIBaseSettings,
            httpClientFactory: KoogHttpClient.Factory,
            clientName: String
        ): KoogHttpClient = httpClientFactory.create(
            clientName = clientName,
            baseUrl = settings.baseUrl,
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            queryParameters = emptyMap(),
            requestTimeoutMillis = settings.timeoutConfig.requestTimeoutMillis,
            connectTimeoutMillis = settings.timeoutConfig.connectTimeoutMillis,
            socketTimeoutMillis = settings.timeoutConfig.socketTimeoutMillis,
            json = defaultJson,
        )
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
    protected abstract fun processProviderChatResponse(response: TResponse): List<Message.Assistant>

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
     * Processes a provider-specific streaming response.
     * Must be implemented by concrete client classes.
     */
    protected abstract fun processStreamingResponse(response: Flow<TStreamResponse>): Flow<StreamFrame>

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
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

        return try {
            channelFlow {
                httpClient.sse(
                    path = chatCompletionsPath,
                    requestBody = request,
                    requestBodyType = String::class,
                    dataFilter = { it != "[DONE]" },
                    decodeStreamingResponse = ::decodeStreamingResponse,
                    processStreamingChunk = { it }
                ).collect { send(it) }
            }.let { processStreamingResponse(it) }.requireEndFrame()
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

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Assistant> {
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
        val requestBody = serializeProviderChatRequest(
            messages = messages,
            model = model,
            tools = llmTools,
            toolChoice = prompt.params.toolChoice?.toOpenAIToolChoice(),
            params = prompt.params,
            stream = false
        )

        return try {
            httpClient.post<String, String>(
                path = chatCompletionsPath,
                requestBody = requestBody
            ).let(::decodeResponse)
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

    @OptIn(ExperimentalUuidApi::class)
    protected fun convertPromptToMessages(prompt: Prompt, model: LLModel): List<OpenAIMessage> {
        return buildList {
            prompt.messages.forEach { message ->
                when (message) {
                    is Message.System -> {
                        message.parts.forEach { part ->
                            add(OpenAIMessage.System(content = OpenAIContent.Text(part.text)))
                        }
                    }

                    is Message.User -> {
                        // First add tool results
                        message.parts.filterIsInstance<MessagePart.Tool.Result>().forEach { part ->
                            add(
                                OpenAIMessage.Tool(
                                    content = OpenAIContent.Text(part.output),
                                    toolCallId = part.id ?: Uuid.random().toString()
                                )
                            )
                        }

                        // Only after add user texts if not empty
                        message.parts.filterIsInstance<MessagePart.ContentPart>().let {
                            if (it.isNotEmpty()) {
                                add(
                                    OpenAIMessage.User(content = it.toMessageContent(model))
                                )
                            }
                        }
                    }

                    is Message.Assistant -> {
                        add(
                            OpenAIMessage.Assistant(
                                content = message.parts.filterIsInstance<MessagePart.ContentPart>()
                                    // If no text, then it should be null
                                    .takeIf { it.isNotEmpty() }?.toMessageContent(model),
                                // FIXME: how to handle multiple reasoning messages
                                reasoningContent = message.parts.filterIsInstance<MessagePart.Reasoning>()
                                    .firstOrNull()?.content?.firstOrNull(),
                                toolCalls = message.parts.filterIsInstance<MessagePart.Tool.Call>()
                                    .takeIf { it.isNotEmpty() }
                                    ?.map {
                                        OpenAIToolCall(
                                            it.id ?: Uuid.random().toString(),
                                            function = OpenAIFunction(it.tool, Json.encodeToString(it.args))
                                        )
                                    }
                            )
                        )
                    }
                }
            }
        }
    }

    protected fun List<MessagePart.ContentPart>.toMessageContent(model: LLModel): OpenAIContent {
        if (this.singleOrNull() is MessagePart.Text) {
            return OpenAIContent.Text((this.singleOrNull() as MessagePart.Text).text)
        }

        return OpenAIContent.Parts(this.map { part -> part.toOpenAIContentPart(model) })
    }

    private fun MessagePart.ContentPart.toOpenAIContentPart(model: LLModel): OpenAIContentPart = when (this) {
        is MessagePart.Text -> {
            OpenAIContentPart.Text(text)
        }

        is MessagePart.Attachment -> {
            when (source) {
                is AttachmentSource.Image -> {
                    model.requireCapability(LLMCapability.Vision.Image)
                    val imageUrl = when (val attachmentContent = source.content) {
                        is AttachmentContent.URL -> attachmentContent.url
                        is AttachmentContent.Binary -> "data:${source.mimeType};base64,${attachmentContent.asBase64()}"
                        else -> throw LLMClientException(
                            clientName,
                            "Unsupported image attachment content: ${attachmentContent::class}"
                        )
                    }
                    OpenAIContentPart.Image(OpenAIContentPart.ImageUrl(imageUrl))
                }

                is AttachmentSource.Audio -> {
                    model.requireCapability(LLMCapability.Audio)
                    val inputAudio = when (val attachmentContent = source.content) {
                        is AttachmentContent.Binary -> OpenAIContentPart.InputAudio(
                            attachmentContent.asBase64(),
                            source.format
                        )

                        else -> throw LLMClientException(
                            clientName,
                            "Unsupported audio attachment content: ${attachmentContent::class}"
                        )
                    }
                    OpenAIContentPart.Audio(inputAudio)
                }

                is AttachmentSource.File -> {
                    model.requireCapability(LLMCapability.Document)
                    when (val attachmentContent = source.content) {
                        is AttachmentContent.Binary -> {
                            val fileData = OpenAIContentPart.FileData(
                                fileData = "data:${source.mimeType};base64,${attachmentContent.asBase64()}",
                                filename = source.fileName
                            )
                            OpenAIContentPart.File(fileData)
                        }

                        is AttachmentContent.PlainText -> {
                            OpenAIContentPart.Text(attachmentContent.text)
                        }

                        else -> throw LLMClientException(
                            clientName,
                            "Unsupported file attachment content: ${attachmentContent::class}"
                        )
                    }
                }

                else -> throw LLMClientException(clientName, "Unsupported attachment type: $this")
            }
        }
    }

    protected fun ToolDescriptor.toOpenAIChatTool(): OpenAITool = OpenAITool(
        function = OpenAIToolFunction(
            name = name,
            description = description,
            parameters = toolsConverter.generate(this)
        )
    )

    protected fun LLMParams.ToolChoice.toOpenAIToolChoice(): OpenAIToolChoice = when (this) {
        LLMParams.ToolChoice.Auto -> OpenAIToolChoice.Auto
        LLMParams.ToolChoice.None -> OpenAIToolChoice.None
        LLMParams.ToolChoice.Required -> OpenAIToolChoice.Required
        is LLMParams.ToolChoice.Named -> OpenAIToolChoice.Function(
            function = OpenAIToolChoice.FunctionName(name)
        )
    }

    private fun OpenAIMessage.Assistant.reasoningPartOrNull(): MessagePart.Reasoning? =
        if (!reasoningContent.isNullOrBlank()) {
            MessagePart.Reasoning(
                content = listOf(reasoningContent),
            )
        } else {
            null
        }

    private fun OpenAIMessage.Assistant.toolCallPartsOrNull(): List<MessagePart.Tool.Call> =
        toolCalls?.map { toolCall ->
            MessagePart.Tool.Call(
                id = toolCall.id,
                tool = toolCall.function.name,
                /*
                 If the tool has no arguments, OpenRouter puts an empty string in the arguments instead of an empty object
                 But we always expect arguments to be a JSON object. Fixing this.
                 */
                args = toolCall.function.arguments
                    .takeIf { it.isNotEmpty() }
                    ?.let { Json.parseToJsonElement(it).jsonObject }
                    ?: JsonObject(mapOf()),
            )
        } ?: emptyList()

    private fun OpenAIMessage.Assistant.contentPartsOrNull(): List<MessagePart.ResponsePart> =
        buildList {
            if (content != null) {
                when (content) {
                    is OpenAIContent.Text -> {
                        if (content.value.isNotBlank()) {
                            add(MessagePart.Text(content.value))
                        }
                    }

                    is OpenAIContent.Parts -> {
                        content.value.forEach { part ->
                            when (part) {
                                is OpenAIContentPart.Text -> {
                                    if (part.text.isNotBlank()) {
                                        add(MessagePart.Text(part.text))
                                    }
                                }

                                else -> throw LLMClientException(
                                    "Unsupported content part type: ${part::class} in assistant message"
                                )
                            }
                        }
                    }
                }
            }
            if (audio?.data != null) {
                audio.transcript?.let { add(MessagePart.Text(it)) }
                add(
                    MessagePart.Attachment(
                        source = AttachmentSource.Audio(
                            content = AttachmentContent.Binary.Base64(audio.data),
                            format = "unknown", // FIXME: clarify format from response
                        )
                    )
                )
            }
        }

    @OptIn(ExperimentalEncodingApi::class)
    protected fun OpenAIMessage.toMessageResponse(
        finishReason: String?,
        metaInfo: ResponseMetaInfo
    ): Message.Assistant {
        check(this is OpenAIMessage.Assistant) { "Expected OpenAIMessage.Assistant, got $this" }

        val message = this
        val parts: List<MessagePart.ResponsePart> = buildList {
            message.contentPartsOrNull().forEach { add(it) }
            message.reasoningPartOrNull()?.let { add(it) }
            message.toolCallPartsOrNull().forEach { add(it) }
        }

        return Message.Assistant(
            parts = parts,
            metaInfo = metaInfo,
            finishReason = finishReason
        )
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

    protected open fun createResponseFormat(schema: LLMParams.Schema?, model: LLModel): OpenAIResponseFormat? {
        return schema?.let {
            require(model.supports(it.capability)) {
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
        httpClient.close()
    }
}
