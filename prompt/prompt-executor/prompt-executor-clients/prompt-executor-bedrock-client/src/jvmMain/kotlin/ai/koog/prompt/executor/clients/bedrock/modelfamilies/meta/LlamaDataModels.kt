package ai.koog.prompt.executor.clients.bedrock.modelfamilies.meta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request structure for Meta Llama models
 */
@Serializable
public data class LlamaRequest(
    @SerialName("prompt")
    val prompt: String,
    @SerialName("max_gen_len")
    val maxGenLen: Int = 2048,
    @SerialName("temperature")
    val temperature: Double? = null
)

/**
 * Response structure for Meta Llama models
 */
@Serializable
public data class LlamaResponse(
    @SerialName("generation")
    val generation: String,
    @SerialName("prompt_token_count")
    val promptTokenCount: Int? = null,
    @SerialName("generation_token_count")
    val generationTokenCount: Int? = null,
    @SerialName("stop_reason")
    val stopReason: String? = null
)

/**
 * Streaming chunk structure for Meta Llama models
 */
@Serializable
public data class LlamaStreamChunk(
    @SerialName("generation")
    val generation: String? = null,
    @SerialName("prompt_token_count")
    val promptTokenCount: Int? = null,
    @SerialName("generation_token_count")
    val generationTokenCount: Int? = null,
    @SerialName("stop_reason")
    val stopReason: String? = null
)
