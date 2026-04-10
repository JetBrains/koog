package ai.koog.prompt.executor.clients.google.models

import kotlinx.serialization.Serializable

@Serializable
internal data class GoogleEmbeddingRequest(
    val model: String,
    val content: GoogleContent
)

@Serializable
internal data class GoogleEmbeddingResponse(
    val embedding: GoogleEmbeddingData
)

@Serializable
internal data class GoogleEmbeddingBatchRequest(
    val requests: List<GoogleEmbeddingRequest>
)

@Serializable
internal data class GoogleEmbeddingBatchResponse(
    val embeddings: List<GoogleEmbeddingData>
)

@Serializable
internal data class GoogleEmbeddingData(
    val values: List<Double>
)
