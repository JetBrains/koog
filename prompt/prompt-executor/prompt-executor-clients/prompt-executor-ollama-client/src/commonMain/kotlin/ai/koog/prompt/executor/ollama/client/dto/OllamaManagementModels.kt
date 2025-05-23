package ai.koog.prompt.executor.ollama.client.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Response from the /api/tags endpoint containing a list of available models.
 */
@Serializable
public data class OllamaModelsListResponse(
    val models: List<OllamaModelInfo>
)

/**
 * Information about a model available in Ollama.
 */
@Serializable
public data class OllamaModelInfo(
    val name: String,
    val model: String,
    val size: Long,
    @SerialName("modified_at") val modifiedAt: String,
    val digest: String,
    val details: OllamaModelDetails? = null
)

/**
 * Detailed information about a model's specifications.
 */
@Serializable
public data class OllamaModelDetails(
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
public data class OllamaShowModelRequest(
    val name: String
)

/**
 * Response from the /api/show endpoint with detailed model information.
 */
@Serializable
public data class OllamaShowModelResponse(
    val modelfile: String? = null,
    val parameters: String? = null,
    val template: String? = null,
    val details: OllamaModelDetails? = null,
    @SerialName("model_info") val modelInfo: Map<String, JsonElement>? = null,
    val capabilities: List<String>,
)

/**
 * Request to pull a model from the Ollama registry.
 */
@Serializable
public data class OllamaPullModelRequest(
    val name: String,
    val stream: Boolean = true
)

/**
 * Response from the /api/pull endpoint during model pulling.
 */
@Serializable
public data class OllamaPullModelResponse(
    val status: String,
    val digest: String? = null,
    val total: Long? = null,
    val completed: Long? = null
)

/**
 * Request to delete a model.
 */
@Serializable
public data class OllamaDeleteModelRequest(
    val name: String
)

/**
 * Response from the /api/delete endpoint.
 */
@Serializable
public data class OllamaDeleteModelResponse(
    val status: String? = null
)
