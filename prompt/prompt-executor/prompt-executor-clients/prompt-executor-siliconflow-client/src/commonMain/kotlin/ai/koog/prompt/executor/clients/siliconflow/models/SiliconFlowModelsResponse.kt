package ai.koog.prompt.executor.clients.siliconflow.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SiliconFlowModelsResponse(
    val data: List<SiliconFlowModel>,
)

@Serializable
internal data class SiliconFlowModelPricing(
    val prompt: String,
    val completion: String,
    val request: String? = null,
    val image: String? = null,
    @SerialName("image_output")
    val imageOutput: String? = null,
    val audio: String? = null,
    @SerialName("input_audio_cache")
    val inputAudioCache: String? = null,
    @SerialName("web_search")
    val webSearch: String? = null,
    @SerialName("internal_reasoning")
    val internalReasoning: String? = null,
    @SerialName("input_cache_read")
    val inputCacheRead: String? = null,
    @SerialName("input_cache_write")
    val inputCacheWrite: String? = null,
    val discount: Double? = null,
)

@Serializable
internal data class SiliconFlowModelArchitecture(
    val tokenizer: String,
    @SerialName("instruct_type")
    val instructType: String? = null,
    val modality: String? = null,
    @SerialName("input_modalities")
    val inputModalities: List<String>? = null,
    @SerialName("output_modalities")
    val outputModalities: List<String>? = null,
)

@Serializable
internal data class SiliconFlowModelTopProvider(
    @SerialName("context_length")
    val contextLength: Long? = null,
    @SerialName("max_completion_tokens")
    val maxCompletionTokens: Long? = null,
    @SerialName("is_moderated")
    val isModerated: Boolean,
)

@Serializable
internal data class SiliconFlowModelPerRequestLimits(
    @SerialName("prompt_tokens")
    val promptTokens: Long,
    @SerialName("completion_tokens")
    val completionTokens: Long,
)

@Serializable
internal data class SiliconFlowModelDefaultParameters(
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("frequency_penalty")
    val frequencyPenalty: Double? = null,
)

@Serializable
internal data class SiliconFlowModel(
    val id: String,
    @SerialName("canonical_slug")
    val canonicalSlug: String,
    @SerialName("hugging_face_id")
    val huggingFaceId: String? = null,
    val name: String,
    val created: Long,
    val description: String,
    val pricing: SiliconFlowModelPricing,
    @SerialName("context_length")
    val contextLength: Long? = null,
    val architecture: SiliconFlowModelArchitecture,
    @SerialName("top_provider")
    val topProvider: SiliconFlowModelTopProvider,
    @SerialName("per_request_limits")
    val perRequestLimits: SiliconFlowModelPerRequestLimits? = null,
    @SerialName("supported_parameters")
    val supportedParameters: List<String>? = null,
    @SerialName("default_parameters")
    val defaultParameters: SiliconFlowModelDefaultParameters? = null,
)
