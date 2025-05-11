package ai.jetbrains.code.prompt.executor.clients.openai

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.SuitableForIO
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.clients.ConnectionTimeoutConfig
import ai.jetbrains.code.prompt.executor.clients.LLMClient
import ai.jetbrains.code.prompt.executor.clients.LLMClientWithEmbeddings
import ai.jetbrains.code.prompt.llm.LLMCapability
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class OpenAIClientSettings(
    val baseUrl: String = "https://api.openai.com/v1",
    val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
)

/**
 * Implementation of [LLMClient] for OpenAI API.
 * Uses Ktor HttpClient to communicate with the OpenAI API.
 *
 * @param apiKey The API key for the OpenAI API
 * @param settings The base URL and timeouts for the OpenAI API, defaults to "https://api.openai.com/v1" and 900 s
 */
open class OpenAILLMClient(
    private val apiKey: String,
    private val settings: OpenAIClientSettings = OpenAIClientSettings(),
    baseClient: HttpClient = HttpClient(engineFactoryProvider())
) : LLMClientWithEmbeddings {

    companion object {
        private val logger =
            LoggerFactory.create("ai.jetbrains.code.prompt.executor.clients.openai.HttpBasedOpenAISuspendableDirectClient")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val httpClient = baseClient.config {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = settings.timeoutConfig.requestTimeoutMillis // Increase timeout to 60 seconds
            connectTimeoutMillis = settings.timeoutConfig.connectTimeoutMillis
            socketTimeoutMillis = settings.timeoutConfig.socketTimeoutMillis
        }
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        if (!prompt.model.capabilities.contains(LLMCapability.Tools) && tools.isNotEmpty()) {
            throw IllegalArgumentException("Model ${prompt.model.id} does not support tools")
        }
        if (!prompt.model.capabilities.contains(LLMCapability.Completion)) {
            throw IllegalArgumentException("Model ${prompt.model.id} does not support chat completions")
        }

        val request = createOpenAIRequest(prompt, tools, model, false)
        val requestBody = json.encodeToString(request)

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post("${settings.baseUrl}/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val openAIResponse = response.body<OpenAIResponse>()
                processOpenAIResponse(openAIResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from OpenAI API: ${response.status}: $errorBody" }
                throw IllegalStateException("Error from OpenAI API: ${response.status}: $errorBody")
            }
        }
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        if (!prompt.model.capabilities.contains(LLMCapability.Completion)) {
            throw IllegalArgumentException("Model ${prompt.model.id} does not support chat completions")
        }

        val request = createOpenAIRequest(prompt, emptyList(), model, true)
        val requestBody = json.encodeToString(request)

        return callbackFlow {
            withContext(Dispatchers.SuitableForIO) {
                try {
                    httpClient.preparePost("${settings.baseUrl}/chat/completions") {
                        contentType(ContentType.Application.Json)
                        header("Authorization", "Bearer $apiKey")
                        setBody(requestBody)
                    }.execute { response ->
                        if (response.status.isSuccess()) {
                            val channel = response.bodyAsChannel()

                            while (!channel.isClosedForRead) {
                                val line = channel.readUTF8Line() ?: continue

                                if (line.startsWith("data: ") && line != "data: [DONE]") {
                                    val jsonData = line.substring(6)
                                    try {
                                        val streamResponse = json.decodeFromString<OpenAIStreamResponse>(jsonData)
                                        streamResponse.choices.forEach { choice ->
                                            choice.delta.content?.let { content ->
                                                trySend(content)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        logger.error { "Error parsing stream response: $e" }
                                    }
                                }
                            }
                        } else {
                            val errorBody = response.bodyAsText()
                            logger.error { "Error from OpenAI API: ${response.status}: $errorBody" }
                            throw IllegalStateException("Error from OpenAI API: ${response.status}: $errorBody")
                        }
                    }
                } catch (e: Exception) {
                    logger.error { "Exception during streaming: $e" }
                    close(e)
                } finally {
                    close()
                }
            }

            awaitClose { }
        }
    }

    private fun createOpenAIRequest(prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, stream: Boolean): OpenAIRequest {
        val messages = mutableListOf<OpenAIMessage>()
        val pendingCalls = mutableListOf<OpenAIToolCall>()

        fun flushCalls() {
            if (pendingCalls.isNotEmpty()) {
                messages += OpenAIMessage(role = "assistant", tool_calls = pendingCalls.toList())
                pendingCalls.clear()
            }
        }

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    flushCalls()
                    messages.add(
                        OpenAIMessage(
                            role = "system",
                            content = message.content
                        )
                    )
                }

                is Message.User -> {
                    flushCalls()
                    messages.add(
                        OpenAIMessage(
                            role = "user",
                            content = message.content
                        )
                    )
                }

                is Message.Assistant -> {
                    flushCalls()
                    messages.add(
                        OpenAIMessage(
                            role = "assistant",
                            content = message.content
                        )
                    )
                }

                is Message.Tool.Result -> {
                    flushCalls()
                    messages.add(
                        OpenAIMessage(
                            role = "tool",
                            content = message.content,
                            tool_call_id = message.id
                        )
                    )
                }

                is Message.Tool.Call -> pendingCalls += OpenAIToolCall(
                    id = message.id ?: UUID.random().toString(),
                    function = OpenAIFunction(message.tool, message.content)
                )
            }
        }
        flushCalls()

        val openAITools = tools.map { tool ->
            val propertiesMap = mutableMapOf<String, JsonElement>()

            // Add required parameters
            tool.requiredParameters.forEach { param ->
                propertiesMap[param.name] = buildOpenAIParam(param)
            }

            // Add optional parameters
            tool.optionalParameters.forEach { param ->
                propertiesMap[param.name] = buildOpenAIParam(param)
            }

            val parametersObject = buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", JsonObject(propertiesMap))
                put("required", buildJsonArray {
                    tool.requiredParameters.forEach { param ->
                        add(JsonPrimitive(param.name))
                    }
                })
            }

            OpenAITool(
                function = OpenAIToolFunction(
                    name = tool.name,
                    description = tool.description,
                    parameters = parametersObject
                )
            )
        }

        return OpenAIRequest(
            model = model.id,
            messages = messages,
            temperature = if (model.capabilities.contains(LLMCapability.Temperature)) prompt.params.temperature else null,
            tools = if (tools.isNotEmpty()) openAITools else null,
            stream = stream
        )
    }

    private fun buildOpenAIParam(param: ToolParameterDescriptor): JsonObject = buildJsonObject {
        put("description", JsonPrimitive(param.description))
        fillOpenAIParamType(param.type)
    }

    private fun JsonObjectBuilder.fillOpenAIParamType(type: ToolParameterType) {
        when (type) {
            ToolParameterType.Boolean -> put("type", JsonPrimitive("boolean"))
            ToolParameterType.Float -> put("type", JsonPrimitive("number"))
            ToolParameterType.Integer -> put("type", JsonPrimitive("integer"))
            ToolParameterType.String -> put("type", JsonPrimitive("string"))
            is ToolParameterType.Enum -> {
                put("type", JsonPrimitive("string"))
                put("enum", buildJsonArray {
                    type.entries.forEach { entry ->
                        add(JsonPrimitive(entry.toString()))
                    }
                })
            }

            is ToolParameterType.List -> {
                put("type", JsonPrimitive("array"))
                put("items", buildJsonObject {
                    fillOpenAIParamType(type.itemsType)
                })
            }
        }
    }

    private fun processOpenAIResponse(response: OpenAIResponse): List<Message.Response> {
        if (response.choices.isEmpty()) {
            logger.error { "Empty choices in OpenAI response" }
            throw IllegalStateException("Empty choices in OpenAI response")
        }

        val message = response.choices.first().message

        return when {
            message.tool_calls != null && message.tool_calls.isNotEmpty() -> {
                message.tool_calls.map { toolCall ->
                    Message.Tool.Call(
                        id = toolCall.id,
                        tool = toolCall.function.name,
                        content = toolCall.function.arguments
                    )
                }
            }

            message.content != null -> {
                listOf(Message.Assistant(message.content))
            }

            else -> {
                logger.error { "Unexpected response from OpenAI: no tool calls and no content" }
                throw IllegalStateException("Unexpected response from OpenAI: no tool calls and no content")
            }
        }
    }

    /**
     * Embeds the given text using the OpenAI embeddings API.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of floating-point values representing the embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    override suspend fun embed(text: String, model: LLModel): List<Double> {
        if (!model.capabilities.contains(LLMCapability.Embed)) {
            throw IllegalArgumentException("Model ${model.id} does not have the Embed capability")
        }

        logger.debug { "Embedding text with model: ${model.id}" }

        val request = OpenAIEmbeddingRequest(
            model = model.id,
            input = text
        )
        val requestBody = json.encodeToString(request)

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post("${settings.baseUrl}/embeddings") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val openAIResponse = response.body<OpenAIEmbeddingResponse>()
                if (openAIResponse.data.isNotEmpty()) {
                    openAIResponse.data.first().embedding
                } else {
                    logger.error { "Empty data in OpenAI embedding response" }
                    throw IllegalStateException("Empty data in OpenAI embedding response")
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from OpenAI API: ${response.status}: $errorBody" }
                throw IllegalStateException("Error from OpenAI API: ${response.status}: $errorBody")
            }
        }
    }
}

internal expect fun engineFactoryProvider(): HttpClientEngineFactory<*>
