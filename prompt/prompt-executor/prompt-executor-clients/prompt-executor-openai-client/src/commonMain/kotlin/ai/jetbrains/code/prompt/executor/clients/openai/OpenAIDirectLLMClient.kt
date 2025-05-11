package ai.jetbrains.code.prompt.executor.clients.openai

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

class OpenAIClientSettings(
    val baseUrl: String = "https://api.openai.com/v1",
    val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
)

/**
 * Implementation of [DirectLLMClient] for OpenAI API.
 * Uses Ktor HttpClient to communicate with the OpenAI API.
 *
 * @param apiKey The API key for the OpenAI API
 * @param settings The base URL and timeouts for the OpenAI API, defaults to "https://api.openai.com/v1" and 900 s
 */
open class OpenAIDirectLLMClient(
    private val apiKey: String,
    private val settings: OpenAIClientSettings = OpenAIClientSettings(),
    baseClient: HttpClient = HttpClient(engineFactoryProvider())
) : DirectLLMClient {

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

    @Serializable
    private data class OpenAIRequest(
        val model: String,
        val messages: List<OpenAIMessage>,
        val temperature: Double? = null,
        val tools: List<OpenAITool>? = null,
        val stream: Boolean = false
    )

    @Serializable
    private data class OpenAIMessage(
        val role: String,
        val content: String? = "",
        val tool_calls: List<OpenAIToolCall>? = null,
        val name: String? = null,
        val tool_call_id: String? = null
    )

    @Serializable
    private data class OpenAIToolCall(
        val id: String,
        val type: String = "function",
        val function: OpenAIFunction
    )

    @Serializable
    private data class OpenAIFunction(
        val name: String,
        val arguments: String
    )

    @Serializable
    private data class OpenAITool(
        val type: String = "function",
        val function: OpenAIToolFunction
    )

    @Serializable
    private data class OpenAIToolFunction(
        val name: String,
        val description: String,
        val parameters: JsonObject
    )

    @Serializable
    private data class OpenAIResponse(
        val id: String,
        @SerialName("object") val objectType: String,
        val created: Long,
        val model: String,
        val choices: List<OpenAIChoice>,
        val usage: OpenAIUsage? = null
    )

    @Serializable
    private data class OpenAIChoice(
        val index: Int,
        val message: OpenAIMessage,
        val finish_reason: String? = null
    )

    @Serializable
    private data class OpenAIUsage(
        val prompt_tokens: Int,
        val completion_tokens: Int,
        val total_tokens: Int
    )

    @Serializable
    private data class OpenAIStreamResponse(
        val id: String,
        @SerialName("object") val objectType: String,
        val created: Long,
        val model: String,
        val choices: List<OpenAIStreamChoice>
    )

    @Serializable
    private data class OpenAIStreamChoice(
        val index: Int,
        val delta: OpenAIStreamDelta,
        val finish_reason: String? = null
    )

    @Serializable
    private data class OpenAIStreamDelta(
        val role: String? = null,
        val content: String? = null,
        val tool_calls: List<OpenAIToolCall>? = null
    )

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

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
}

internal expect fun engineFactoryProvider(): HttpClientEngineFactory<*>
