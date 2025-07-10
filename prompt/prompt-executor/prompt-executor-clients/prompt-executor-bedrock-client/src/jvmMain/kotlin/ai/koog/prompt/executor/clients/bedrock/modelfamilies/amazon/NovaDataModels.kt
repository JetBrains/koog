package ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request data classes for Amazon Nova models
 */
@Serializable
public data class NovaRequest(
    @SerialName("messages")
    val messages: List<NovaMessage>,
    @SerialName("inferenceConfig")
    val inferenceConfig: NovaInferenceConfig? = null,
    @SerialName("system")
    val system: List<NovaSystemMessage>? = null
)

@Serializable
public data class NovaMessage(
    @SerialName("role")
    val role: String,
    @SerialName("content")
    val content: List<NovaContent>
)

@Serializable
public data class NovaContent(
    @SerialName("text")
    val text: String
)

@Serializable
public data class NovaSystemMessage(
    @SerialName("text")
    val text: String
)

@Serializable
public data class NovaInferenceConfig(
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
public data class NovaResponse(
    @SerialName("output")
    val output: NovaOutput,
    @SerialName("usage")
    val usage: NovaUsage? = null,
    @SerialName("stopReason")
    val stopReason: String? = null
)

@Serializable
public data class NovaOutput(
    @SerialName("message")
    val message: NovaMessage
)

@Serializable
public data class NovaUsage(
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
public data class NovaStreamChunk(
    @SerialName("contentBlockDelta")
    val contentBlockDelta: NovaContentBlockDelta? = null,
    @SerialName("messageStop")
    val messageStop: NovaMessageStop? = null,
    @SerialName("metadata")
    val metadata: NovaStreamMetadata? = null
)

@Serializable
public data class NovaContentBlockDelta(
    @SerialName("delta")
    val delta: NovaContentDelta
)

@Serializable
public data class NovaContentDelta(
    @SerialName("text")
    val text: String? = null
)

@Serializable
public data class NovaMessageStop(
    @SerialName("stopReason")
    val stopReason: String
)

@Serializable
public data class NovaStreamMetadata(
    @SerialName("usage")
    val usage: NovaUsage? = null
)
