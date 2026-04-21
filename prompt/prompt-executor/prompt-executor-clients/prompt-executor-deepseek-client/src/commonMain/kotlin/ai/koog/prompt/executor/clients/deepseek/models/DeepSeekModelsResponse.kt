package ai.koog.prompt.executor.clients.deepseek.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DeepSeek Models list API Response
 */
@Serializable
internal data class DeepSeekModelsResponse(
    val data: List<DeepSeekModel>,
    @SerialName("object")
    val objectType: String
)

/**
 * DeepSeek Model API Response
 */
@Serializable
internal data class DeepSeekModel(
    val id: String,
    @SerialName("object")
    val objectType: String,
    @SerialName("owned_by")
    val ownedBy: String
)
