package ai.koog.prompt.executor.ollama.client.dto

import ai.koog.prompt.executor.clients.serialization.AdditionalPropertiesFlatteningSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Message for the chat API.
 */
@Serializable
internal data class OllamaChatMessageDTO(
    val role: String,
    val content: String,
    val images: List<String>? = null,
    @SerialName("tool_calls") val toolCalls: List<OllamaToolCallDTO>? = null
)

/**
 * Tool call for the chat API.
 */
@Serializable
internal data class OllamaToolCallDTO(
    val function: Call
) {
    /**
     * Tool function for the chat API.
     */
    @Serializable
    internal data class Call(
        val name: String,
        val arguments: JsonElement
    )
}

/**
 * Tool definition for the chat API.
 */
@Serializable
internal data class OllamaToolDTO(
    val type: String,
    val function: Definition
) {
    /**
     * Tool definition for the chat API.
     */
    @Serializable
    internal data class Definition(
        val name: String,
        val description: String,
        val parameters: JsonElement
    )
}

/**
 * Request for the /api/chat endpoint.
 */
@Serializable
internal data class OllamaChatRequestDTO(
    val model: String,
    val messages: List<OllamaChatMessageDTO>,
    val tools: List<OllamaToolDTO>? = null,
    val format: JsonElement? = null,
    val options: Options? = null,
    val stream: Boolean,
    @SerialName("keep_alive") val keepAlive: String? = null,
    val additionalProperties: Map<String, JsonElement>? = null,
) {
    /**
     * Model options for generation.
     */
    @Serializable
    internal data class Options(
        val temperature: Double? = null,
    )
}

/**
 * Response from the /api/chat endpoint.
 */
@Serializable
internal data class OllamaChatResponseDTO(
    val model: String,
    val message: OllamaChatMessageDTO? = null,
    val done: Boolean,
    @SerialName("prompt_eval_count") val promptEvalCount: Int? = null,
    @SerialName("eval_count") val evalCount: Int? = null
)

/**
 * Error response from the /api/chat endpoint.
 */
@Serializable
internal data class OllamaErrorResponseDTO(val error: String)

/**
 * Represents a request to generate an embedding using a specific model.
 *
 * The request includes the model to be used and the prompt text for which the embedding is to be generated.
 *
 * @property model The identifier of the model to be used for generating the embedding.
 * @property prompt The input text for which the embedding is to be generated.
 */
@Serializable
internal data class EmbeddingRequestDTO(
    val model: String,
    val prompt: String
)

/**
 * Represents the response for an embedding operation, containing the result of the operation.
 *
 * This class is used for deserializing responses containing vector embeddings that may be
 * associated with a specific model.
 *
 * @property embedding The list of double values representing the computed embedding or vector.
 *                     Each value corresponds to a specific dimension in the generated embedding space.
 * @property modelId An optional identifier for the model that generated the embedding.
 */
@Serializable
internal data class EmbeddingResponseDTO(
    val embedding: List<Double>,
    @SerialName("model") val modelId: String? = null
)

internal object OllamaChatRequestDTOSerializer :
    AdditionalPropertiesFlatteningSerializer<OllamaChatRequestDTO>(OllamaChatRequestDTO.serializer())
