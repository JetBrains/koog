package ai.koog.prompt.executor.ollama.client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.ollama.client.dto.*
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Client for interacting with the Ollama API.
 *
 * @property baseUrl The base URL of the Ollama API server.
 * @property baseClient The HTTP client used for making requests.
 */
public class OllamaClient(
    private val baseUrl: String = "http://localhost:11434",
    baseClient: HttpClient = HttpClient(engineFactoryProvider()),
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
): LLMClient, LLMEmbeddingProvider {

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
            requestTimeoutMillis = timeoutConfig.requestTimeoutMillis
            connectTimeoutMillis = timeoutConfig.connectTimeoutMillis
            socketTimeoutMillis  = timeoutConfig.socketTimeoutMillis
        }
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        val response = client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(
                OllamaChatRequestDTO(
                    model = model.toOllamaModelId(),
                    messages = prompt.toOllamaChatMessages(),
                    stream = false,
                )
            )
        }.body<OllamaChatResponseDTO>()

        return if (response.message?.content != null) {
            listOf(Message.Assistant(
                content = response.message.content,
                finishReason = null // Ollama does not provide a stop reason
            ))
        } else {
            emptyList()
        }
    }

    override suspend fun executeStreaming(
        prompt: Prompt,
        model: LLModel
    ): Flow<String> = flow {
        val response = client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(
                OllamaChatRequestDTO(
                    model = model.toOllamaModelId(),
                    messages = prompt.toOllamaChatMessages(),
                    stream = true,
                )
            )
        }

        val channel = response.bodyAsChannel()

        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            val chunk = ollamaJson.decodeFromString<OllamaChatResponseDTO>(line)
            emit(chunk.message?.content ?: "")
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
