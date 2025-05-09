package ai.jetbrains.embeddings.local

import ai.jetbrains.embeddings.base.Vector
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for interacting with the Ollama API.
 *
 * @property baseUrl The base URL of the Ollama API.
 * @property modelId The ID of the model to use.
 * @property httpClient The HTTP client to use for API requests.
 */
open class OllamaEmbedderClient(
    private val baseUrl: String,
    private val model: OllamaEmbeddingModel,
    baseClient: HttpClient = HttpClient(engineFactoryProvider())
) {
    private val httpClient: HttpClient = baseClient.config {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /**
     * Embeds the given text using the Ollama model.
     *
     * @param text The text to embed.
     * @return A vector representation of the text.
     */
    open suspend fun embed(text: String): Vector {
        val response = httpClient.post("${baseUrl}/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(EmbeddingRequest(model = model.id, prompt = text))
        }

        val embeddingResponse = response.body<EmbeddingResponse>()
        return Vector(embeddingResponse.embedding)
    }

    /**
     * Closes the HTTP client.
     */
    open fun close() {
        httpClient.close()
    }

    @Serializable
    private data class EmbeddingRequest(
        val model: String,
        val prompt: String
    )

    @Serializable
    private data class EmbeddingResponse(
        val embedding: List<Double>,
        @SerialName("model") val modelId: String? = null
    )
}

expect fun engineFactoryProvider(): HttpClientEngineFactory<*>