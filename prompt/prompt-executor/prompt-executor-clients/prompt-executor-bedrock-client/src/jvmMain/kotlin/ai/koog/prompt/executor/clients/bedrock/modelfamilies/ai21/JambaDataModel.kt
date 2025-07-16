package ai.koog.prompt.executor.clients.bedrock.modelfamilies.ai21

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class JambaRequest(
    public val model: String,
    public val messages: List<JambaMessage>,
    @SerialName("max_tokens")
    public val maxTokens: Int? = null,
    public val temperature: Double? = null,
    @SerialName("top_p")
    public val topP: Double? = null,
    public val stop: List<String>? = null,
    public val n: Int? = null,
    public val stream: Boolean? = null,
    public val tools: List<JambaTool>? = null,
    @SerialName("response_format")
    public val responseFormat: JambaResponseFormat? = null
)

@Serializable
internal data class JambaMessage(
    public val role: String,
    public val content: String? = null,
    @SerialName("tool_calls")
    public val toolCalls: List<JambaToolCall>? = null,
    @SerialName("tool_call_id")
    public val toolCallId: String? = null
)

@Serializable
internal data class JambaTool(
    public val type: String = "function",
    public val function: JambaFunction
)

@Serializable
internal data class JambaFunction(
    public val name: String,
    public val description: String,
    public val parameters: JsonObject
)

@Serializable
internal data class JambaToolCall(
    public val id: String,
    public val type: String = "function",
    public val function: JambaFunctionCall
)

@Serializable
internal data class JambaFunctionCall(
    public val name: String,
    public val arguments: String
)

@Serializable
internal data class JambaResponseFormat(
    public val type: String
)

@Serializable
internal data class JambaResponse(
    public val id: String,
    public val model: String,
    public val choices: List<JambaChoice>,
    public val usage: JambaUsage? = null
)

@Serializable
internal data class JambaChoice(
    public val index: Int,
    public val message: JambaMessage,
    @SerialName("finish_reason")
    public val finishReason: String? = null
)

@Serializable
internal data class JambaUsage(
    @SerialName("prompt_tokens")
    public val promptTokens: Int,
    @SerialName("completion_tokens")
    public val completionTokens: Int,
    @SerialName("total_tokens")
    public val totalTokens: Int
)

@Serializable
internal data class JambaStreamResponse(
    public val id: String,
    public val choices: List<JambaStreamChoice>,
    public val usage: JambaUsage? = null
)

@Serializable
internal data class JambaStreamChoice(
    public val index: Int,
    public val delta: JambaStreamDelta,
    @SerialName("finish_reason")
    public val finishReason: String? = null
)

@Serializable
internal data class JambaStreamDelta(
    public val role: String? = null,
    public val content: String? = null,
    @SerialName("tool_calls")
    public val toolCalls: List<JambaToolCall>? = null
)
