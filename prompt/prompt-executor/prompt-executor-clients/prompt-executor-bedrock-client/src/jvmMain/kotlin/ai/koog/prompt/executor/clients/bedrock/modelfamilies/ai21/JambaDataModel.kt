package ai.koog.prompt.executor.clients.bedrock.modelfamilies.ai21

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class JambaRequest(
    val model: String,
    val messages: List<JambaMessage>,
    @SerialName("max_tokens") val maxTokens: Int? = MAX_TOKENS_DEFAULT,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    val stop: List<String>? = null,
    val n: Int? = null,
    val stream: Boolean? = null,
    val tools: List<JambaTool>? = null,
    @SerialName("response_format") val responseFormat: JambaResponseFormat? = null
) {
    init {
        if (maxTokens != null) {
            require(maxTokens > 0) { "maxTokens must be greater than 0, but was $maxTokens" }
        }
        if (temperature != null) {
            require(temperature >= 0) { "temperature must be greater than 0, but was $temperature" }
        }
        if (topP != null) {
            require(topP in 0.0..1.0) { "topP must be between 0 and 1, but was $topP" }
        }
    }

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

@Serializable
internal data class JambaMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<JambaToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null
)

@Serializable
internal data class JambaTool(
    val type: String = "function",
    val function: JambaFunction
)

@Serializable
internal data class JambaFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
internal data class JambaToolCall(
    val id: String,
    @EncodeDefault
    val type: String = "function",
    val function: JambaFunctionCall
)

@Serializable
internal data class JambaFunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
internal data class JambaResponseFormat(
    val type: String
)

@Serializable
internal data class JambaResponse(
    val id: String,
    val model: String,
    val choices: List<JambaChoice>,
    val usage: JambaUsage? = null
)

@Serializable
internal data class JambaChoice(
    val index: Int,
    val message: JambaMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
internal data class JambaUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

@Serializable
internal data class JambaStreamResponse(
    val id: String,
    val choices: List<JambaStreamChoice>,
    val usage: JambaUsage? = null
)

@Serializable
internal data class JambaStreamChoice(
    val index: Int,
    val delta: JambaStreamDelta,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
internal data class JambaStreamDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<JambaToolCall>? = null
)
