package ai.koog.prompt.executor.clients.google.genai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.clients.google.genai.GoogleGenaiLLMClient.Companion.DEFAULT_THOUGHT_SIGNATURE
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingLevel
import ai.koog.prompt.executor.clients.google.structure.GoogleBasicJsonSchemaGenerator
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
import ai.koog.prompt.streaming.buildStreamFrameFlow
import ai.koog.prompt.streaming.requireEndFrame
import ai.koog.utils.io.SuitableForIO
import com.google.genai.Client
import com.google.genai.errors.ClientException
import com.google.genai.errors.ServerException
import com.google.genai.types.AutomaticFunctionCallingConfig
import com.google.genai.types.Blob
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.FunctionCallingConfig
import com.google.genai.types.FunctionCallingConfigMode
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.ThinkingConfig
import com.google.genai.types.ThinkingLevel
import com.google.genai.types.Tool
import com.google.genai.types.ToolConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Implementation of [LLMClient] for Google's Gemini API using the official Google GenAI Java SDK.
 *
 * This client delegates all API calls to [Client] async methods,
 * bridging between Koog's internal types and the SDK's native types.
 *
 * @property client The configured Google GenAI SDK client.
 * @property llmProvider The provider for LLM configuration and execution.
 * @property fallbackThoughtSignature Thought signature used for thinking models when no signature is available.
 *          Default to [DEFAULT_THOUGHT_SIGNATURE]
 * @property ioDispatcher Dispatcher for blocking stream iteration. Defaults to [Dispatchers.IO].
 *   Pass a custom dispatcher for virtual threads, test dispatchers, or application-specific thread pools.
 * @property clock Clock instance used for tracking response metadata timestamps.
 */
