package ai.koog.prompt.executor.ollama.client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.ollama.client.dto.EmbeddingRequestDTO
import ai.koog.prompt.executor.ollama.client.dto.EmbeddingResponseDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatRequestDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatResponseDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaErrorResponseDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaModelsListResponseDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaPullModelRequestDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaPullModelResponseDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaShowModelRequestDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaShowModelResponseDTO
import ai.koog.prompt.executor.ollama.client.dto.extractOllamaJsonFormat
import ai.koog.prompt.executor.ollama.client.dto.extractOllamaOptions
import ai.koog.prompt.executor.ollama.client.dto.getToolCalls
import ai.koog.prompt.executor.ollama.client.dto.toOllamaChatMessages
import ai.koog.prompt.executor.ollama.client.dto.toOllamaModelCard
import ai.koog.prompt.executor.ollama.client.dto.toOllamaTool
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.emitAppend
import ai.koog.prompt.streaming.emitToolCall
import ai.koog.prompt.streaming.streamFrameFlow
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * Client for interacting with the Ollama API with comprehensive model support.
 *
 * @param baseUrl The base URL of the Ollama server. Defaults to "http://localhost:11434".
 * @param baseClient The underlying HTTP client used for making requests.
 * @param timeoutConfig Configuration for connection, request, and socket timeouts.
 * @param clock Clock instance used for tracking response metadata timestamps.
 * Implements:
 * - LLMClient for executing prompts and streaming responses.
 * - LLMEmbeddingProvider for generating embeddings from input text.
 */
