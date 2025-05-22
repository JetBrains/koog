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
import kotlinx.serialization.encodeToString
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
) : LLMClient, LLMEmbeddingProvider {

    private companion object {
        private val logger = KotlinLogging.logger { }

        private const val DEFAULT_LIST_MODELS_PATH = "api/tags"
        private const val DEFAULT_SHOW_MODEL_PATH = "api/show"
        private const val DEFAULT_MESSAGE_PATH = "api/chat"
        private const val DEFAULT_EMBEDDINGS_PATH = "api/embeddings"
    }

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
            socketTimeoutMillis = timeoutConfig.socketTimeoutMillis
        }
    }

    public suspend fun listModels(): List<LLModel> {
        val response = client.get("$baseUrl/$DEFAULT_LIST_MODELS_PATH") {
            contentType(ContentType.Application.Json)
        }.body<OllamaListModelsResponseDTO>()

        return response.models.map { model ->
            val showResponse = client.post("$baseUrl/$DEFAULT_SHOW_MODEL_PATH") {
                contentType(ContentType.Application.Json)
                setBody(
                    OllamaShowModelRequestDTO(
                        model = model.name,
                    )
                )
            }.body<OllamaShowModelResponseDTO>()

            LLModel(
                provider = LLMProvider.Ollama,
                id = model.name,
                capabilities = showResponse.capabilities.flatMap { capability ->
                    when (capability) {
                        OllamaShowModelResponseDTO.Capability.COMPLETION -> listOf(LLMCapability.Completion)
                        OllamaShowModelResponseDTO.Capability.EMBEDDING -> listOf(LLMCapability.Embed)
                        OllamaShowModelResponseDTO.Capability.INSERT -> listOf()
                        OllamaShowModelResponseDTO.Capability.VISION -> listOf(LLMCapability.Vision)
                        OllamaShowModelResponseDTO.Capability.TOOLS -> listOf(LLMCapability.Tools)
                    }
                } + listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Schema.JSON.Simple,
                    LLMCapability.Schema.JSON.Full,
                )
            )
        }
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        require(model.provider == LLMProvider.Ollama) { "Only Ollama provider is supported" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.capabilities.contains(LLMCapability.Tools) || tools.isEmpty()) {
            "Model ${model.id} does not support tools"
        }

        val response = client.post("$baseUrl/$DEFAULT_MESSAGE_PATH") {
            contentType(ContentType.Application.Json)
            setBody(
                OllamaChatRequestDTO(
                    model = model.id,
                    messages = prompt.toOllamaChatMessages(),
                    tools = tools.map { it.toOllamaTool() },
                    format = prompt.toOllamaJsonFormat(),
                    options = prompt.toOllamaOptions(),
                    stream = false,
                )
            )
        }.body<OllamaChatResponseDTO>()

        val message = response.message ?: error("Unexpected null message from Ollama")
        return when {
            message.toolCalls != null && message.toolCalls.isNotEmpty() -> {
                message.toolCalls.map { toolCall ->
                    Message.Tool.Call(
                        id = null, // Ollama does not provide a tool call id
                        tool = toolCall.function.name,
                        content = Json.encodeToString(toolCall.function.arguments)
                    )
                }
            }

            message.content.isNotBlank() -> {
                listOf(
                    Message.Assistant(
                        content = response.message.content,
                        finishReason = null // Ollama does not provide a stop reason
                    )
                )
            }

            else -> {
                logger.error { "Unexpected response from OpenAI: no tool calls and no content" }
                error("Unexpected response from Ollama: no tool calls and no content")
            }
        }
    }

    override suspend fun executeStreaming(
        prompt: Prompt,
        model: LLModel
    ): Flow<String> = flow {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        require(model.provider == LLMProvider.Ollama) { "Only Ollama provider is supported" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        val response = client.post("$baseUrl/$DEFAULT_MESSAGE_PATH") {
            contentType(ContentType.Application.Json)
            setBody(
                OllamaChatRequestDTO(
                    model = model.id,
                    messages = prompt.toOllamaChatMessages(),
                    format = prompt.toOllamaJsonFormat(),
                    options = prompt.toOllamaOptions(),
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

        val response = client.post("$baseUrl/$DEFAULT_EMBEDDINGS_PATH") {
            contentType(ContentType.Application.Json)
            setBody(EmbeddingRequest(model = model.id, prompt = text))
        }

        val embeddingResponse = response.body<EmbeddingResponse>()
        return embeddingResponse.embedding
    }
}

internal expect fun engineFactoryProvider(): HttpClientEngineFactory<*>
