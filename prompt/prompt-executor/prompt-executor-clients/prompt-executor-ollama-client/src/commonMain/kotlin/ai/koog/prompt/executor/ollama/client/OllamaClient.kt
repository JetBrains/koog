package ai.koog.prompt.executor.ollama.client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.ollama.client.dto.*
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * Configuration settings for the OllamaClient.
 *
 * @property baseUrl The base URL for connecting to the Ollama server. Defaults to "http://localhost:11434".
 * @property timeoutConfig Configuration for connection timeouts, including request, connect, and socket timeouts.
 * @property messagePath The API endpoint path for sending chat messages. Defaults to "api/chat".
 * @property embeddingsPath The API endpoint path for generating embeddings. Defaults to "api/embeddings".
 * @property listModelsPath The API endpoint path for listing available models. Defaults to "api/tags".
 * @property showModelPath The API endpoint path for showing detailed information about a specific model. Defaults to "api/show".
 * @property pullModelPath The API endpoint path for pulling a model from the Ollama registry. Defaults to "api/pull".
 */
public class OllamaClientSettings(
    public val baseUrl: String = "http://localhost:11434",
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    public val messagePath: String = "api/chat",
    public val embeddingsPath: String = "api/embeddings",
    public val listModelsPath: String = "api/tags",
    public val showModelPath: String = "api/show",
    public val pullModelPath: String = "api/pull"
)

/**
 * Client for interacting with the Ollama API with comprehensive model support.
 *
 * @param settings The base URL and timeouts for the Ollama API, defaults to http://localhost:11434" and 900 s, also provides a configuration for the API paths if non-default Ollama API is used
 * @param baseClient The underlying HTTP client used for making requests.
 * @param clock Clock instance used for tracking response metadata timestamps.
 * Implements:
 * - LLMClient for executing prompts and streaming responses.
 * - LLMEmbeddingProvider for generating embeddings from input text.
 */
public class OllamaClient(
    private val settings: OllamaClientSettings,
    baseClient: HttpClient = HttpClient(engineFactoryProvider()),
    private val clock: Clock = Clock.System
) : LLMClient, LLMEmbeddingProvider {

    /**
     * Constructor for `OllamaClient` to initialize the client with the specified base URL, HTTP client, timeout configurations,
     * and clock.
     *
     * This constructor is deprecated and should be replaced by the `OllamaClient(settings, baseClient, clock)` constructor
     * for improved flexibility and clarity.
     *
     * @param baseUrl The base URL for connecting to the Ollama server. Defaults to "http://localhost:11434".
     * @param baseClient The `HttpClient` instance for executing HTTP requests. Uses the platform-specific engine by default.
     * @param timeoutConfig Configuration settings for timeout durations, including request, connect, and socket timeouts.
     * @param clock The clock instance used for time-related operations, defaults to `Clock.System`.
     * @deprecated Use `OllamaClient(settings, baseClient, clock)` instead.
     */
    @Deprecated(
        replaceWith = ReplaceWith("OllamaClient(settings, baseClient, clock)"),
        message = "Use OllamaClient(settings, baseClient, clock) instead"
    )
    public constructor(
        baseUrl: String = "http://localhost:11434",
        baseClient: HttpClient = HttpClient(engineFactoryProvider()),
        timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
        clock: Clock = Clock.System
    ) : this(OllamaClientSettings(baseUrl, timeoutConfig), baseClient, clock)

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val ollamaJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = baseClient.config {
        defaultRequest {
            url(settings.baseUrl)
            contentType(ContentType.Application.Json)
        }
        install(ContentNegotiation) {
            json(ollamaJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = settings.timeoutConfig.requestTimeoutMillis
            connectTimeoutMillis = settings.timeoutConfig.connectTimeoutMillis
            socketTimeoutMillis = settings.timeoutConfig.socketTimeoutMillis
        }
    }

    override suspend fun execute(
        prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>
    ): List<Message.Response> {
        require(model.provider == LLMProvider.Ollama) { "Model not supported by Ollama" }

        val response: OllamaChatResponseDTO = client.post(settings.messagePath) {
            setBody(
                OllamaChatRequestDTO(
                    model = model.id,
                    messages = prompt.toOllamaChatMessages(),
                    tools = if (tools.isNotEmpty()) tools.map { it.toOllamaTool() } else null,
                    format = prompt.extractOllamaJsonFormat(),
                    options = prompt.extractOllamaOptions(),
                    stream = false,
                ))
        }.body<OllamaChatResponseDTO>()

        return parseResponse(response, prompt)
    }


    private fun parseResponse(response: OllamaChatResponseDTO, prompt: Prompt): List<Message.Response> {
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
                        content = content, metaInfo = responseMetadata
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

    override suspend fun executeStreaming(
        prompt: Prompt, model: LLModel
    ): Flow<String> = flow {
        require(model.provider == LLMProvider.Ollama) { "Model not supported by Ollama" }

        val response = client.post(settings.messagePath) {
            setBody(
                OllamaChatRequestDTO(
                    model = model.id,
                    messages = prompt.toOllamaChatMessages(),
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
                chunk.message?.content?.let { content ->
                    if (content.isNotEmpty()) {
                        emit(content)
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

        val response = client.post(settings.embeddingsPath) {
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
                showModel(model.name)
                    .toOllamaModelCard(model.name, model.size)
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

    private suspend fun loadModelCardOrNull(name: String): OllamaModelCard? {
        return try {
            val listModelsResponse = listModels()

            val modelInfo = listModelsResponse.models.firstOrNull { it.name.isSameModelAs(name) }
                ?: return null

            val modelCard = showModel(modelInfo.name)
                .toOllamaModelCard(modelInfo.name, modelInfo.size)

            logger.info { "Loaded Ollama model card for $name" }
            modelCard
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch model card from Ollama" }
            throw e
        }
    }

    private suspend fun listModels(): OllamaModelsListResponseDTO {
        return client.get(settings.listModelsPath)
            .body<OllamaModelsListResponseDTO>()
    }

    private suspend fun showModel(name: String): OllamaShowModelResponseDTO {
        return client.post(settings.showModelPath) {
            setBody(OllamaShowModelRequestDTO(name = name))
        }.body<OllamaShowModelResponseDTO>()
    }

    private suspend fun pullModel(name: String) {
        try {
            val response = client.post(settings.pullModelPath) {
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
