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
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.google.models.GoogleCandidate
import ai.koog.prompt.executor.clients.google.models.GoogleContent
import ai.koog.prompt.executor.clients.google.models.GoogleData
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
import ai.koog.prompt.streaming.emitAppend
import ai.koog.prompt.streaming.emitEnd
import ai.koog.prompt.streaming.emitToolCall
import ai.koog.prompt.streaming.streamFrameFlow
import ai.koog.prompt.structure.RegisteredBasicJsonSchemaGenerators
import ai.koog.prompt.structure.RegisteredStandardJsonSchemaGenerators
import ai.koog.prompt.structure.annotations.InternalStructuredOutputApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
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
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    public val defaultPath: String = "v1beta/models",
    public val generateContentMethod: String = "generateContent",
    public val streamGenerateContentMethod: String = "streamGenerateContent",
    public val embedContentMethod: String = "embedContent"
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
) : LLMClient, LLMEmbeddingProvider {

    @OptIn(InternalStructuredOutputApi::class)
    private companion object {
        private val logger = KotlinLogging.logger { }

        init {
            // On class load register custom Google JSON schema generators for structured output.
            RegisteredBasicJsonSchemaGenerators[LLMProvider.Google] = GoogleBasicJsonSchemaGenerator
            RegisteredStandardJsonSchemaGenerators[LLMProvider.Google] = GoogleStandardJsonSchemaGenerator
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val httpClient: KoogHttpClient = KoogHttpClient.fromKtorClient(
        clientName = clientName,
        logger = logger,
        baseClient = baseClient
    ) {
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

    /**
     * Provides the Large Language Model (LLM) provider associated with this client.
     *
     * @return The LLM provider, which is Google for this implementation.
     */
    override fun llmProvider(): LLMProvider = LLMProvider.Google

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.capabilities.contains(LLMCapability.Tools) || tools.isEmpty()) {
            "Model ${model.id} does not support tools"
        }

        val response = getGoogleResponse(prompt, model, tools)
        return processGoogleResponse(response).first()
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = streamFrameFlow {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
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
                    candidate.content?.parts?.forEach { part ->
                        when (part) {
                            is GooglePart.FunctionCall -> emitToolCall(
                                id = part.functionCall.id,
                                name = part.functionCall.name,
                                content = part.functionCall.args?.toString() ?: "{}"
                            )

                            is GooglePart.Text -> emitAppend(part.text)
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
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> {
        logger.debug { "Executing prompt with multiple choices: $prompt with tools: $tools and model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.capabilities.contains(LLMCapability.Tools) || tools.isEmpty()) {
            "Model ${model.id} does not support tools"
        }
        require(model.capabilities.contains(LLMCapability.MultipleChoices)) {
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
        var lastSignature: String? = null

        fun flushCalls() {
            if (pendingCalls.isNotEmpty()) {
                contents += GoogleContent(role = "model", parts = pendingCalls.toList())
                pendingCalls.clear()
            }
        }

        fun flushResults() {
            if (pendingResults.isNotEmpty()) {
                contents += GoogleContent(role = "user", parts = pendingResults.toList())
                pendingResults.clear()
            }
        }

        fun flushAll() {
            flushCalls()
            flushResults()
        }

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    systemMessageParts.add(GooglePart.Text(message.content))
                }

                is Message.User -> {
                    flushAll()
                    // User messages become 'user' role content
                    contents.add(message.toGoogleContent(model))
                }

                is Message.Assistant -> {
                    flushAll()
                    contents.add(
                        GoogleContent(
                            role = "model",
                            parts = listOf(GooglePart.Text(message.content))
                        )
                    )
                }

                is Message.Reasoning -> {
                    // Reasoning indicates a new step - flush previous step
                    flushAll()

                    if (message.content.isNotBlank()) {
                        // If content is present, it's a "Thought Summary" -> Convert to Text part with thought=true
                        contents.add(
                            GoogleContent(
                                role = "model",
                                parts = listOf(
                                    GooglePart.Text(
                                        text = message.content,
                                        thought = true,
                                        thoughtSignature = message.encrypted
                                    )
                                )
                            )
                        )
                    } else {
                        // If content is empty/blank, it's strictly a signature carrier for the next Tool.Call
                        lastSignature = message.encrypted
                    }
                }

                is Message.Tool.Result -> {
                    // Just buffer results. We only flush when we know the current tool turn is complete.
                    pendingResults.add(
                        GooglePart.FunctionResponse(
                            functionResponse = GoogleData.FunctionResponse(
                                id = message.id,
                                name = message.tool,
                                response = buildJsonObject { put("result", message.content) }
                            )
                        )
                    )
                }

                is Message.Tool.Call -> {
                    // First call in step needs to flush stale results
                    if (pendingCalls.isEmpty()) {
                        flushResults()
                    }

                    // Use signature from preceding Reasoning message
                    val signature = lastSignature
                    lastSignature = null // Consume: only first call gets the signature

                    pendingCalls += GooglePart.FunctionCall(
                        functionCall = GoogleData.FunctionCall(
                            id = message.id,
                            name = message.tool,
                            args = json.decodeFromString(message.content)
                        ),
                        thoughtSignature = signature
                    )
                }
            }
        }
        flushAll()

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
            require(schema.capability in model.capabilities) {
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
            temperature = if (model.capabilities.contains(LLMCapability.Temperature)) googleParams.temperature else null,
            candidateCount = if (model.capabilities.contains(LLMCapability.MultipleChoices)) googleParams.numberOfChoices else null,
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
                        require(model.capabilities.contains(LLMCapability.Vision.Image)) {
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
                        require(model.capabilities.contains(LLMCapability.Audio)) {
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
                        require(model.capabilities.contains(LLMCapability.Document)) {
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
                        require(model.capabilities.contains(LLMCapability.Vision.Video)) {
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
            parts.forEach { part ->
                // Create Reasoning for any part with signature (signature carrier),
                // unless the part itself is a thought (in which case it carries the signature)
                val signature = part.thoughtSignature
                val isThought = part.thought == true
                if (signature != null && !isThought) {
                    add(Message.Reasoning(encrypted = signature, content = "", metaInfo = metaInfo))
                }

                when (part) {
                    is GooglePart.Text -> {
                        if (isThought) {
                            add(
                                Message.Reasoning(
                                    content = part.text,
                                    encrypted = signature,
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

                    is GooglePart.FunctionCall -> {
                        add(
                            Message.Tool.Call(
                                id = Uuid.random().toString(),
                                tool = part.functionCall.name,
                                content = part.functionCall.args.toString(),
                                metaInfo = metaInfo
                            )
                        )
                    }

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
            // When the model calls tools, keep Reasoning (for signature) and Tool.Call, filter out Assistant text
            responses.any { it is Message.Tool.Call } -> responses.filter { it is Message.Reasoning || it is Message.Tool.Call }
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
    public override suspend fun models(): List<String> {
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

        return models
    }

    override fun close() {
        httpClient.close()
    }

    override suspend fun embed(text: String, model: LLModel): List<Double> {
        require(model.capabilities.contains(LLMCapability.Embed)) {
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
}
