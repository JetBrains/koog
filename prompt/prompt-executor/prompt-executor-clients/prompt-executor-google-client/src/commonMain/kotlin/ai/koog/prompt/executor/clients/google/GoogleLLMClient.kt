package ai.koog.prompt.executor.clients.google

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.fromKtorClient
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.google.models.GoogleCandidate
import ai.koog.prompt.executor.clients.google.models.GoogleContent
import ai.koog.prompt.executor.clients.google.models.GoogleData
import ai.koog.prompt.executor.clients.google.models.GoogleEmbeddingBatchRequest
import ai.koog.prompt.executor.clients.google.models.GoogleEmbeddingBatchResponse
import ai.koog.prompt.executor.clients.google.models.GoogleEmbeddingRequest
import ai.koog.prompt.executor.clients.google.models.GoogleEmbeddingResponse
import ai.koog.prompt.executor.clients.google.models.GoogleFunctionCallingConfig
import ai.koog.prompt.executor.clients.google.models.GoogleFunctionCallingMode
import ai.koog.prompt.executor.clients.google.models.GoogleFunctionDeclaration
import ai.koog.prompt.executor.clients.google.models.GoogleGenerationConfig
import ai.koog.prompt.executor.clients.google.models.GoogleModelsResponse
import ai.koog.prompt.executor.clients.google.models.GooglePart
import ai.koog.prompt.executor.clients.google.models.GoogleRequest
import ai.koog.prompt.executor.clients.google.models.GoogleResponse
import ai.koog.prompt.executor.clients.google.models.GoogleTool
import ai.koog.prompt.executor.clients.google.models.GoogleToolConfig
import ai.koog.prompt.executor.clients.google.structure.GoogleBasicJsonSchemaGenerator
import ai.koog.prompt.executor.clients.google.structure.GoogleResponseFormat
import ai.koog.prompt.executor.clients.google.structure.GoogleStandardJsonSchemaGenerator
import ai.koog.prompt.executor.clients.modelsById
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.buildStreamFrameFlow
import ai.koog.prompt.streaming.requireEndFrame
import ai.koog.prompt.structure.annotations.InternalStructuredOutputApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.jvm.JvmOverloads
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Configuration settings for the Google AI client.
 *
 * @property baseUrl The base URL for the Google AI API.
 * @property timeoutConfig Timeout configuration for API requests.
 * @property fallbackThoughtSignature Default `thought_signature` used for thinking models
 */
public class GoogleClientSettings(
    public val baseUrl: String = "https://generativelanguage.googleapis.com",
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    public val defaultPath: String = "v1beta/models",
    public val generateContentMethod: String = "generateContent",
    public val streamGenerateContentMethod: String = "streamGenerateContent",
    public val embedContentMethod: String = "embedContent",
    public val batchEmbedContentsMethod: String = "batchEmbedContents",
    public val fallbackThoughtSignature: String = "context_engineering_is_the_way_to_go",
)

