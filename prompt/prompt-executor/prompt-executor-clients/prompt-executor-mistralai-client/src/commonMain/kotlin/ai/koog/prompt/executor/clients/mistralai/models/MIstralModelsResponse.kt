package ai.koog.prompt.executor.clients.mistralai.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MistralModelsResponse(
    val data: List<MistralModel>,
)

@Serializable
internal data class MistralModelCapabilities(
    val classification: Boolean = false,
    @SerialName("completion_chat")
    val completionChat: Boolean = false,
    @SerialName("completion_fim")
    val completionFim: Boolean = false,
    @SerialName("fine_tuning")
    val fineTuning: Boolean = false,
    @SerialName("function_calling")
    val functionCalling: Boolean = false,
    val version: Boolean = false,
)

@Serializable
internal data class MistralModel(
    val id: String,
    val capabilities: MistralModelCapabilities,
    @SerialName("type")
    val objectType: String = "base",
    @SerialName("owned_by")
    val ownedBy: String = "mistralai"
)