@Suppress("TooManyFunctions")
public open class GoogleGenaiLLMClient @JvmOverloads constructor(
    private val client: Client,
    private val llmProvider: LLMProvider = if (client.vertexAI()) LLMProvider.Vertex else LLMProvider.Google,
    private val fallbackThoughtSignature: String = DEFAULT_THOUGHT_SIGNATURE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.SuitableForIO,
    private val clock: Clock = Clock.System
) : LLMClient(), LLMEmbeddingProvider {

    public companion object {
        private val logger = KotlinLogging.logger { }

        /**
         * Default thought signature used for thinking models when no signature is available.
         *
         * See https://ai.google.dev/gemini-api/docs/thought-signatures
         */
        public const val DEFAULT_THOUGHT_SIGNATURE: String = "context_engineering_is_the_way_to_go"

        /**
         * Constant used to identify and validate the signature of skip-thought processes.
         *
         * See https://ai.google.dev/gemini-api/docs/thought-signatures
         */
        public const val SKIP_THOUGHT_SIGNATURE: String = "skip_thought_signature_validator"
    }

    override fun getBasicJsonSchemaGenerator(): GoogleBasicJsonSchemaGenerator {
        return GoogleBasicJsonSchemaGenerator
    }

    override fun getStandardJsonSchemaGenerator(): GoogleStandardJsonSchemaGenerator {
        return GoogleStandardJsonSchemaGenerator
    }

    override fun llmProvider(): LLMProvider = llmProvider

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> callApi(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ClientException) {
        throw LLMClientException(
            clientName = clientName,
            message = "Status code: ${e.code()} (${e.status()}). ${e.message}",
            cause = e
        )
    } catch (e: ServerException) {
        throw LLMClientException(
            clientName = clientName,
            message = "Status code: ${e.code()} (${e.status()}). ${e.message}",
            cause = e
        )
    } catch (e: Exception) {
        throw LLMClientException(clientName = clientName, message = e.message, cause = e)
    }

    // region Execute

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        require(model.supports(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.supports(LLMCapability.Tools) || tools.isEmpty()) {
            "Model ${model.id} does not support tools"
        }

        val (contents, systemInstruction) = buildSdkContents(prompt, model)
        val config = buildConfig(prompt.params, model, tools, systemInstruction).build()
        val response = callApi { client.async.models.generateContent(model.id, contents, config).await() }
        return processResponse(response).first()
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

        val (contents, systemInstruction) = buildSdkContents(prompt, model)
        val config = buildConfig(prompt.params, model, tools, systemInstruction).build()

        callApi {
            val stream = client.async.models.generateContentStream(model.id, contents, config).await()
            stream.use { responseStream ->
                for (chunk in responseStream) {
                    val meta = extractResponseMetaInfo(chunk)

                    chunk.candidates().orElse(null)?.firstOrNull()?.let { candidate ->
                        candidate.content().orElse(null)?.parts()?.orElse(null)
                            ?.forEachIndexed { index, part ->
                                val functionCall = part.functionCall().orElse(null)
                                val text = part.text().orElse(null)

                                when {
                                    functionCall != null -> emitToolCallDelta(
                                        id = functionCall.id().orElse(null),
                                        name = functionCall.name().orElse(null),
                                        args = functionCall.args().orElse(null)
                                            ?.let { convertMapToJsonObject(it).toString() } ?: "{}",
                                        index = index
                                    )

                                    text != null -> emitTextDelta(text, index)
                                }
                            }

                        candidate.finishReason().orElse(null)?.let { reason ->
                            emitEnd(reason.toString(), meta)
                        }
                    }
                }
            }
        }
    }.flowOn(ioDispatcher).requireEndFrame()

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

        val (contents, systemInstruction) = buildSdkContents(prompt, model)
        val config = buildConfig(prompt.params, model, tools, systemInstruction).build()
        val response = callApi { client.async.models.generateContent(model.id, contents, config).await() }
        return processResponse(response)
    }

    // endregion

    // region Prompt → SDK types conversion

    /**
     * Converts a [Prompt] to SDK [Content] list and optional system instruction.
     *
     * @return Pair of (conversation contents, system instruction content or null)
     */
    protected open fun buildSdkContents(
        prompt: Prompt,
        model: LLModel
    ): Pair<List<Content>, Content?> {
        val systemParts = mutableListOf<Part>()
        val contents = mutableListOf<Content>()
        val pendingCalls = mutableListOf<Part>()
        val pendingResults = mutableListOf<Part>()
        var lastSignature: String? = null
        val isThinkingModel = model.supports(LLMCapability.Thinking)

        fun flushCalls() {
            if (pendingCalls.isNotEmpty()) {
                contents += Content.builder().role("model").parts(pendingCalls.toList()).build()
                pendingCalls.clear()
            }
        }

        fun flushResults() {
            if (pendingResults.isNotEmpty()) {
                contents += Content.builder().role("user").parts(pendingResults.toList()).build()
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
                    systemParts.add(Part.fromText(message.content))
                }

                is Message.User -> {
                    flushAll()
                    contents.add(buildUserContent(message, model))
                }

                is Message.Assistant -> {
                    flushAll()
                    contents.add(
                        Content.builder().role("model").parts(Part.fromText(message.content)).build()
                    )
                }

                is Message.Reasoning -> {
                    flushAll()

                    if (message.content.isNotBlank()) {
                        val partBuilder = Part.builder().text(message.content).thought(true)
                        message.encrypted?.let { partBuilder.thoughtSignature(signatureToBytes(it)) }
                        contents.add(
                            Content.builder().role("model").parts(listOf(partBuilder.build())).build()
                        )
                    } else {
                        lastSignature = message.encrypted
                    }
                }

                is Message.Tool.Result -> {
                    pendingResults.add(
                        Part.fromFunctionResponse(
                            message.tool,
                            mapOf("result" to message.content)
                        )
                    )
                }

                is Message.Tool.Call -> {
                    if (pendingCalls.isEmpty()) {
                        flushResults()
                    }

                    val signature = lastSignature
                    lastSignature = null

                    val effectiveSignature = signature ?: if (isThinkingModel) {
                        fallbackThoughtSignature
                    } else {
                        null
                    }

                    val args = parseJsonToMap(message.content)
                    val partBuilder = Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .name(message.tool)
                                .args(args)
                                .build()
                        )
                    effectiveSignature?.let { partBuilder.thoughtSignature(signatureToBytes(it)) }
                    pendingCalls += partBuilder.build()
                }
            }
        }
        flushAll()

        val systemInstruction = systemParts
            .takeIf { it.isNotEmpty() }
            ?.let { Content.builder().parts(it).build() }

        return contents to systemInstruction
    }

    private fun buildUserContent(message: Message.User, model: LLModel): Content {
        val parts = message.parts.map { part ->
            when (part) {
                is ContentPart.Text -> Part.fromText(part.text)

                is ContentPart.Image -> {
                    require(model.supports(LLMCapability.Vision.Image)) {
                        "Model ${model.id} does not support images"
                    }
                    blobPart(part.content, part.mimeType)
                }

                is ContentPart.Audio -> {
                    require(model.supports(LLMCapability.Audio)) {
                        "Model ${model.id} does not support audio"
                    }
                    blobPart(part.content, part.mimeType)
                }

                is ContentPart.File -> {
                    require(model.supports(LLMCapability.Document)) {
                        "Model ${model.id} does not support documents"
                    }
                    blobPart(part.content, part.mimeType)
                }

                is ContentPart.Video -> {
                    require(model.supports(LLMCapability.Vision.Video)) {
                        "Model ${model.id} does not support video"
                    }
                    blobPart(part.content, part.mimeType)
                }
            }
        }
        return Content.builder().role("user").parts(parts).build()
    }

    private fun blobPart(content: AttachmentContent, mimeType: String): Part {
        val bytes = when (content) {
            is AttachmentContent.Binary -> content.asBytes()
            else -> throw IllegalArgumentException("Unsupported attachment content: ${content::class}")
        }
        return Part.builder().inlineData(
            Blob.builder().data(bytes).mimeType(mimeType).build()
        ).build()
    }

    // endregion

    // region Tool conversion

    /**
     * Converts [ToolDescriptor] list to SDK [Tool.Builder] list.
     * Returns builders so subclasses can further modify them
     * (e.g. add google search, code execution) before `.build()`.
     */
    protected open fun buildSdkTools(tools: List<ToolDescriptor>): List<Tool.Builder>? {
        if (tools.isEmpty()) return null

        val declarations = tools.map { tool ->
            val properties = (tool.requiredParameters + tool.optionalParameters)
                .associate { it.name to buildParamSchema(it) }

            val schema = mapOf(
                "type" to "object",
                "properties" to properties,
                "required" to tool.requiredParameters.map { it.name }
            )

            FunctionDeclaration.builder()
                .name(tool.name)
                .description(tool.description)
                .parametersJsonSchema(schema)
                .build()
        }

        return listOf(Tool.builder().functionDeclarations(declarations))
    }

    private fun buildParamSchema(param: ToolParameterDescriptor): Map<String, Any?> {
        val schema = mutableMapOf<String, Any?>("description" to param.description)
        putTypeSchema(schema, param.type)
        return schema
    }

    private fun putTypeSchema(schema: MutableMap<String, Any?>, type: ToolParameterType) {
        when (type) {
            ToolParameterType.Boolean -> schema["type"] = "boolean"

            ToolParameterType.Float -> schema["type"] = "number"

            ToolParameterType.Integer -> schema["type"] = "integer"

            ToolParameterType.String -> schema["type"] = "string"

            ToolParameterType.Null -> schema["type"] = "null"

            is ToolParameterType.Enum -> {
                schema["type"] = "string"
                schema["enum"] = type.entries.toList()
            }

            is ToolParameterType.List -> {
                schema["type"] = "array"
                val itemSchema = mutableMapOf<String, Any?>()
                putTypeSchema(itemSchema, type.itemsType)
                schema["items"] = itemSchema
            }

            is ToolParameterType.AnyOf -> {
                schema["anyOf"] = type.types.map { buildParamSchema(it) }
            }

            is ToolParameterType.Object -> {
                schema["type"] = "object"
                schema["properties"] = type.properties.associate { prop ->
                    val propSchema = mutableMapOf<String, Any?>("description" to prop.description)
                    putTypeSchema(propSchema, prop.type)
                    prop.name to propSchema
                }
            }
        }
    }

    /**
     * Converts [LLMParams.ToolChoice] to SDK [ToolConfig].
     */
    protected open fun buildSdkToolConfig(toolChoice: LLMParams.ToolChoice?): ToolConfig? {
        val fcConfig = when (toolChoice) {
            LLMParams.ToolChoice.Auto -> FunctionCallingConfig.builder()
                .mode(FunctionCallingConfigMode.Known.AUTO)
                .build()

            LLMParams.ToolChoice.None -> FunctionCallingConfig.builder()
                .mode(FunctionCallingConfigMode.Known.NONE)
                .build()

            LLMParams.ToolChoice.Required -> FunctionCallingConfig.builder()
                .mode(FunctionCallingConfigMode.Known.ANY)
                .build()

            is LLMParams.ToolChoice.Named -> FunctionCallingConfig.builder()
                .mode(FunctionCallingConfigMode.Known.ANY)
                .allowedFunctionNames(listOf(toolChoice.name))
                .build()

            null -> return null
        }
        return ToolConfig.builder().functionCallingConfig(fcConfig).build()
    }

    // endregion

    // region Config building

    /**
     * Builds a [GenerateContentConfig.Builder] from Koog params, model, tools, and system instruction.
     * Returns the builder so subclasses can further modify it before `.build()` is called by the caller.
     */
    protected open fun buildConfig(
        params: LLMParams,
        model: LLModel,
        tools: List<ToolDescriptor>,
        systemInstruction: Content?
    ): GenerateContentConfig.Builder {
        val googleParams = asGoogleParams(params)

        val builder = GenerateContentConfig.builder()
            .automaticFunctionCalling(
                AutomaticFunctionCallingConfig.builder().disable(true).build()
            )

        // Generation parameters
        if (model.supports(LLMCapability.Temperature)) {
            googleParams.temperature?.let { builder.temperature(it.toFloat()) }
        }
        googleParams.maxTokens?.let { builder.maxOutputTokens(it) }
        if (model.supports(LLMCapability.MultipleChoices)) {
            googleParams.numberOfChoices?.let { builder.candidateCount(it) }
        }
        googleParams.topP?.let { builder.topP(it.toFloat()) }
        googleParams.topK?.let { builder.topK(it.toFloat()) }

        // System instruction
        systemInstruction?.let { builder.systemInstruction(it) }

        // Tools
        buildSdkTools(tools)?.map { it.build() }?.let { builder.tools(it) }
        buildSdkToolConfig(googleParams.toolChoice)?.let { builder.toolConfig(it) }

        // Response format (structured output schema)
        googleParams.schema?.let { schema ->
            require(model.supports(schema.capability)) {
                "Model ${model.id} does not support structured output schema ${schema.name}"
            }
            builder.responseMimeType("application/json")

            @Suppress("REDUNDANT_ELSE_IN_WHEN")
            when (schema) {
                is LLMParams.Schema.JSON.Basic ->
                    builder.responseSchema(jsonObjectToSdkSchema(schema.schema))

                is LLMParams.Schema.JSON.Standard ->
                    builder.responseJsonSchema(jsonObjectToMap(schema.schema))

                else -> throw IllegalArgumentException("Unsupported schema type: $schema")
            }
        }

        // Thinking config
        googleParams.thinkingConfig?.let { tc ->
            builder.thinkingConfig(buildSdkThinkingConfig(tc))
        }

        return builder
    }

    private fun asGoogleParams(params: LLMParams): GoogleParams {
        if (params is GoogleParams) return params
        return GoogleParams(
            temperature = params.temperature,
            maxTokens = params.maxTokens,
            numberOfChoices = params.numberOfChoices,
            speculation = params.speculation,
            schema = params.schema,
            toolChoice = params.toolChoice,
            user = params.user,
            additionalProperties = params.additionalProperties,
        )
    }

    private fun buildSdkThinkingConfig(tc: GoogleThinkingConfig): ThinkingConfig {
        val builder = ThinkingConfig.builder()
        tc.includeThoughts?.let { builder.includeThoughts(it) }
        tc.thinkingBudget?.let { builder.thinkingBudget(it) }
        tc.thinkingLevel?.let {
            builder.thinkingLevel(
                when (it) {
                    GoogleThinkingLevel.LOW -> ThinkingLevel(ThinkingLevel.Known.LOW)
                    GoogleThinkingLevel.HIGH -> ThinkingLevel(ThinkingLevel.Known.HIGH)
                }
            )
        }
        return builder.build()
    }

    // endregion

    // region Response processing

    /**
     * Extracts [ResponseMetaInfo] from a [GenerateContentResponse].
     * Override to enrich metadata with additional fields (e.g. model version, response ID, thoughts token count).
     */
    protected open fun extractResponseMetaInfo(response: GenerateContentResponse): ResponseMetaInfo {
        val usageMetadata = response.usageMetadata().orElse(null)
        return ResponseMetaInfo.create(
            clock,
            totalTokensCount = usageMetadata?.totalTokenCount()?.orElse(null),
            inputTokensCount = usageMetadata?.promptTokenCount()?.orElse(null),
            outputTokensCount = usageMetadata?.candidatesTokenCount()?.orElse(null),
        )
    }

    /**
     * Processes a [GenerateContentResponse] into a list of choices.
     * Override to customize the full response-to-choices pipeline.
     */
    protected open fun processResponse(response: GenerateContentResponse): List<List<Message.Response>> {
        val candidates = response.candidates().orElse(null)
        if (candidates.isNullOrEmpty()) {
            logger.error { "Empty candidates in Google API response" }
            throw LLMClientException(clientName, "Empty candidates in Google API response")
        }

        val metaInfo = extractResponseMetaInfo(response)

        // Warn on empty parts
        if (candidates.all { c -> c.content().orElse(null)?.parts()?.orElse(null)?.isEmpty() == true }) {
            logger.warn { "Content `parts` field is missing in the response from GoogleAI API: $response" }
        }

        return candidates.map { candidate ->
            processCandidate(candidate, metaInfo)
        }
    }

    /**
     * Processes a single [Candidate] into internal message format.
     * Override to customize how individual candidates are converted to Koog messages.
     */
    @OptIn(ExperimentalUuidApi::class)
    protected open fun processCandidate(
        candidate: Candidate,
        metaInfo: ResponseMetaInfo
    ): List<Message.Response> {
        val parts = candidate.content().orElse(null)?.parts()?.orElse(null).orEmpty()
        val finishReason = candidate.finishReason().orElse(null)?.toString()
        val responses = mutableListOf<Message.Response>()

        for (part in parts) {
            val signature = part.thoughtSignature().orElse(null)?.let { signatureFromBytes(it) }
            val isThought = part.thought().orElse(false)

            // Create Reasoning for any part with signature, unless the part itself is a thought
            // and we haven't already added a reasoning message for this signature.
            if (signature != null && !isThought && responses.none { it is Message.Reasoning && it.encrypted == signature }) {
                responses.add(Message.Reasoning(encrypted = signature, content = "", metaInfo = metaInfo))
            }

            val functionCall = part.functionCall().orElse(null)
            val text = part.text().orElse(null)
            val inlineData = part.inlineData().orElse(null)

            when {
                text != null -> {
                    if (isThought) {
                        val existing = if (signature != null) {
                            responses.filterIsInstance<Message.Reasoning>().find { it.encrypted == signature }
                        } else {
                            null
                        }

                        if (existing != null && existing.content.isEmpty()) {
                            val index = responses.indexOf(existing)
                            responses[index] =
                                existing.copy(parts = listOf(ContentPart.Text(text)))
                        } else {
                            responses.add(
                                Message.Reasoning(
                                    content = text,
                                    encrypted = signature,
                                    metaInfo = metaInfo
                                )
                            )
                        }
                    } else {
                        responses.add(
                            Message.Assistant(
                                content = text,
                                finishReason = finishReason,
                                metaInfo = metaInfo
                            )
                        )
                    }
                }

                functionCall != null -> {
                    val args = functionCall.args().orElse(null)
                        ?.let { convertMapToJsonObject(it).toString() } ?: "{}"
                    responses.add(
                        Message.Tool.Call(
                            id = Uuid.random().toString(),
                            tool = functionCall.name().orElse(""),
                            content = args,
                            metaInfo = metaInfo
                        )
                    )
                }

                inlineData != null -> {
                    val mimeType = inlineData.mimeType().orElse("application/octet-stream")
                    val data = inlineData.data().orElse(ByteArray(0))
                    val contentPart = when (mimeType) {
                        "image/png", "image/jpeg", "image/webp" -> ContentPart.Image(
                            content = AttachmentContent.Binary.Bytes(data),
                            format = mimeType.substringAfter("image/"),
                            mimeType = mimeType,
                        )

                        else -> ContentPart.File(
                            content = AttachmentContent.Binary.Bytes(data),
                            mimeType = mimeType,
                            format = mimeType.substringAfterLast('.'),
                        )
                    }
                    responses.add(
                        Message.Assistant(
                            parts = listOf(contentPart),
                            finishReason = finishReason,
                            metaInfo = metaInfo
                        )
                    )
                }

                else -> {
                    logger.warn { "Unhandled part type in response: $part" }
                }
            }
        }

        return when {
            responses.any { it is Message.Tool.Call } ->
                responses.filter { it is Message.Reasoning || it is Message.Tool.Call }

            responses.isEmpty() -> listOf(
                Message.Assistant(content = "", finishReason = finishReason, metaInfo = metaInfo)
            )

            else -> responses
        }
    }

    // endregion

    // region Embedding

    override suspend fun embed(text: String, model: LLModel): List<Double> {
        require(model.supports(LLMCapability.Embed)) {
            "Model ${model.id} does not support embedding."
        }

        logger.debug { "Embedding text with model: ${model.id}" }

        val response = callApi { client.async.models.embedContent(model.id, text, null).await() }
        return response.embeddings().orElse(emptyList())
            .firstOrNull()
            ?.values()?.orElse(emptyList())
            ?.map { it.toDouble() }
            ?: emptyList()
    }

    // endregion

    // region Models & lifecycle

    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by Google API" }
        throw UnsupportedOperationException("Moderation is not supported by Google API.")
    }

    public override suspend fun models(): List<LLModel> {
        return GoogleModels.models
    }

    override fun close() {
        client.close()
    }

    // endregion

    // region Utility functions

    /**
     * Converts a signature string to byte[] for the SDK.
     *
     * The Google API returns thought signatures as JSON strings. The SDK expects `byte[]` and
     * serializes them back to JSON. We use UTF-8 encoding (string → bytes) on requests and
     * UTF-8 decoding (bytes → string) on responses, preserving the original string value.
     * This assumes signatures are valid UTF-8 strings (which holds for both base64 and plaintext tokens).
     */
    private fun signatureToBytes(value: String): ByteArray = value.encodeToByteArray()
    private fun signatureFromBytes(value: ByteArray): String = value.decodeToString()

    /**
     * Parses a JSON string (tool call args) into a Map<String, Object> for the SDK.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun parseJsonToMap(jsonString: String): Map<String, Any?> {
        if (jsonString.isBlank() || jsonString == "{}") return emptyMap()
        return try {
            val element = Json.parseToJsonElement(jsonString)
            if (element is JsonObject) jsonObjectToMap(element) else emptyMap()
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse JSON args: $jsonString" }
            emptyMap()
        }
    }

    /**
     * Converts a [JsonObject] to a plain Map for the SDK.
     */
    private fun jsonObjectToMap(json: JsonObject): Map<String, Any?> {
        return json.mapValues { (_, v) -> jsonElementToAny(v) }
    }

    private fun jsonElementToAny(element: JsonElement): Any? = when (element) {
        is JsonNull -> null

        is JsonPrimitive -> when {
            element.isString -> element.content
            element.content == "true" || element.content == "false" -> element.content.toBoolean()
            else -> element.content.toLongOrNull() ?: element.content.toDoubleOrNull() ?: element.content
        }

        is JsonObject -> jsonObjectToMap(element)

        is JsonArray -> element.map { jsonElementToAny(it) }
    }

    /**
     * Converts a Map<String, Object> from SDK response to a [JsonObject].
     */
    private fun convertMapToJsonObject(map: Map<String, Any?>): JsonObject = buildJsonObject {
        for ((key, value) in map) {
            when (value) {
                null -> put(key, JsonNull)

                is String -> put(key, value)

                is Int -> put(key, value.toLong())

                is Long -> put(key, value)

                is Number -> put(key, value.toDouble())

                is Boolean -> put(key, value)

                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    put(key, convertMapToJsonObject(value as Map<String, Any?>))
                }

                is List<*> -> {
                    put(key, JsonArray(value.map { convertAnyToJsonElement(it) }))
                }

                else -> put(key, value.toString())
            }
        }
    }

    private fun convertAnyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull

        is String -> JsonPrimitive(value)

        is Int -> JsonPrimitive(value.toLong())

        is Long -> JsonPrimitive(value)

        is Number -> JsonPrimitive(value.toDouble())

        is Boolean -> JsonPrimitive(value)

        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            convertMapToJsonObject(value as Map<String, Any?>)
        }

        is List<*> -> JsonArray(value.map { convertAnyToJsonElement(it) })

        else -> JsonPrimitive(value.toString())
    }

    /**
     * Converts a [JsonObject] to an SDK [Schema] for response schema.
     */
    private fun jsonObjectToSdkSchema(json: JsonObject): Schema {
        return Schema.fromJson(json.toString())
    }

    // endregion
}
