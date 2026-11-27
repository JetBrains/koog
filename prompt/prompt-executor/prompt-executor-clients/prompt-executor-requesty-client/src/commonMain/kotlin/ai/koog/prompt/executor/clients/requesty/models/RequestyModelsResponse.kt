package ai.koog.prompt.executor.clients.requesty.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Requesty Models list API Response
 * https://docs.requesty.ai
 */
@Serializable
internal data class RequestyModelsResponse(
    val data: List<RequestyModel>,
    @SerialName("object")
    val objectType: String? = null,
)

/**
 * Requesty Model API Response
 *
 * Only the fields used by the client are declared; the rest of the payload is ignored.
 */
@Serializable
internal data class RequestyModel(
    val id: String,
    @SerialName("object")
    val objectType: String? = null,
    @SerialName("owned_by")
    val ownedBy: String? = null,
    @SerialName("context_window")
    val contextWindow: Long? = null,
    @SerialName("max_output_tokens")
    val maxOutputTokens: Long? = null,
)
