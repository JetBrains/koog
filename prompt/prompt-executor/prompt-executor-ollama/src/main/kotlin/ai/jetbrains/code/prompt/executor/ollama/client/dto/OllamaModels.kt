package ai.jetbrains.code.prompt.executor.ollama.client.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


/**
 * Message for the chat API.
 */
@Serializable
data class OllamaChatMessageDTO(
    val role: String,
    val content: String,
    val images: List<String>? = null,
    @SerialName("tool_calls") val toolCalls: List<OllamaToolCallDTO>? = null
)

/**
 * Tool call for the chat API.
 */
@Serializable
data class OllamaToolCallDTO(
    val function: Call
) {
    /**
     * Tool function for the chat API.
     */
    @Serializable
    data class Call(
        val name: String,
        val arguments: JsonElement
    )
}


/**
 * Tool definition for the chat API.
 */
@Serializable
data class OllamaToolDTO(
    val type: String,
    val function: Definition
) {
    /**
     * Tool definition for the chat API.
     */
    @Serializable
    data class Definition(
        val name: String,
        val description: String,
        val parameters: JsonElement
    )
}

/**
 * Request for the /api/chat endpoint.
 */
@Serializable
data class OllamaChatRequestDTO(
    val model: String,
    val messages: List<OllamaChatMessageDTO>,
    val tools: List<OllamaToolDTO>? = null,
    val format: JsonElement? = null,
    val options: Options? = null,
    val stream: Boolean,
    @SerialName("keep_alive") val keepAlive: String? = null
) {
    /**
     * Model options for generation.
     */
    @Serializable
    data class Options(
        val temperature: Double? = null,
    )
}

/**
 * Response from the /api/chat endpoint.
 */
@Serializable
data class OllamaChatResponseDTO(
    val model: String,
    val message: OllamaChatMessageDTO? = null,
    val done: Boolean,
)

