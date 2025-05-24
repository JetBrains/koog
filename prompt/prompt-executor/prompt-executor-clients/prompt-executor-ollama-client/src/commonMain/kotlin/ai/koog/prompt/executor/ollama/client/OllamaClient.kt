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
import kotlinx.serialization.json.Json

/**
 * Client for interacting with the Ollama API with comprehensive model support.
 *
 * @property baseUrl The base URL of the Ollama API server.
 * @property baseClient The HTTP client used for making requests.
 * @property timeoutConfig Timeout configuration for HTTP requests.
 */
public class OllamaClient(
    private val baseUrl: String = "http://localhost:11434",
    baseClient: HttpClient = HttpClient(engineFactoryProvider()),
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
) : LLMClient, LLMEmbeddingProvider {

    private val ollamaJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = baseClient.config {
        install(ContentNegotiation) {
            json(ollamaJson)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = timeoutConfig.requestTimeoutMillis
            connectTimeoutMillis = timeoutConfig.connectTimeoutMillis
            socketTimeoutMillis = timeoutConfig.socketTimeoutMillis
        }
    }

    private val modelCardCache by lazy { OllamaModelCardCache(client, baseUrl) }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        require(model.provider == LLMProvider.Ollama) { "Model not supported by Ollama" }

        val response: OllamaChatResponseDTO = client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(
                OllamaChatRequestDTO(
                    model = model.id,
                    messages = prompt.toOllamaChatMessages(),
                    tools = if (tools.isNotEmpty()) tools.map { it.toOllamaTool() } else null,
                    format = prompt.extractOllamaJsonFormat(),
                    options = prompt.extractOllamaOptions(),
                    stream = false,
                )
            )
        }.body<OllamaChatResponseDTO>()

        return parseResponse(response)
    }

    private fun parseResponse(response: OllamaChatResponseDTO): List<Message.Response> {
        val messages = response.message ?: return emptyList()
        val content = messages.content
        val toolCalls = messages.toolCalls ?: emptyList()

        return when {
            content.isNotEmpty() && toolCalls.isEmpty() -> {
                listOf(Message.Assistant(content = content))
            }

            content.isEmpty() && toolCalls.isNotEmpty() -> {
                messages.getToolCalls()
            }

            else -> {
                val toolCallMessages = messages.getToolCalls()
                val assistantMessage = Message.Assistant(content = content)
                listOf(assistantMessage) + toolCallMessages
            }
        }
    }

    override suspend fun executeStreaming(
        prompt: Prompt,
        model: LLModel
    ): Flow<String> = flow {
        require(model.provider == LLMProvider.Ollama) { "Model not supported by Ollama" }

        val response = client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
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

        val response = client.post("$baseUrl/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(EmbeddingRequestDTO(model = model.id, prompt = text))
        }

        val embeddingResponse = response.body<EmbeddingResponseDTO>()
        return embeddingResponse.embedding
    }

    /**
     * Returns the model cards for all the available models on the server. The model cards are cached.
     * @param refreshCache true if you want to force refresh the cached model cards, false otherwise
     */
    public suspend fun getModels(refreshCache: Boolean = false): List<OllamaModelCard> {
        return modelCardCache.getModels(refreshCache)
    }

    /**
     * Returns a model card by its model name, on null if no such model exists on the server.
     * @param refreshCache true if you want to force refresh the cached model cards, false otherwise
     * @param pullIfMissing true if you want to pull the model from the Ollama registry, false otherwise
     */
    public suspend fun getModelOrNull(
        name: String,
        refreshCache: Boolean = false,
        pullIfMissing: Boolean = false,
    ): OllamaModelCard? {
        return modelCardCache.getModelOrNull(name, refreshCache, pullIfMissing)
    }
}

internal expect fun engineFactoryProvider(): HttpClientEngineFactory<*>