public class OllamaClient(
    public val baseUrl: String = "http://localhost:11434",
    baseClient: HttpClient = HttpClient(engineFactoryProvider()),
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    private val clock: Clock = Clock.System
) : LLMClient, LLMEmbeddingProvider {

    private companion object {
        private val logger = KotlinLogging.logger { }

        private const val DEFAULT_MESSAGE_PATH = "api/chat"
        private const val DEFAULT_EMBEDDINGS_PATH = "api/embeddings"
        private const val DEFAULT_LIST_MODELS_PATH = "api/tags"
        private const val DEFAULT_SHOW_MODEL_PATH = "api/show"
        private const val DEFAULT_PULL_MODEL_PATH = "api/pull"

        private val moderationCategoriesMapping: Map<String, List<ModerationCategory>> = mapOf(
            // Violent crimes: unlawful violence towards people and animals
            "S1" to listOf(ModerationCategory.IllicitViolent, ModerationCategory.Violence),

            // Non-violent crimes: fraud, drugs, weapons, hacking, etc.
            "S2" to listOf(ModerationCategory.Illicit),

            // Sex-related crimes: trafficking, harassment, prostitution
            "S3" to listOf(ModerationCategory.IllicitViolent, ModerationCategory.Sexual),

            // Child sexual exploitation
            "S4" to listOf(ModerationCategory.SexualMinors),

            // Defamation (unique)
            "S5" to listOf(ModerationCategory.Defamation),

            // Specialized advice (unique)
            "S6" to listOf(ModerationCategory.SpecializedAdvice),

            // Privacy violations (unique)
            "S7" to listOf(ModerationCategory.Privacy),

            // Intellectual property violations (unique)
            "S8" to listOf(ModerationCategory.IntellectualProperty),

            // Indiscriminate weapons (e.g., nukes, bioweapons)
            "S9" to listOf(ModerationCategory.IllicitViolent),

            // Hate speech (demeaning protected groups)
            "S10" to listOf(ModerationCategory.Hate),

            // Suicide and self-harm
            "S11" to listOf(ModerationCategory.SelfHarm),

            // Sexual content (adult erotica)
            "S12" to listOf(ModerationCategory.Sexual),

            // Election misinformation (unique)
            "S13" to listOf(ModerationCategory.ElectionsMisinformation)
        )

        private val possibleModerationCategories = moderationCategoriesMapping.values.flatten().distinct()
    }

    private val ollamaJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = baseClient.config {
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
        install(ContentNegotiation) {
            json(ollamaJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = timeoutConfig.requestTimeoutMillis
            connectTimeoutMillis = timeoutConfig.connectTimeoutMillis
            socketTimeoutMillis = timeoutConfig.socketTimeoutMillis
        }
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        require(model.provider == LLMProvider.Ollama) { "Model not supported by Ollama" }

        val response = client.post(DEFAULT_MESSAGE_PATH) {
            setBody(
                OllamaChatRequestDTO(
                    model = model.id,
                    messages = prompt.toOllamaChatMessages(model),
                    tools = if (tools.isNotEmpty()) tools.map { it.toOllamaTool() } else null,
                    format = prompt.extractOllamaJsonFormat(),
                    options = prompt.extractOllamaOptions(),
                    stream = false,
                )
            )
        }

        if (response.status.isSuccess()) {
            return parseResponse(response.body<OllamaChatResponseDTO>())
        } else {
            val errorResponse = response.body<OllamaErrorResponseDTO>()
            logger.error { "Ollama error: ${errorResponse.error}" }
            throw RuntimeException("Ollama API error: ${errorResponse.error}")
        }
    }

    private fun parseResponse(response: OllamaChatResponseDTO): List<Message.Response> {
        val messages = response.message ?: return emptyList()
        val content = messages.content
        val toolCalls = messages.toolCalls ?: emptyList()

        // Get token counts from the response, or use null if not available
        val promptTokenCount = response.promptEvalCount
        val responseTokenCount = response.evalCount

        // Calculate total tokens (prompt + response) if both are available
        val totalTokensCount = when {
            promptTokenCount != null && responseTokenCount != null -> promptTokenCount + responseTokenCount
            promptTokenCount != null -> promptTokenCount
            responseTokenCount != null -> responseTokenCount
            else -> null
        }

        val responseMetadata = ResponseMetaInfo.create(
            clock,
            totalTokensCount = totalTokensCount,
            inputTokensCount = promptTokenCount,
            outputTokensCount = responseTokenCount,
        )

        return when {
            content.isNotEmpty() && toolCalls.isEmpty() -> {
                listOf(
                    Message.Assistant(
                        content = content,
                        metaInfo = responseMetadata
                    )
                )
            }

            content.isEmpty() && toolCalls.isNotEmpty() -> {
                messages.getToolCalls(responseMetadata)
            }

            else -> {
                val toolCallMessages = messages.getToolCalls(responseMetadata)
                val assistantMessage = Message.Assistant(
                    content = content,
                    metaInfo = responseMetadata
                )
                listOf(assistantMessage) + toolCallMessages
            }
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = streamFrameFlow {
        require(model.provider == LLMProvider.Ollama) { "Model not supported by Ollama" }

        val response = client.post(DEFAULT_MESSAGE_PATH) {
            setBody(
                OllamaChatRequestDTO(
                    model = model.id,
                    messages = prompt.toOllamaChatMessages(model),
                    options = prompt.extractOllamaOptions(),
                    stream = true,
                )
            )
        }

        val channel = response.bodyAsChannel()

        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.isBlank()) continue

            try {
                val chunk = ollamaJson.decodeFromString<OllamaChatResponseDTO>(line)
                chunk.message?.let { message ->
                    emitAppend(message.content)
                    message.toolCalls?.forEach { toolCall ->
                        emitToolCall(
                            id = null,
                            name = toolCall.function.name,
                            content = toolCall.function.arguments.toString()
                        )
                    }
                }
            } catch (_: Exception) {
                // Skip malformed JSON lines
                continue
            }
        }
    }

    /**
     * Embeds the given text using the Ollama model.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A vector representation of the text.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    override suspend fun embed(text: String, model: LLModel): List<Double> {
        require(model.provider == LLMProvider.Ollama) { "Model not supported by Ollama" }

        if (!model.capabilities.contains(LLMCapability.Embed)) {
            throw IllegalArgumentException("Model ${model.id} does not have the Embed capability")
        }

        val response = client.post(DEFAULT_EMBEDDINGS_PATH) {
            setBody(EmbeddingRequestDTO(model = model.id, prompt = text))
        }

        val embeddingResponse = response.body<EmbeddingResponseDTO>()
        return embeddingResponse.embedding
    }

    /**
     * Returns the model cards for all the available models on the server.
     */
    public suspend fun getModels(): List<OllamaModelCard> {
        return try {
            val listModelsResponse = listModels()

            val modelCards = listModelsResponse.models.map { model ->
                showModel(model.name).toOllamaModelCard(model.name, model.size)
            }

            logger.info { "Loaded ${modelCards.size} Ollama model cards" }
            modelCards
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch model cards from Ollama" }
            throw e
        }
    }

    /**
     * Returns a model card by its model name, on null if no such model exists on the server.
     * @param name the name of the model to get the model card for
     * @param pullIfMissing true if you want to pull the model from the Ollama registry, false otherwise
     */
    public suspend fun getModelOrNull(name: String, pullIfMissing: Boolean = false): OllamaModelCard? {
        var modelCard = loadModelCardOrNull(name)

        if (modelCard == null && pullIfMissing) {
            pullModel(name)
            modelCard = loadModelCardOrNull(name)
        }

        return modelCard
    }

    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        if (!model.capabilities.contains(LLMCapability.Moderation)) {
            throw IllegalArgumentException("Model ${model.id} does not support moderation")
        }

        require(prompt.messages.isNotEmpty()) {
            "Can't moderate an empty prompt"
        }

        val responses = execute(prompt, model)

        check(responses.size == 1) { "Moderation model from Ollama must return a single response" }
        val singleResponse = responses.single()
        check(singleResponse is Message.Assistant) {
            "Moderation model from Ollama must return an assistant message" +
                " (actual response: ${singleResponse::class.simpleName})"
        }
        val contentLines = singleResponse.content.lines()
        val moderationResult = contentLines.first()
        val hazardCategories = singleResponse.content.removePrefix(moderationResult)

        return ModerationResult(
            isHarmful = parseModerationResult(moderationResult),
            categories = parseHazardCategories(hazardCategories),
        )
    }

    private fun parseModerationResult(result: String): Boolean {
        return when (result) {
            "safe" -> false
            "unsafe" -> true
            else -> throw IllegalStateException("Unknown moderation result: $result")
        }
    }

    private fun parseHazardCategories(commentWithHazardCodes: String): Map<ModerationCategory, ModerationCategoryResult> {
        return buildMap {
            commentWithHazardCodes.split(",", "\n", ";", ".", "-", "+", " ").forEach { hazardCode ->
                moderationCategoriesMapping[hazardCode]?.let { categories ->
                    categories.forEach { category ->
                        put(category, ModerationCategoryResult(true))
                    }
                }
            }

            possibleModerationCategories.forEach { category ->
                if (category !in this) {
                    put(category, ModerationCategoryResult(false))
                }
            }
        }
    }

    private suspend fun loadModelCardOrNull(name: String): OllamaModelCard? {
        return try {
            val listModelsResponse = listModels()

            val modelInfo = listModelsResponse.models.firstOrNull { it.name.isSameModelAs(name) } ?: return null

            val modelCard = showModel(modelInfo.name).toOllamaModelCard(modelInfo.name, modelInfo.size)

            logger.info { "Loaded Ollama model card for $name" }
            modelCard
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch model card from Ollama" }
            throw e
        }
    }

    private suspend fun listModels(): OllamaModelsListResponseDTO {
        return client.get(DEFAULT_LIST_MODELS_PATH).body<OllamaModelsListResponseDTO>()
    }

    private suspend fun showModel(name: String): OllamaShowModelResponseDTO {
        return client.post(DEFAULT_SHOW_MODEL_PATH) {
            setBody(OllamaShowModelRequestDTO(name = name))
        }.body<OllamaShowModelResponseDTO>()
    }

    private suspend fun pullModel(name: String) {
        try {
            val response = client.post(DEFAULT_PULL_MODEL_PATH) {
                setBody(OllamaPullModelRequestDTO(name = name, stream = false))
            }.body<OllamaPullModelResponseDTO>()

            if ("success" !in response.status) error("Failed to pull model: '$name'")

            logger.info { "Pulled model '$name'" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to pull model '$name'" }
            throw e
        }
    }
}

internal expect fun engineFactoryProvider(): HttpClientEngineFactory<*>
