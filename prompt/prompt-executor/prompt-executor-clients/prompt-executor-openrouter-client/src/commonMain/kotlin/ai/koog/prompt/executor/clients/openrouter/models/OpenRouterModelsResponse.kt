package ai.koog.prompt.executor.clients.openrouter.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenRouterModelsResponse(
    val data: List<OpenRouteModel>,
)

@Serializable
internal data class OpenRouteModelPricing(
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
internal data class OpenRouteModelArchitecture(
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
internal data class OpenRouteModelTopProvider(
    @SerialName("context_length")
    val contextLength: Long? = null,
    @SerialName("max_completion_tokens")
    val maxCompletionTokens: Long? = null,
    @SerialName("is_moderated")
    val isModerated: Boolean,
)

@Serializable
internal data class OpenRouteModelPerRequestLimits(
    @SerialName("prompt_tokens")
    val promptTokens: Long,
    @SerialName("completion_tokens")
    val completionTokens: Long,
)

@Serializable
internal data class OpenRouteModelDefaultParameters(
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("frequency_penalty")
    val frequencyPenalty: Double? = null,
)

@Serializable
internal data class OpenRouteModel(
    val id: String,
    @SerialName("canonical_slug")
    val canonicalSlug: String,
    @SerialName("hugging_face_id")
    val huggingFaceId: String? = null,
    val name: String,
    val created: Long,
    val description: String,
    val pricing: OpenRouteModelPricing,
    @SerialName("context_length")
    val contextLength: Long? = null,
    val architecture: OpenRouteModelArchitecture,
    @SerialName("top_provider")
    val topProvider: OpenRouteModelTopProvider,
    @SerialName("per_request_limits")
    val perRequestLimits: OpenRouteModelPerRequestLimits? = null,
    @SerialName("supported_parameters")
    val supportedParameters: List<String>? = null,
    @SerialName("default_parameters")
    val defaultParameters: OpenRouteModelDefaultParameters? = null,
)
