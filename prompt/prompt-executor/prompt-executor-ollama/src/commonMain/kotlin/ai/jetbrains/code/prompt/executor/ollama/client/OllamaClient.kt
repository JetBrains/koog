package ai.jetbrains.code.prompt.executor.ollama.client

import ai.jetbrains.code.prompt.executor.ollama.client.dto.EmbeddingRequest
import ai.jetbrains.code.prompt.executor.ollama.client.dto.EmbeddingResponse
import ai.jetbrains.code.prompt.executor.ollama.client.dto.OllamaChatRequestDTO
import ai.jetbrains.code.prompt.executor.ollama.client.dto.OllamaChatResponseDTO
import ai.jetbrains.code.prompt.llm.LLMCapability
import ai.jetbrains.code.prompt.llm.LLModel
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Client for interacting with the Ollama API.
 *
 * @property baseUrl The base URL of the Ollama API server.
 * @property baseClient The HTTP client used for making requests.
 */
public open class OllamaClient(
    private val baseUrl: String = "http://localhost:11434",
    baseClient: HttpClient = HttpClient(engineFactoryProvider()),
    requestTimeout: Long = 60_000,
    connectTimeout: Long = 60_000,
    socketTimeout: Long = 60_000,
) {

    private val ollamaJson = Json {
        ignoreUnknownKeys = true
        isLenient = true

    }

    private val client = baseClient.config {
        install(Logging)
        install(ContentNegotiation) {
            json(ollamaJson)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeout
            connectTimeoutMillis = connectTimeout
            socketTimeoutMillis  = socketTimeout
        }
    }

    // TODO: reconsider request and response classnames
    /**
     * Generate a chat completion for a given set of messages with a provided model.
     *
     * @param request The chat request parameters.
     * @return A flow of chat responses.
     */
    public open suspend fun chat(request: OllamaChatRequestDTO): OllamaChatResponseDTO {
        val response = client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        // Non-streaming response
        return response.body<OllamaChatResponseDTO>()
    }

    public open suspend fun chatStream(request: OllamaChatRequestDTO): Flow<OllamaChatResponseDTO>  = flow {
        val response = client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val channel = response.bodyAsChannel()

        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            val chunk = ollamaJson.decodeFromString<OllamaChatResponseDTO>(line)
            emit(chunk)
        }
    }

    // TODO: reconsider it's openness
    /**
     * Embeds the given text using the Ollama model.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A vector representation of the text.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    public open suspend fun embed(text: String, model: LLModel): List<Double> {
        if (!model.capabilities.contains(LLMCapability.Embed)) {
            throw IllegalArgumentException("Model ${model.id} does not have the Embed capability")
        }

        val response = client.post("$baseUrl/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(EmbeddingRequest(model = model.id, prompt = text))
        }

        val embeddingResponse = response.body<EmbeddingResponse>()
        return embeddingResponse.embedding
    }

}

internal expect fun engineFactoryProvider(): HttpClientEngineFactory<*>
