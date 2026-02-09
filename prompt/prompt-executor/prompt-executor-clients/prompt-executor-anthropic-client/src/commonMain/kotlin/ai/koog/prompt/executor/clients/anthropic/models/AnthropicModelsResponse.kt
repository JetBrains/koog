package ai.koog.prompt.executor.clients.anthropic.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Anthropic Models list API Response
 * https://platform.claude.com/docs/en/api/models/list
 */
@Serializable
internal data class AnthropicModelsResponse(
    val data: List<AnthropicModel>,
    @SerialName("first_id")
    val firstId: String,
    @SerialName("has_more")
    val hasMore: Boolean,
    @SerialName("last_id")
    val lastId: String,
)

/**
 * Anthropic Model API Response
 */
@Serializable
internal data class AnthropicModel(
    val id: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("display_name")
    val displayName: String,
    val type: String,
)
