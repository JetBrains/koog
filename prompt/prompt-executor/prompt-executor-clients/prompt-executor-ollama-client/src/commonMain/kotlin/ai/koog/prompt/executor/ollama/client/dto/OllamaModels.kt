package ai.koog.prompt.executor.ollama.client.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Response from the /api/tags endpoint.
 */
@Serializable
public data class OllamaListModelsResponseDTO(
    val models: List<Model>,
) {
    /**
     * Model definition.
     */
    @Serializable
    public data class Model(
        val name: String,
    )
}

/**
 * Message for the /api/show API.
 */
@Serializable
public data class OllamaShowModelRequestDTO(
    val model: String,
)

/**
 * Response for the /api/show API.
 */
@Serializable
public data class OllamaShowModelResponseDTO(
    val capabilities: List<Capability>,
) {
    @Serializable
    public enum class Capability {
        @SerialName("completion")
        COMPLETION,
        @SerialName("embedding")
        EMBEDDING,
        @SerialName("insert")
        INSERT,
        @SerialName("vision")
        VISION,
        @SerialName("tools")
        TOOLS,
    }
}

/**
 * Message for the chat API.
 */
@Serializable
public data class OllamaChatMessageDTO(
    val role: String,
    val content: String,
    val images: List<String>? = null,
    @SerialName("tool_calls") val toolCalls: List<OllamaToolCallDTO>? = null
)

/**
 * Tool call for the chat API.
 */
@Serializable
public data class OllamaToolCallDTO(
    val function: Call
) {
    /**
     * Tool function for the chat API.
     */
    @Serializable
    public data class Call(
        val name: String,
        val arguments: JsonElement
    )
}


/**
 * Tool definition for the chat API.
 */
@Serializable
public data class OllamaToolDTO(
    val type: String,
    val function: Definition
) {
    /**
     * Tool definition for the chat API.
     */
    @Serializable
    public data class Definition(
        val name: String,
        val description: String,
        val parameters: JsonElement
    )
}

/**
 * Request for the /api/chat endpoint.
 */
@Serializable
public data class OllamaChatRequestDTO(
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
    public data class Options(
        val temperature: Double? = null,
    )
}

/**
 * Response from the /api/chat endpoint.
 */
@Serializable
public data class OllamaChatResponseDTO(
    val model: String,
    val message: OllamaChatMessageDTO? = null,
    val done: Boolean
)

/**
 * Represents a request to generate an embedding using a specific model.
 *
 * The request includes the model to be used and the prompt text for which the embedding is to be generated.
 *
 * @property model The identifier of the model to be used for generating the embedding.
 * @property prompt The input text for which the embedding is to be generated.
 */
@Serializable
public data class EmbeddingRequest(
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
public data class EmbeddingResponse(
    val embedding: List<Double>,
    @SerialName("model") val modelId: String? = null
)
