package ai.jetbrains.code.prompt.executor.clients.anthropic

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.SuitableForIO
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.clients.ConnectionTimeoutConfig
import ai.jetbrains.code.prompt.executor.clients.DirectLLMClient
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
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/**
 * Represents the settings for configuring an Anthropic client, including model mapping, base URL, and API version.
 *
 * @property modelVersionsMap Maps specific `LLModel` instances to their corresponding model version strings.
 * This determines which Anthropic model versions are used for operations.
 * @property baseUrl The base URL for accessing the Anthropic API. Defaults to "https://api.anthropic.com".
 * @property apiVersion The version of the Anthropic API to be used. Defaults to "2023-06-01".
 */
class AnthropicClientSettings(
    val modelVersionsMap: Map<LLModel, String> = DEFAULT_ANTHROPIC_MODEL_VERSIONS_MAP,
    val baseUrl: String = "https://api.anthropic.com",
    val apiVersion: String = "2023-06-01",
    val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
)

/**
 * A client implementation for interacting with Anthropic's API in a suspendable and direct manner.
 *
 * This class supports functionalities for executing text prompts and streaming interactions with the Anthropic API.
 * It leverages Kotlin Coroutines to handle asynchronous operations and provides full support for configuring HTTP
 * requests, including timeout handling and JSON serialization.
 *
 * @constructor Creates an instance of the AnthropicSuspendableDirectClient.
 * @param apiKey The API key required to authenticate with the Anthropic service.
 * @param settings Configurable settings for the Anthropic client, which include the base URL and other options.
 * @param baseClient An optional custom configuration for the underlying HTTP client, defaulting to a Ktor client.
 */
