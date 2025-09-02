package ai.koog.prompt.executor.clients.openai.models

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenAIEmbeddingRequest(
    val model: String,
    val input: String
)

@Serializable
internal data class OpenAIEmbeddingResponse(
    val data: List<OpenAIEmbeddingData>,
    val model: String,
    val usage: OpenAIUsage? = null
)

@Serializable
internal data class OpenAIEmbeddingData(
    val embedding: List<Double>,
    val index: Int
)
