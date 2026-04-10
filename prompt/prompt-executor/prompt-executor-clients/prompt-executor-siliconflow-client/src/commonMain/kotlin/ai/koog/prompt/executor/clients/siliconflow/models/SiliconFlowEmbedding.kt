package ai.koog.prompt.executor.clients.siliconflow.models

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import kotlinx.serialization.Serializable

@Serializable
internal data class SiliconFlowEmbeddingRequest(
    val model: String,
    val input: String
)

@Serializable
internal data class SiliconFlowEmbeddingResponse(
    val data: List<SiliconFlowEmbeddingData> = emptyList(),
    val model: String? = null,
    val usage: OpenAIUsage? = null,
    val error: SiliconFlowError? = null
)

@Serializable
internal data class SiliconFlowEmbeddingData(
    val embedding: List<Double>,
    val index: Int
)