open class AnthropicDirectLLMClient(
    private val apiKey: String,
    private val settings: AnthropicClientSettings = AnthropicClientSettings(),
    baseClient: HttpClient = HttpClient(engineFactoryProvider())
) : DirectLLMClient {

    companion object {
        private val logger =
            LoggerFactory.create("ai.jetbrains.code.prompt.executor.clients.anthropic.HTTPBasedAnthropicSuspendableDirectClient")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true // Ensure default values are included in serialization
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
    private data class AnthropicMessageRequest(
        val model: String,
        val messages: List<AnthropicMessage>,
        val max_tokens: Int = 2048,
        val temperature: Double? = null,
        val system: String? = null,
        val tools: List<AnthropicTool>? = null,
        val stream: Boolean = false
    )

    @Serializable
    private data class AnthropicMessage(
        val role: String,
        val content: List<AnthropicContent>
    )

    @Serializable
    private sealed class AnthropicContent {
        @Serializable
        @SerialName("text")
        data class Text(val text: String) : AnthropicContent()

        @Serializable
        @SerialName("tool_use")
        data class ToolUse(
            val id: String,
            val name: String,
            val input: JsonObject
        ) : AnthropicContent()

        @Serializable
        @SerialName("tool_result")
        data class ToolResult(
            val tool_use_id: String,
            val content: String
        ) : AnthropicContent()
    }

    @Serializable
    private data class AnthropicTool(
        val name: String,
        val description: String,
        val input_schema: AnthropicToolSchema
    )

    @Serializable
    private data class AnthropicToolSchema(
        val type: String = "object",
        val properties: JsonObject,
        val required: List<String>
    )

    @Serializable
    private data class AnthropicResponse(
        val id: String,
        val type: String,
        val role: String,
        val content: List<AnthropicResponseContent>,
        val model: String,
        val stop_reason: String? = null,
        val usage: AnthropicUsage? = null
    )

    @Serializable
    private sealed class AnthropicResponseContent {
        @Serializable
        @SerialName("text")
        data class Text(val text: String) : AnthropicResponseContent()

        @Serializable
        @SerialName("tool_use")
        data class ToolUse(
            val id: String,
            val name: String,
            val input: JsonObject
        ) : AnthropicResponseContent()
    }

    @Serializable
    private data class AnthropicUsage(
        val input_tokens: Int,
        val output_tokens: Int
    )

    @Serializable
    private data class AnthropicStreamResponse(
        val type: String,
        val delta: AnthropicStreamDelta? = null,
        val message: AnthropicResponse? = null
    )

    @Serializable
    private data class AnthropicStreamDelta(
        val type: String,
        val text: String? = null,
        val tool_use: AnthropicResponseContent.ToolUse? = null
    )

    override suspend fun execute(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools" }

        val request = createAnthropicRequest(prompt, tools, false)
        val requestBody = json.encodeToString(request)

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post("${settings.baseUrl}/v1/messages") {
                contentType(ContentType.Application.Json)
                header("x-api-key", apiKey)
                header("anthropic-version", settings.apiVersion)
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val anthropicResponse = response.body<AnthropicResponse>()
                processAnthropicResponse(anthropicResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from Anthropic API: ${response.status}: $errorBody" }
                throw IllegalStateException("Error from Anthropic API: ${response.status}: $errorBody")
            }
        }
    }

    override suspend fun executeStreaming(prompt: Prompt): Flow<String> {
        logger.debug { "Executing streaming prompt: $prompt without tools" }

        val request = createAnthropicRequest(prompt, emptyList(), true)
        val requestBody = json.encodeToString(request)

        return callbackFlow {
            withContext(Dispatchers.SuitableForIO) {
                try {
                    httpClient.preparePost("${settings.baseUrl}/v1/messages") {
                        contentType(ContentType.Application.Json)
                        header("x-api-key", apiKey)
                        header("anthropic-version", settings.apiVersion)
                        setBody(requestBody)
                    }.execute { response ->
                        if (response.status.isSuccess()) {
                            val channel = response.bodyAsChannel()

                            while (!channel.isClosedForRead) {
                                val line = channel.readUTF8Line() ?: continue

                                if (line.startsWith("data: ") && line != "data: [DONE]") {
                                    val jsonData = line.substring(6)
                                    try {
                                        val streamResponse = json.decodeFromString<AnthropicStreamResponse>(jsonData)
                                        streamResponse.delta?.text?.let { text ->
                                            trySend(text)
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

    private fun createAnthropicRequest(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        stream: Boolean
    ): AnthropicMessageRequest {
        var systemMessage: String? = null
        val messages = mutableListOf<AnthropicMessage>()

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    // TODO: figure out how to make Anthropic accept > 1 system messages.
                    if (systemMessage != null) {
                        error("Only one system message is allowed for Anthropic client")
                    }
                    systemMessage = message.content
                }

                is Message.User -> {
                    messages.add(
                        AnthropicMessage(
                            role = "user",
                            content = listOf(AnthropicContent.Text(message.content))
                        )
                    )
                }

                is Message.Assistant -> {
                    messages.add(
                        AnthropicMessage(
                            role = "assistant",
                            content = listOf(AnthropicContent.Text(message.content))
                        )
                    )
                }

                is Message.Tool.Result -> {
                    val lastMessage = messages.lastOrNull()
                    if (lastMessage?.role == "user") {
                        // Add tool result to the last user message
                        val newContent = lastMessage.content.toMutableList()
                        newContent.add(
                            AnthropicContent.ToolResult(
                                tool_use_id = message.id ?: "",
                                content = message.content
                            )
                        )
                        messages[messages.lastIndex] = lastMessage.copy(content = newContent)
                    } else {
                        // Create a new user message with the tool result
                        messages.add(
                            AnthropicMessage(
                                role = "user",
                                content = listOf(
                                    AnthropicContent.ToolResult(
                                        tool_use_id = message.id ?: "",
                                        content = message.content
                                    )
                                )
                            )
                        )
                    }
                }

                is Message.Tool.Call -> {
                    val lastMessage = messages.lastOrNull()
                    if (lastMessage?.role == "assistant") {
                        // Add tool call to the last assistant message
                        val newContent = lastMessage.content.toMutableList()
                        newContent.add(
                            AnthropicContent.ToolUse(
                                id = message.id ?: UUID.random().toString(),
                                name = message.tool,
                                input = Json.parseToJsonElement(message.content).jsonObject
                            )
                        )
                        messages[messages.lastIndex] = lastMessage.copy(content = newContent)
                    } else {
                        // Create a new assistant message with the tool call
                        messages.add(
                            AnthropicMessage(
                                role = "assistant",
                                content = listOf(
                                    AnthropicContent.ToolUse(
                                        id = message.id ?: UUID.random().toString(),
                                        name = message.tool,
                                        input = Json.parseToJsonElement(message.content).jsonObject
                                    )
                                )
                            )
                        )
                    }
                }
            }
        }

        val anthropicTools = tools.map { tool ->
            val properties = mutableMapOf<String, JsonElement>()

            (tool.requiredParameters + tool.optionalParameters).forEach { param ->
                val typeMap = getTypeMapForParameter(param.type)

                properties[param.name] = JsonObject(
                    mapOf("description" to JsonPrimitive(param.description)) +
                            typeMap.mapValues {
                                when (val value = it.value) {
                                    is List<*> -> JsonArray(value.map { item -> JsonPrimitive(item.toString()) })
                                    is Map<*, *> -> {
                                        val jsonMap = value.entries.associate { entry ->
                                            entry.key.toString() to JsonPrimitive(entry.value.toString())
                                        }
                                        JsonObject(jsonMap)
                                    }

                                    else -> JsonPrimitive(value.toString())
                                }
                            }
                )
            }

            AnthropicTool(
                name = tool.name,
                description = tool.description,
                input_schema = AnthropicToolSchema(
                    properties = JsonObject(properties),
                    required = tool.requiredParameters.map { it.name }
                )
            )
        }

        // Always include max_tokens as it's required by the API
        return AnthropicMessageRequest(
            model = settings.modelVersionsMap[prompt.model]
                ?: throw IllegalArgumentException("Unsupported model: ${prompt.model}"),
            messages = messages,
            max_tokens = 2048, // This is required by the API
            temperature = prompt.params.temperature ?: 0.7, // Default temperature if not provided
            system = systemMessage,
            tools = if (tools.isNotEmpty()) anthropicTools else emptyList(), // Always provide a list for tools
            stream = stream
        )
    }

    private fun processAnthropicResponse(response: AnthropicResponse): List<Message.Response> {
        val responses = mutableListOf<Message.Response>()

        for (content in response.content) {
            when (content) {
                is AnthropicResponseContent.Text -> {
                    responses.add(Message.Assistant(content.text))
                }

                is AnthropicResponseContent.ToolUse -> {
                    responses.add(
                        Message.Tool.Call(
                            id = content.id,
                            tool = content.name,
                            content = content.input.toString()
                        )
                    )
                }
            }
        }

        // Fix the situation when the model decides to both call tools and talk
        if (responses.any { it is Message.Tool.Call }) {
            return responses.filterIsInstance<Message.Tool.Call>()
        }

        return responses
    }

    /**
     * Helper function to get the type map for a parameter type without using smart casting
     */
    private fun getTypeMapForParameter(type: ToolParameterType): Map<String, Any> {
        return when (type) {
            ToolParameterType.Boolean -> mapOf("type" to "boolean")
            ToolParameterType.Float -> mapOf("type" to "number")
            ToolParameterType.Integer -> mapOf("type" to "integer")
            ToolParameterType.String -> mapOf("type" to "string")
            is ToolParameterType.Enum -> mapOf("type" to "string", "enum" to type.entries)
            is ToolParameterType.List -> mapOf("type" to "array", "items" to getTypeMapForParameter(type.itemsType))
        }
    }
}

/**
 * Platform-specific HTTP client engine factory provider.
 * Each platform (JVM, Native, JS) implements this function to provide appropriate HTTP client engine.
 *
 * @return HTTP client engine factory for the current platform
 */
internal expect fun engineFactoryProvider(): HttpClientEngineFactory<*>
