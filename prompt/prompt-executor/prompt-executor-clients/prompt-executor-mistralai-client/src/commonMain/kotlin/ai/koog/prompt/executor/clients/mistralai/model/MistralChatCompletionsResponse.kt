package ai.koog.prompt.executor.clients.mistralai.model

import ai.koog.prompt.executor.clients.InternalLLMClientApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@InternalLLMClientApi
@Serializable
internal data class UsageInfo(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int? = null
)

@InternalLLMClientApi
@Serializable
internal data class MistralChatCompletionsResponse(
    val id: String,
    val model: String,
    val usage: UsageInfo,
    val created: Long,
    val choices: List<MistralAIChoice>
)

@InternalLLMClientApi
@Serializable
internal data class MistralAIChoice(
    val index: Long,
    val message: MistralAIAssistantMessage,
    val finishReason: FinishReason
)

@InternalLLMClientApi
@Serializable
internal enum class FinishReason {
    @SerialName("stop")
    STOP,

    @SerialName("length")
    LENGTH,

    @SerialName("model_length")
    MODEL_LENGTH,

    @SerialName("error")
    ERROR,

    @SerialName("tool_calls")
    TOOL_CALLS
}

@InternalLLMClientApi
@Serializable
internal data class MistralAIAssistantMessage(
    val content: String? = null,
    val toolCalls: List<MistralAIToolCall>? = null,
    val prefix: Boolean = false,
    val role: String = "assistant"
)
