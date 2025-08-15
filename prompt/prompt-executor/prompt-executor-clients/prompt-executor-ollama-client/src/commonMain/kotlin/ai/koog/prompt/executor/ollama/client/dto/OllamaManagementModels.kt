package ai.koog.prompt.executor.ollama.client.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Response from the /api/tags endpoint containing a list of available models.
 */
@Serializable
internal data class OllamaModelsListResponseDTO(
    val models: List<OllamaModelInfoDTO>
)

/**
 * Information about a model available in Ollama.
 */
@Serializable
internal data class OllamaModelInfoDTO(
    val name: String,
    val model: String,
    val size: Long,
    @SerialName("modified_at") val modifiedAt: String,
    val digest: String,
    val details: OllamaModelDetailsDTO? = null
)

/**
 * Detailed information about a model's specifications.
 */
@Serializable
internal data class OllamaModelDetailsDTO(
    val format: String,
    val family: String,
    val families: List<String>? = null,
    @SerialName("parameter_size") val parameterSize: String,
    @SerialName("quantization_level") val quantizationLevel: String? = null
)

/**
 * Request to show information about a specific model.
 */
@Serializable
internal data class OllamaShowModelRequestDTO(
    val name: String
)

/**
 * Response from the /api/show endpoint with detailed model information.
 */
@Serializable
internal data class OllamaShowModelResponseDTO(
    val modelfile: String? = null,
    val parameters: String? = null,
    val template: String? = null,
    val details: OllamaModelDetailsDTO? = null,
    @SerialName("model_info") val modelInfo: Map<String, JsonElement>? = null,
    val capabilities: List<Capability>,
) {
    @Serializable
    enum class Capability {
        @SerialName("completion") COMPLETION,

        @SerialName("embedding") EMBEDDING,

        @SerialName("insert") INSERT,

        @SerialName("vision") VISION,

        @SerialName("thinking") THINKING,

        @SerialName("tools") TOOLS,
    }
}

/**
 * Request to pull a model from the Ollama registry.
 */
@Serializable
internal data class OllamaPullModelRequestDTO(
    val name: String,
    val stream: Boolean = true
)

/**
 * Response from the /api/pull endpoint during model pulling.
 */
@Serializable
internal data class OllamaPullModelResponseDTO(
    val status: String,
    val digest: String? = null,
    val total: Long? = null,
    val completed: Long? = null
)
