package ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request data classes for Amazon Nova models
 */
@Serializable
internal data class NovaRequest(
    @SerialName("messages")
    val messages: List<NovaMessage>,
    @SerialName("inferenceConfig")
    val inferenceConfig: NovaInferenceConfig? = null,
    @SerialName("system")
    val system: List<NovaSystemMessage>? = null
)

@Serializable
internal data class NovaMessage(
    @SerialName("role")
    val role: String,
    @SerialName("content")
    val content: List<NovaContent>
)

@Serializable
internal data class NovaContent(
    @SerialName("text")
    val text: String
)

@Serializable
internal data class NovaSystemMessage(
    @SerialName("text")
    val text: String
)

@Serializable
internal data class NovaInferenceConfig(
    @SerialName("temperature")
    val temperature: Double? = null,
    @SerialName("topP")
    val topP: Double? = null,
    @SerialName("topK")
    val topK: Int? = null,
    @SerialName("maxTokens")
    val maxTokens: Int? = null
)

/**
 * Response data classes for Amazon Nova models
 */
@Serializable
internal data class NovaResponse(
    @SerialName("output")
    val output: NovaOutput,
    @SerialName("usage")
    val usage: NovaUsage? = null,
    @SerialName("stopReason")
    val stopReason: String? = null
)

@Serializable
internal data class NovaOutput(
    @SerialName("message")
    val message: NovaMessage
)

@Serializable
internal data class NovaUsage(
    @SerialName("inputTokens")
    val inputTokens: Int? = null,
    @SerialName("outputTokens")
    val outputTokens: Int? = null,
    @SerialName("totalTokens")
    val totalTokens: Int? = null
)

/**
 * Streaming response data classes for Amazon Nova models
 */
@Serializable
internal data class NovaStreamChunk(
    @SerialName("contentBlockDelta")
    val contentBlockDelta: NovaContentBlockDelta? = null,
    @SerialName("messageStop")
    val messageStop: NovaMessageStop? = null,
    @SerialName("metadata")
    val metadata: NovaStreamMetadata? = null
)

@Serializable
internal data class NovaContentBlockDelta(
    @SerialName("delta")
    val delta: NovaContentDelta
)

@Serializable
internal data class NovaContentDelta(
    @SerialName("text")
    val text: String? = null
)

@Serializable
internal data class NovaMessageStop(
    @SerialName("stopReason")
    val stopReason: String
)

@Serializable
internal data class NovaStreamMetadata(
    @SerialName("usage")
    val usage: NovaUsage? = null
)
