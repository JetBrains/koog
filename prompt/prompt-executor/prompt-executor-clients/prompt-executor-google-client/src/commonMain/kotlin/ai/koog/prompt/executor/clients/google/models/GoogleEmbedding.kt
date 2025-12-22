package ai.koog.prompt.executor.clients.google.models

import kotlinx.serialization.Serializable

@Serializable
internal data class GoogleEmbeddingRequest(
    val model: String,
    val content: GoogleContent,
    val outputDimensionality: Int? = null,
    val taskType: String? = null,
    val title: String? = null,
)

@Serializable
internal data class GoogleEmbeddingResponse(
    val embedding: GoogleEmbeddingData
)

@Serializable
internal data class GoogleEmbeddingData(
    val values: List<Double>
)

