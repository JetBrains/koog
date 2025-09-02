package ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
    val system: List<NovaSystemMessage>? = null,
    @SerialName("toolConfig")
    val toolConfig: NovaToolConfig? = null,
)

@Serializable
internal data class NovaMessage(
    @SerialName("role")
    val role: String,
    @SerialName("content")
    val content: List<NovaContent>
) {

    internal constructor(
        role: String,
        content: NovaContent,
    ) : this(role, listOf(content))
}

@Serializable
internal data class NovaContent(
    @SerialName("text")
    val text: String? = null,
    @SerialName("toolUse")
    val toolUse: NovaToolUse? = null,
    @SerialName("toolResult")
    val toolResult: NovaToolResult? = null
)

@Serializable
internal data class NovaToolUse(
    @SerialName("toolUseId")
    val toolUseId: String,
    @SerialName("name")
    val name: String,
    @SerialName("input")
    val input: JsonObject
)

@Serializable
internal data class NovaToolResult(
    @SerialName("toolUseId")
    val toolUseId: String,
    @SerialName("content")
    val content: List<NovaToolResultContent>,
    @SerialName("status")
    val status: String,
) {
    internal constructor(
        toolUseId: String,
        content: NovaToolResultContent,
        status: String,
    ) : this(toolUseId, listOf(content), status)
}

@Serializable
internal data class NovaToolResultContent(
    @SerialName("json")
    val result: String
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
    val maxTokens: Int? = MAX_TOKENS_DEFAULT,
    @SerialName("stopSequences")
    val stopSequences: List<String>? = null,
) {
    /**
     * Companion object with default values for request
     */
    companion object {
        /**
         * Default max tokens
         */
        const val MAX_TOKENS_DEFAULT: Int = 4096
    }
}

/**
 * Tool-related data classes for Amazon Nova models
 */
@Serializable
internal data class NovaToolConfig(
    @SerialName("tools")
    val tools: List<NovaToolSpec>? = null
)

@Serializable
internal data class NovaToolSpec(
    @SerialName("toolSpec")
    val toolSpec: NovaToolSpecDetails
)

@Serializable
internal data class NovaToolSpecDetails(
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String,
    @SerialName("inputSchema")
    val inputSchema: NovaInputSchema
)

@Serializable
internal data class NovaInputSchema(
    @SerialName("json")
    val json: NovaJsonSchema
)

@Serializable
internal data class NovaJsonSchema(
    @SerialName("type")
    val type: String = "object",
    @SerialName("properties")
    val properties: JsonObject,
    @SerialName("required")
    val required: List<String>
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
    val totalTokens: Int? = null,
    @SerialName("cacheReadInputTokenCount")
    val cacheReadInputTokenCount: Int? = null,
    @SerialName("cacheWriteInputTokenCount")
    val cacheWriteInputTokenCount: Int? = null,
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
