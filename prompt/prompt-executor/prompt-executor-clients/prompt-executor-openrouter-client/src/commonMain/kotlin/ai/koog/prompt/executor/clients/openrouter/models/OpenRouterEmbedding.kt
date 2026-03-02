package ai.koog.prompt.executor.clients.openrouter.models

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenRouterEmbeddingRequest(
    val model: String,
    val input: String
)

@Serializable
internal data class OpenRouterEmbeddingResponse(
    val data: List<OpenRouterEmbeddingData> = emptyList(),
    val model: String? = null,
    val usage: OpenAIUsage? = null,
    val error: OpenRouterError? = null
)

@Serializable
internal data class OpenRouterEmbeddingData(
    val embedding: List<Double>,
    val index: Int
)
