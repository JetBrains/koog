package ai.jetbrains.code.prompt.executor.clients.openrouter

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.SuitableForIO
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.clients.ConnectionTimeoutConfig
import ai.jetbrains.code.prompt.executor.clients.DirectLLMClient
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

class OpenRouterClientSettings(
    val baseUrl: String = "https://openrouter.ai/api/v1",
    val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
)

/**
 * Implementation of [DirectLLMClient] for OpenRouter API.
 * OpenRouter is an API that routes requests to multiple LLM providers.
 *
 * @param apiKey The API key for the OpenRouter API
 * @param settings The base URL and timeouts for the OpenRouter API, defaults to "https://openrouter.ai/api/v1" and 900s
 */
open class OpenRouterDirectLLMClient(
    private val apiKey: String,
    private val settings: OpenRouterClientSettings = OpenRouterClientSettings(),
    baseClient: HttpClient = HttpClient(engineFactoryProvider())
) : DirectLLMClient {

    companion object {
        private val logger =
            LoggerFactory.create("ai.jetbrains.code.prompt.executor.clients.openrouter.OpenRouterDirectLLMClient")
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
            requestTimeoutMillis = settings.timeoutConfig.requestTimeoutMillis
            connectTimeoutMillis = settings.timeoutConfig.connectTimeoutMillis
            socketTimeoutMillis = settings.timeoutConfig.socketTimeoutMillis
        }
    }

    @Serializable
    private data class OpenRouterRequest(
        val model: String,
        val messages: List<OpenRouterMessage>,
        val temperature: Double? = null,
        val tools: List<OpenRouterTool>? = null,
        val stream: Boolean = false
    )

    @Serializable
    private data class OpenRouterMessage(
        val role: String,
        val content: String? = "",
        val tool_calls: List<OpenRouterToolCall>? = null,
        val name: String? = null,
        val tool_call_id: String? = null
    )

    @Serializable
    private data class OpenRouterToolCall(
        val id: String,
        val type: String = "function",
        val function: OpenRouterFunction
    )

    @Serializable
    private data class OpenRouterFunction(
        val name: String,
        val arguments: String
    )

    @Serializable
    private data class OpenRouterTool(
        val type: String = "function",
        val function: OpenRouterToolFunction
    )

    @Serializable
    private data class OpenRouterToolFunction(
        val name: String,
        val description: String,
        val parameters: JsonObject
    )

    @Serializable
    private data class OpenRouterResponse(
        val id: String,
        @SerialName("object") val objectType: String,
        val created: Long,
        val model: String,
        val choices: List<OpenRouterChoice>,
        val usage: OpenRouterUsage? = null
    )

    @Serializable
    private data class OpenRouterChoice(
        val index: Int,
        val message: OpenRouterMessage,
        val finish_reason: String? = null
    )

    @Serializable
    private data class OpenRouterUsage(
        val prompt_tokens: Int,
        val completion_tokens: Int,
        val total_tokens: Int
    )

    @Serializable
    private data class OpenRouterStreamResponse(
        val id: String,
        @SerialName("object") val objectType: String,
        val created: Long,
        val model: String,
        val choices: List<OpenRouterStreamChoice>
    )

    @Serializable
    private data class OpenRouterStreamChoice(
        val index: Int,
        val delta: OpenRouterStreamDelta,
        val finish_reason: String? = null
    )

    @Serializable
    private data class OpenRouterStreamDelta(
        val role: String? = null,
        val content: String? = null,
        val tool_calls: List<OpenRouterToolCall>? = null
    )

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools" }

        val request = createOpenRouterRequest(prompt, model, tools, false)
        val requestBody = json.encodeToString(request)

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post("${settings.baseUrl}/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                // OpenRouter requires HTTP_REFERER header to be set
                header("HTTP-Referer", "https://jetbrains.com")
                // Set custom user agent for OpenRouter
                header("User-Agent", "JetBrains/1.0")
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val openRouterResponse = response.body<OpenRouterResponse>()
                processOpenRouterResponse(openRouterResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from OpenRouter API: ${response.status}: $errorBody" }
                throw IllegalStateException("Error from OpenRouter API: ${response.status}: $errorBody")
            }
        }
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        logger.debug { "Executing streaming prompt: $prompt" }

        val request = createOpenRouterRequest(prompt, model, emptyList(), true)
        val requestBody = json.encodeToString(request)

        return callbackFlow {
            withContext(Dispatchers.SuitableForIO) {
                try {
                    httpClient.preparePost("${settings.baseUrl}/chat/completions") {
                        contentType(ContentType.Application.Json)
                        header("Authorization", "Bearer $apiKey")
                        // OpenRouter requires HTTP_REFERER header to be set
                        header("HTTP-Referer", "https://jetbrains.com")
                        // Set custom user agent for OpenRouter
                        header("User-Agent", "JetBrains/1.0")
                        setBody(requestBody)
                    }.execute { response ->
                        if (response.status.isSuccess()) {
                            val channel = response.bodyAsChannel()

                            while (!channel.isClosedForRead) {
                                val line = channel.readUTF8Line() ?: continue

                                if (line.startsWith("data: ") && line != "data: [DONE]") {
                                    val jsonData = line.substring(6)
                                    try {
                                        val streamResponse = json.decodeFromString<OpenRouterStreamResponse>(jsonData)
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
                            logger.error { "Error from OpenRouter API: ${response.status}: $errorBody" }
                            throw IllegalStateException("Error from OpenRouter API: ${response.status}: $errorBody")
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

    private fun createOpenRouterRequest(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        stream: Boolean
    ): OpenRouterRequest {
        val messages = mutableListOf<OpenRouterMessage>()
        val pendingCalls = mutableListOf<OpenRouterToolCall>()

        fun flushCalls() {
            if (pendingCalls.isNotEmpty()) {
                messages += OpenRouterMessage(role = "assistant", tool_calls = pendingCalls.toList())
                pendingCalls.clear()
            }
        }

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    flushCalls()
                    messages.add(
                        OpenRouterMessage(
                            role = "system",
                            content = message.content
                        )
                    )
                }

                is Message.User -> {
                    flushCalls()
                    messages.add(
                        OpenRouterMessage(
                            role = "user",
                            content = message.content
                        )
                    )
                }

                is Message.Assistant -> {
                    flushCalls()
                    messages.add(
                        OpenRouterMessage(
                            role = "assistant",
                            content = message.content
                        )
                    )
                }

                is Message.Tool.Result -> {
                    flushCalls()
                    messages.add(
                        OpenRouterMessage(
                            role = "tool",
                            content = message.content,
                            tool_call_id = message.id
                        )
                    )
                }

                is Message.Tool.Call -> pendingCalls += OpenRouterToolCall(
                    id = message.id ?: UUID.random().toString(),
                    function = OpenRouterFunction(message.tool, message.content)
                )
            }
        }
        flushCalls()

        val openRouterTools = tools.map { tool ->
            val propertiesMap = mutableMapOf<String, JsonElement>()

            // Add required parameters
            tool.requiredParameters.forEach { param ->
                propertiesMap[param.name] = buildOpenRouterParam(param)
            }

            // Add optional parameters
            tool.optionalParameters.forEach { param ->
                propertiesMap[param.name] = buildOpenRouterParam(param)
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

            OpenRouterTool(
                function = OpenRouterToolFunction(
                    name = tool.name,
                    description = tool.description,
                    parameters = parametersObject
                )
            )
        }

        return OpenRouterRequest(
            model = model.id,
            messages = messages,
            temperature = if (model.capabilities.contains(LLMCapability.Temperature)) prompt.params.temperature else null,
            tools = if (tools.isNotEmpty()) openRouterTools else null,
            stream = stream
        )
    }

    private fun buildOpenRouterParam(param: ToolParameterDescriptor): JsonObject = buildJsonObject {
        put("description", JsonPrimitive(param.description))
        fillOpenRouterParamType(param.type)
    }

    private fun JsonObjectBuilder.fillOpenRouterParamType(type: ToolParameterType) {
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
                    fillOpenRouterParamType(type.itemsType)
                })
            }
        }
    }

    private fun processOpenRouterResponse(response: OpenRouterResponse): List<Message.Response> {
        if (response.choices.isEmpty()) {
            logger.error { "Empty choices in OpenRouter response" }
            throw IllegalStateException("Empty choices in OpenRouter response")
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
                logger.error { "Unexpected response from OpenRouter: no tool calls and no content" }
                throw IllegalStateException("Unexpected response from OpenRouter: no tool calls and no content")
            }
        }
    }
}

internal expect fun engineFactoryProvider(): HttpClientEngineFactory<*>