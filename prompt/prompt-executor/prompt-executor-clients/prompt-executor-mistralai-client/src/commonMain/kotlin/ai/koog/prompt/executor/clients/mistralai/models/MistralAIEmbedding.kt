package ai.koog.prompt.executor.clients.mistralai.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request for Mistral AI embeddings
 *
 * @property model ID of the model to use.
 * @property input Text to embed
 * @property outputDimension The dimension of the output embeddings.
 * @property outputDtype Default: "float"
 * Enum: "float" "int8" "uint8" "binary" "ubinary"
 * The data type of the output embeddings.
 */
@Serializable
internal data class MistralAIEmbeddingRequest(
    val model: String,
    val input: String,
    val outputDimension: Int? = null,
    val outputDtype: MistralAIEmbeddingDtype? = null
)

@Serializable
internal enum class MistralAIEmbeddingDtype(val value: String) {
    @SerialName("float")
    FLOAT("float"),

    @SerialName("int8")
    INT8("int8"),

    @SerialName("uint8")
    UINT8("uint8"),

    @SerialName("binary")
    BINARY("binary"),

    @SerialName("ubinary")
    UBINARY("ubinary"),
}

@Serializable
internal data class MistralAIEmbeddingResponse(
    val id: String,
    @SerialName("object")
    val objectType: String,
    val model: String,
    val usage: MistralAIUsage,
    val data: List<MistralAIEmbeddingData>,
)

@Serializable
internal data class MistralAIEmbeddingData(
    val embedding: List<Double>,
    val index: Int
)