/**
 * Implementation of [LLMClient] for Google's Gemini API.
 *
 * This client supports both standard and streaming text generation with
 * optional tool calling capabilities.
 *
 * @param settings Custom client settings, defaults to standard API endpoint and timeouts
 * @param httpClient A preconfigured Koog HTTP client used for API calls. Must have authentication and other
 *   request defaults already embedded. To use a Ktor-backed client with standard defaults, use the secondary
 *   constructor that accepts an API key and an [io.ktor.client.HttpClient].
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public open class GoogleLLMClient @JvmOverloads constructor(
    private val settings: GoogleClientSettings = GoogleClientSettings(),
    private val httpClient: KoogHttpClient,
    private val clock: Clock = Clock.System
) : LLMClient() {

    /**
     * Secondary constructor for creating a GoogleLLMClient backed with a Ktor HTTP client.
     *
     * @param apiKey The API key for the Google AI API
     * @param settings Custom client settings, defaults to standard API endpoint and timeouts
     * @param baseClient Ktor HTTP client used for making API requests.
     * @param clock Clock instance used for tracking response metadata timestamps.
     */
    @JvmOverloads
    public constructor(
        apiKey: String,
        settings: GoogleClientSettings = GoogleClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System
    ) : this(
        settings,
        createConfiguredHttpClient(apiKey, settings, baseClient),
        clock
    )

    @OptIn(InternalStructuredOutputApi::class)
    private companion object {
        private const val GOOGLE_CLIENT_NAME = "GoogleLLMClient"

        private val logger = KotlinLogging.logger { }
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        }

        private fun createConfiguredHttpClient(
            apiKey: String,
            settings: GoogleClientSettings,
            baseClient: HttpClient = HttpClient()
        ): KoogHttpClient = KoogHttpClient.fromKtorClient(
            clientName = GOOGLE_CLIENT_NAME,
            logger = logger,
            baseClient = baseClient,
            baseUrl = settings.baseUrl,
            requestTimeoutMillis = settings.timeoutConfig.requestTimeoutMillis,
            connectTimeoutMillis = settings.timeoutConfig.connectTimeoutMillis,
            socketTimeoutMillis = settings.timeoutConfig.socketTimeoutMillis,
            json = json,
            queryParameters = mapOf("key" to apiKey),
        )
    }

    override fun getBasicJsonSchemaGenerator(): GoogleBasicJsonSchemaGenerator {
        return GoogleBasicJsonSchemaGenerator
    }

    override fun getStandardJsonSchemaGenerator(): GoogleStandardJsonSchemaGenerator {
        return GoogleStandardJsonSchemaGenerator
    }

    /**
     * Provides the Large Language Model (LLM) provider associated with this client.
     *
     * @return The LLM provider, which is Google for this implementation.
     */
    override val clientName: String = GOOGLE_CLIENT_NAME

    override fun llmProvider(): LLMProvider = LLMProvider.Google

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        require(model.supports(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.supports(LLMCapability.Tools) || tools.isEmpty()) {
            "Model ${model.id} does not support tools"
        }

        val response = getGoogleResponse(prompt, model, tools)
        return processGoogleResponse(response).first()
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = buildStreamFrameFlow {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        require(model.supports(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        val request = createGoogleRequest(prompt, model, tools)

        try {
            httpClient.sse(
                path = "${settings.defaultPath}/${model.id}:${settings.streamGenerateContentMethod}",
                request = request,
                requestBodyType = GoogleRequest::class,
                dataFilter = { it != "[DONE]" },
                decodeStreamingResponse = { json.decodeFromString<GoogleResponse>(it) },
                parameters = mapOf("alt" to "sse"),
                processStreamingChunk = { it }
            ).collect { response ->
                val meta = response.usageMetadata?.let {
                    ResponseMetaInfo.create(
                        clock = clock,
                        totalTokensCount = it.totalTokenCount,
                        inputTokensCount = it.promptTokenCount,
                        outputTokensCount = it.candidatesTokenCount,
                    )
                }
                response.candidates.firstOrNull()?.let { candidate ->
                    candidate.content?.parts?.forEachIndexed { index, part ->
                        when (part) {
                            is GooglePart.FunctionCall -> {
                                emitToolCallDelta(
                                    id = part.functionCall.id,
                                    name = part.functionCall.name,
                                    args = part.functionCall.args?.toString() ?: "{}",
                                    index = index
                                )
                            }

                            is GooglePart.Text -> {
                                emitTextDelta(part.text, index)
                            }

                            else -> Unit
                        }
                    }
                    candidate.finishReason?.let { emitEnd(it, meta) }
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
    }.requireEndFrame()

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> {
        logger.debug { "Executing prompt with multiple choices: $prompt with tools: $tools and model: $model" }
        require(model.supports(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.supports(LLMCapability.Tools) || tools.isEmpty()) {
            "Model ${model.id} does not support tools"
        }
        require(model.supports(LLMCapability.MultipleChoices)) {
            "Model ${model.id} does not support multiple choices"
        }

        return processGoogleResponse(getGoogleResponse(prompt, model, tools))
    }

    /**
     * Gets a response from the Google AI API.
     *
     * @param prompt The prompt to execute
     * @param model The model to use
     * @param tools The tools to include in the request
     * @return The raw response from the Google AI API
     */
    private suspend fun getGoogleResponse(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): GoogleResponse {
        val request = createGoogleRequest(prompt, model, tools)

        try {
            httpClient.post(
                path = "${settings.defaultPath}/${model.id}:${settings.generateContentMethod}",
                request = request,
                requestBodyType = GoogleRequest::class,
                responseType = GoogleResponse::class,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException(
                clientName = clientName,
                message = e.message,
                cause = e
            )
        }.let { response ->

            // https://discuss.ai.google.dev/t/gemini-2-5-pro-with-empty-response-text/81175/219
            if (response.candidates.isNotEmpty() && response.candidates.all { it.content?.parts?.isEmpty() == true }) {
                logger.warn { "Content `parts` field is missing in the response from GoogleAI API: $response" }
            }

            return response
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
    internal fun createGoogleRequest(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): GoogleRequest {
        val systemMessageParts = mutableListOf<GooglePart.Text>()
        val contents = mutableListOf<GoogleContent>()
        val pendingCalls = mutableListOf<GooglePart.FunctionCall>()
        val pendingResults = mutableListOf<GooglePart.FunctionResponse>()
        var lastThoughtSignature: String? = null

        fun flushCalls() {
            if (pendingCalls.isNotEmpty()) {
                require(pendingCalls.size == pendingResults.size) {
                    "${pendingCalls.size} tool calls should be followed by ${pendingCalls.size} tool results," +
                        " but ${pendingResults.size} are present." +
                        "Message order: " + prompt.messages.map { it::class.simpleName }
                }
                //  If the model generates parallel function calls in a response, the thought_signature is attached
                //  only to the first functionCall part.
                //  Subsequent functionCall parts in the same response will not contain a signature.
                // See https://ai.google.dev/gemini-api/docs/thought-signatures#function-calling
                pendingCalls[0] = pendingCalls[0].copy(thoughtSignature = lastThoughtSignature)
                // Mark thought signature as consumed
                lastThoughtSignature = null
                contents += GoogleContent(role = "model", parts = pendingCalls.toList())
                contents += GoogleContent(role = "user", parts = pendingResults.toList())
                pendingCalls.clear()
                pendingResults.clear()
            }
        }

        fun verifyReasoningLocation() {
            require(lastThoughtSignature == null) {
                "The order of messages is incorrect, " +
                    "reasoning messages should appear before assistant and tool call messages. Current order: " +
                    prompt.messages.map { it::class.simpleName }
            }
        }

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    systemMessageParts.add(GooglePart.Text(message.content))
                }

                is Message.User -> {
                    flushCalls()
                    verifyReasoningLocation()
                    // User messages become 'user' role content
                    contents.add(message.toGoogleContent(model))
                }

                is Message.Assistant -> {
                    flushCalls()
                    contents.add(
                        GoogleContent(
                            role = "model",
                            parts = listOf(
                                GooglePart.Text(
                                    message.content,
                                    // If the thought signature was not consumed by a tool call,
                                    // it should be placed on the "model" message
                                    // https://ai.google.dev/gemini-api/docs/thought-signatures#textin-context_reasoning_no_validation
                                    thoughtSignature = lastThoughtSignature
                                )
                            )
                        )
                    )
                    lastThoughtSignature = null
                }

                is Message.Reasoning -> {
                    flushCalls()
                    verifyReasoningLocation()
                    lastThoughtSignature = message.encrypted
                    if (message.content != "") {
                        contents.add(
                            GoogleContent(
                                role = "assistant",
                                parts = listOf(
                                    GooglePart.Text(
                                        text = message.content,
                                        // Thought signatures belong in the functionalCall part or model part, not the reasoning message.
                                        // See https://ai.google.dev/gemini-api/docs/thought-signatures#how_it_works
                                        thoughtSignature = null,
                                        thought = true,
                                    )
                                )
                            )
                        )
                    }
                }

                is Message.Tool.Result -> {
                    // Tool results should be grouped into one response, each in its own GooglePart.
                    pendingResults += GooglePart.FunctionResponse(
                        functionResponse = GoogleData.FunctionResponse(
                            id = message.id,
                            name = message.tool,
                            response = buildJsonObject { put("result", message.content) }
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

        val googleParams = prompt.params.toGoogleParams()

        val responseFormat: GoogleResponseFormat? = googleParams.schema?.let { schema ->
            require(model.supports(schema.capability)) {
                "Model ${model.id} does not support structured output schema ${schema.name}"
            }

            @Suppress("REDUNDANT_ELSE_IN_WHEN") // if more formats are added later
            when (schema) {
                is LLMParams.Schema.JSON.Basic -> GoogleResponseFormat(
                    responseMimeType = "application/json",
                    responseSchema = schema.schema,
                )

                is LLMParams.Schema.JSON.Standard -> GoogleResponseFormat(
                    responseMimeType = "application/json",
                    responseJsonSchema = schema.schema,
                )

                else -> throw IllegalArgumentException("Unsupported schema type: $schema")
            }
        }

        val generationConfig = GoogleGenerationConfig(
            responseMimeType = responseFormat?.responseMimeType,
            responseSchema = responseFormat?.responseSchema,
            responseJsonSchema = responseFormat?.responseJsonSchema,
            maxOutputTokens = googleParams.maxTokens,
            temperature = if (model.supports(LLMCapability.Temperature)) googleParams.temperature else null,
            candidateCount = if (model.supports(LLMCapability.MultipleChoices)) googleParams.numberOfChoices else null,
            topP = googleParams.topP,
            topK = googleParams.topK,
            thinkingConfig = googleParams.thinkingConfig,
            additionalProperties = googleParams.additionalProperties
        )

        val functionCallingConfig = when (val toolChoice = googleParams.toolChoice) {
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
            parts.forEach { part ->
                when (part) {
                    is ContentPart.Text -> {
                        add(GooglePart.Text(part.text))
                    }

                    is ContentPart.Image -> {
                        require(model.supports(LLMCapability.Vision.Image)) {
                            "Model ${model.id} does not support images"
                        }

                        val blob: GoogleData.Blob = when (val content = part.content) {
                            is AttachmentContent.Binary -> GoogleData.Blob(part.mimeType, content.asBytes())

                            else -> throw IllegalArgumentException(
                                "Unsupported image attachment content: ${content::class}"
                            )
                        }

                        add(GooglePart.InlineData(blob))
                    }

                    is ContentPart.Audio -> {
                        require(model.supports(LLMCapability.Audio)) {
                            "Model ${model.id} does not support audio"
                        }

                        val blob: GoogleData.Blob = when (val content = part.content) {
                            is AttachmentContent.Binary -> GoogleData.Blob(part.mimeType, content.asBytes())

                            else -> throw IllegalArgumentException(
                                "Unsupported audio attachment content: ${content::class}"
                            )
                        }

                        add(GooglePart.InlineData(blob))
                    }

                    is ContentPart.File -> {
                        require(model.supports(LLMCapability.Document)) {
                            "Model ${model.id} does not support documents"
                        }

                        val blob: GoogleData.Blob = when (val content = part.content) {
                            is AttachmentContent.Binary -> GoogleData.Blob(part.mimeType, content.asBytes())

                            else -> throw IllegalArgumentException(
                                "Unsupported file attachment content: ${content::class}"
                            )
                        }

                        add(GooglePart.InlineData(blob))
                    }

                    is ContentPart.Video -> {
                        require(model.supports(LLMCapability.Vision.Video)) {
                            "Model ${model.id} does not support video"
                        }

                        val blob: GoogleData.Blob = when (val content = part.content) {
                            is AttachmentContent.Binary -> GoogleData.Blob(part.mimeType, content.asBytes())

                            else -> throw IllegalArgumentException(
                                "Unsupported video attachment content: ${content::class}"
                            )
                        }

                        add(GooglePart.InlineData(blob))
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

                ToolParameterType.Null -> put("type", "null")

                is ToolParameterType.Enum -> {
                    put("type", "string")
                    putJsonArray("enum") { type.entries.forEach { add(it) } }
                }

                is ToolParameterType.List -> {
                    put("type", "array")
                    put("items", buildJsonObject { putType(type.itemsType) })
                }

                is ToolParameterType.AnyOf -> {
                    put(
                        "anyOf",
                        buildJsonArray {
                            addAll(
                                type.types.map { parameterType ->
                                    buildGoogleParamType(parameterType)
                                }
                            )
                        }
                    )
                }

                is ToolParameterType.Object -> {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            type.properties.forEach { property ->
                                put(
                                    property.name,
                                    buildJsonObject {
                                        putType(property.type)
                                        put("description", property.description)
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }

        putType(param.type)
    }

    /**
     * Processes a single Google AI API candidate into internal message format.
     *
     * @param candidate The candidate from the Google AI API response
     * @param metaInfo The metadata for the response
     * @return A list of response messages
     */
    @OptIn(ExperimentalUuidApi::class)
    internal fun processGoogleCandidate(
        candidate: GoogleCandidate,
        metaInfo: ResponseMetaInfo
    ): List<Message.Response> {
        val parts = candidate.content?.parts.orEmpty()
        val responses = mutableListOf<Message.Response>()
        with(responses) {
            val thoughtSignature = parts.find { it.thoughtSignature != null }?.thoughtSignature

            // The Gemini API may provide thought signatures even when no reasoning is requested, and it is required
            // to keep that information in subsequent steps
            // See https://ai.google.dev/gemini-api/docs/thought-signatures#faqs
            // There are basically three options:
            // A) No reasoning is present and no thought signature is provided. In this case we have no Reasoning message.
            // B) No reasoning is present but a thought signature is provided. This is the case below, and we have a single
            // Reasoning message with content = "" and the thought signature included.
            // C) Reasoning is present. In this case we have a Reasoning message with the thought summary (content field),
            // and the thought signature is provided in the encrypted field.
            //
            // The "encrypted" field was originally designed for the OpenAI API for statelessly providing GPT models
            // with the reasoning tokens, but Google (in Gemini 2.5 somewhat, but mainly in Gemini 3 Pro) later used
            // encrypted reasoning tokens as part of the context as the norm (calling it a "thought signature"),
            // so considering it is essentially the same thing it fits into the same field.
            //
            // It's also worth pointing out that the Gemini API will provide at most one thought signature per
            // response ("per step"), even when multiple tool calls are requested ("parallel tool calls")
            // so the logic here of putting exactly one Reasoning message holds.
            if (parts.none { it.thought == true }) {
                add(
                    Message.Reasoning(
                        encrypted = thoughtSignature,
                        content = "",
                        metaInfo = metaInfo
                    )
                )
            }

            parts.forEach { part ->
                when (part) {
                    is GooglePart.Text -> {
                        if (part.thought ?: false) {
                            add(
                                Message.Reasoning(
                                    // Google does not provide thought signature (the encrypted CoT) in thought = true parts, but
                                    // just as part of the tool call or assistant message
                                    encrypted = thoughtSignature,
                                    content = part.text,
                                    metaInfo = metaInfo
                                )
                            )
                        } else {
                            add(
                                Message.Assistant(
                                    content = part.text,
                                    finishReason = candidate.finishReason,
                                    metaInfo = metaInfo
                                )
                            )
                        }
                    }

                    is GooglePart.FunctionCall -> add(
                        Message.Tool.Call(
                            id = Uuid.random().toString(),
                            tool = part.functionCall.name,
                            content = part.functionCall.args.toString(),
                            metaInfo = metaInfo
                        )
                    )

                    is GooglePart.InlineData -> {
                        val inlineData = part.inlineData
                        val contentPart = when (val mimeType = inlineData.mimeType) {
                            "image/png", "image/jpeg", "image/webp" -> ContentPart.Image(
                                content = AttachmentContent.Binary.Bytes(inlineData.data),
                                format = mimeType.substringAfter("image/"),
                                mimeType = mimeType,
                            )

                            else -> ContentPart.File(
                                content = AttachmentContent.Binary.Bytes(inlineData.data),
                                mimeType = mimeType,
                                format = mimeType.substringAfterLast('.'),
                            )
                        }
                        add(
                            Message.Assistant(
                                parts = listOf(contentPart),
                                finishReason = candidate.finishReason,
                                metaInfo = metaInfo
                            )
                        )
                    }

                    else -> throw LLMClientException(clientName, "Not supported part type: $part")
                }
            }
        }

        return when {
            // If no messages where returned, return an empty message and check finishReason
            responses.isEmpty() -> listOf(
                Message.Assistant(
                    content = "",
                    finishReason = candidate.finishReason,
                    metaInfo = metaInfo
                )
            )

            // Just return responses
            else -> responses
        }
    }

    /**
     * Processes the Google AI API response into a list of choices.
     *
     * @param response The raw response from the Google AI API
     * @return A list of choices, where each choice is a list of response messages
     */
    private fun processGoogleResponse(response: GoogleResponse): List<List<Message.Response>> {
        if (response.candidates.isEmpty()) {
            logger.error { "Empty candidates in Google API response" }
            throw LLMClientException(clientName, "Empty candidates in Google API response")
        }

        // Extract token count from the response
        val inputTokensCount = response.usageMetadata?.promptTokenCount
        val outputTokensCount = response.usageMetadata?.candidatesTokenCount
        val totalTokensCount = response.usageMetadata?.totalTokenCount

        val metaInfo = ResponseMetaInfo.create(
            clock,
            totalTokensCount = totalTokensCount,
            inputTokensCount = inputTokensCount,
            outputTokensCount = outputTokensCount
        )

        return response.candidates.map { candidate ->
            processGoogleCandidate(candidate, metaInfo)
        }
    }

    /**
     * Moderates the given prompt using the specified language model.
     * This method is not supported by the Google API and will throw an exception when invoked.
     *
     * @param prompt The prompt to be evaluated for moderation.
     * @param model The language model to use for moderation.
     * @return This method does not return a result as moderation is not supported by the Google API.
     * @throws UnsupportedOperationException Always thrown since moderation is not supported.
     */
    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by Google API" }
        throw UnsupportedOperationException("Moderation is not supported by Google API.")
    }

    /**
     * Retrieves a list of available language models supported by the Google LLM client.
     * https://ai.google.dev/api/models#method:-models.list
     *
     * @return A list of strings, each representing a model identifier available for use.
     */
    public override suspend fun models(): List<LLModel> {
        var response: GoogleModelsResponse? = null
        val models = mutableListOf<String>()

        while ((response == null) || response.nextPageToken != null) {
            val parameters = response?.nextPageToken?.let {
                mapOf("pageToken" to it)
            } ?: emptyMap()
            try {
                response = httpClient.get(
                    settings.defaultPath,
                    GoogleModelsResponse::class,
                    parameters = parameters
                )
                models.addAll(response.models.map { it.name })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw LLMClientException(clientName, e.message, e)
            }
        }

        val modelsById = GoogleModels.modelsById()

        return models.map { id -> modelsById[id] ?: LLModel(provider = llmProvider(), id = id) }
    }

    /**
     * Embeds the given text using the Google AI embeddings API.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the [LLMCapability.Embed] capability.
     * @return A list of floating-point values representing the embedding vector.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    override suspend fun embed(text: String, model: LLModel): List<Double> {
        require(model.supports(LLMCapability.Embed)) {
            "Model ${model.id} does not support embedding."
        }

        logger.debug { "Embedding text with model: ${model.id}" }

        val request = GoogleEmbeddingRequest(
            model = "models/${model.id}",
            content = GoogleContent(
                parts = listOf(GooglePart.Text(text))
            )
        )

        try {
            val response = httpClient.post(
                path = "${settings.defaultPath}/${model.id}:${settings.embedContentMethod}",
                request = request,
                requestBodyType = GoogleEmbeddingRequest::class,
                responseType = GoogleEmbeddingResponse::class,
            )

            return response.embedding.values
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

    /**
     * Embeds the given inputs using the Google AI batch embeddings API.
     *
     * @param inputs The list of texts to embed.
     * @param model The model to use for embedding. Must have the [LLMCapability.Embed] capability.
     * @return A list of embedding vectors, one per input string.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    override suspend fun embed(inputs: List<String>, model: LLModel): List<List<Double>> {
        require(model.supports(LLMCapability.Embed)) {
            "Model ${model.id} does not support embedding."
        }

        logger.debug { "Embedding input with model: ${model.id}" }

        val request = GoogleEmbeddingBatchRequest(
            requests = inputs.map {
                GoogleEmbeddingRequest(
                    model = "models/${model.id}",
                    content = GoogleContent(
                        parts = listOf(GooglePart.Text(it))
                    )
                )
            }
        )

        try {
            val response = httpClient.post(
                path = "${settings.defaultPath}/${model.id}:${settings.batchEmbedContentsMethod}",
                request = request,
                requestBodyType = GoogleEmbeddingBatchRequest::class,
                responseType = GoogleEmbeddingBatchResponse::class,
            )

            return response.embeddings.map { it.values }
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

    override fun close() {
        httpClient.close()
    }
}
