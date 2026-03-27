package ai.koog.prompt.executor.clients.google.genai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.resolveEffectiveTools
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.InternalLLMClientApi
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
import ai.koog.prompt.executor.clients.requireMatchingProvider
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
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
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.ListModelsConfig
import com.google.genai.types.ThinkingConfig
import com.google.genai.types.ThinkingLevel
import com.google.genai.types.Tool
import com.google.genai.types.ToolConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.future.await
import java.util.concurrent.ExecutorService
import kotlin.jvm.optionals.getOrDefault
import kotlin.time.Clock

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
 * @property knownModels List of known [LLModel] used in [knownModels]. Defaults to [GoogleModels.models].
 */
public open class GoogleGenaiLLMClient @JvmOverloads constructor(
    private val client: Client,
    private val llmProvider: LLMProvider = if (client.vertexAI()) LLMProvider.Vertex else LLMProvider.Google,
    private val fallbackThoughtSignature: String = DEFAULT_THOUGHT_SIGNATURE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.SuitableForIO,
    private val clock: Clock = Clock.System,
    private val knownModels: List<LLModel> = GoogleModels.models
) : LLMClient(), LLMEmbeddingProvider {

    /**
     * Java-friendly constructor that accepts an [ExecutorService] for blocking stream iteration.
     * The executor is converted to a [CoroutineDispatcher] via [asCoroutineDispatcher].
     */
    @JvmOverloads
    public constructor(
        client: Client,
        ioExecutor: ExecutorService,
        llmProvider: LLMProvider = if (client.vertexAI()) LLMProvider.Vertex else LLMProvider.Google,
        fallbackThoughtSignature: String = DEFAULT_THOUGHT_SIGNATURE,
        clock: Clock = Clock.System,
        knownModels: List<LLModel> = GoogleModels.models
    ) : this(
        client = client,
        llmProvider = llmProvider,
        fallbackThoughtSignature = fallbackThoughtSignature,
        ioDispatcher = ioExecutor.asCoroutineDispatcher(),
        clock = clock,
        knownModels = knownModels
    )

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

    private val conversionUtils = GoogleGenaiConversionUtils(logger)
    private val requestConverter = GoogleGenaiRequestConverter(fallbackThoughtSignature, conversionUtils)
    private val responseConverter = GoogleGenaiResponseConverter(logger, clock, conversionUtils)

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

    @OptIn(InternalAgentToolsApi::class, InternalLLMClientApi::class)
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        requireMatchingProvider(model)
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        require(model.supports(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        return doExecute(prompt, model, tools).first()
    }

    @OptIn(InternalLLMClientApi::class)
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = buildStreamFrameFlow {
        requireMatchingProvider(model)
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
                                            ?.let { conversionUtils.convertMapToJsonObject(it).toString() }
                                            ?: "{}",
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

    @OptIn(InternalAgentToolsApi::class, InternalLLMClientApi::class)
    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> {
        requireMatchingProvider(model)
        logger.debug { "Executing prompt with multiple choices: $prompt with tools: $tools and model: $model" }
        require(model.supports(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.supports(LLMCapability.MultipleChoices)) {
            "Model ${model.id} does not support multiple choices"
        }

        return doExecute(prompt, model, tools)
    }

    @OptIn(InternalAgentToolsApi::class)
    private suspend fun doExecute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<List<Message.Response>> {
        val effectiveTools = tools.resolveEffectiveTools(model, prompt.params.toolChoice)
        val (contents, systemInstruction) = buildSdkContents(prompt, model)
        val config = buildConfig(prompt.params, model, effectiveTools, systemInstruction).build()
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
        return requestConverter.buildSdkContents(prompt, model)
    }

    // endregion

    // region Tool conversion

    /**
     * Converts [ToolDescriptor] list to SDK [Tool.Builder] list.
     * Returns builders so subclasses can further modify them
     * (e.g. add google search, code execution) before `.build()`.
     */
    protected open fun buildSdkTools(tools: List<ToolDescriptor>): List<Tool.Builder>? {
        return GoogleGenaiToolConverter.buildSdkTools(tools)
    }

    /**
     * Converts [LLMParams.ToolChoice] to SDK [ToolConfig].
     */
    protected open fun buildSdkToolConfig(toolChoice: LLMParams.ToolChoice?): ToolConfig? {
        return GoogleGenaiToolConverter.buildSdkToolConfig(toolChoice)
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
                    builder.responseSchema(conversionUtils.jsonObjectToSdkSchema(schema.schema))

                is LLMParams.Schema.JSON.Standard ->
                    builder.responseJsonSchema(conversionUtils.jsonObjectToMap(schema.schema))

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
        return responseConverter.extractResponseMetaInfo(response)
    }

    /**
     * Processes a [GenerateContentResponse] into a list of choices.
     * Override to customize the full response-to-choices pipeline.
     *
     * Note: This method stays on the client (rather than in [GoogleGenaiResponseConverter])
     * to preserve virtual dispatch to [extractResponseMetaInfo] and [processCandidate].
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
    protected open fun processCandidate(
        candidate: Candidate,
        metaInfo: ResponseMetaInfo
    ): List<Message.Response> {
        return responseConverter.processCandidate(candidate, metaInfo)
    }

    // endregion

    // region Embedding

    @OptIn(InternalLLMClientApi::class)
    override suspend fun embed(text: String, model: LLModel): List<Double> {
        requireMatchingProvider(model)
        require(model.supports(LLMCapability.Embed)) {
            "Model ${model.id} does not support embedding."
        }

        logger.debug { "Embedding text with model: ${model.id}" }

        val response = callApi { client.async.models.embedContent(model.id, text, null).await() }

        return response.embeddings().getOrDefault(emptyList())
            .firstOrNull()
            ?.values()?.getOrDefault(emptyList())
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
        val knownModelsById = this.knownModels.associateBy { it.id }
        return client.models.list(ListModelsConfig.builder().build()).map {
            responseConverter.convertModel(it, llmProvider, knownModelsById)
        }
    }

    override fun close() {
        client.close()
    }

    // endregion
}
