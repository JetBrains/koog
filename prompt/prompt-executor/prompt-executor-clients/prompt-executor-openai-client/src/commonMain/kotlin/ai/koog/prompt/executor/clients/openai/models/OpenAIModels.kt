package ai.koog.prompt.executor.clients.openai.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenAIModelsResponse(
    val data: List<OpenAIModel>,
    @SerialName("object")
    val objectType: String
)

@Serializable
internal data class OpenAIModel(
    val id: String,
    @SerialName("object")
    val objectType: String,
    val created: Long,
    @SerialName("owned_by")
    val ownedBy: String
)
