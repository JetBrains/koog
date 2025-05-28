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
import io.github.oshai.kotlinlogging.KotlinLogging
import ai.koog.prompt.message.ResponseMetaInfo
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
    private val baseUrl: String = "http://localhost:11434",
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
        prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>
    ): List<Message.Response> {
        require(model.provider == LLMProvider.Ollama) { "Model not supported by Ollama" }

        val response: OllamaChatResponseDTO = client.post(DEFAULT_MESSAGE_PATH) {
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

        val response = client.post(DEFAULT_MESSAGE_PATH) {
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
        return client.get(DEFAULT_LIST_MODELS_PATH)
            .body<OllamaModelsListResponseDTO>()
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
