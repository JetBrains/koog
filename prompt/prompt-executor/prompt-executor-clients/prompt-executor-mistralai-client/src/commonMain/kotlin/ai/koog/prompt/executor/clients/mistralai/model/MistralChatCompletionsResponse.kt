package ai.koog.prompt.executor.clients.mistralai.model

import ai.koog.prompt.executor.clients.InternalLLMClientApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@InternalLLMClientApi
@Serializable
public data class UsageInfo(
    @SerialName("prompt_tokens") val inputTokens: Int,
    @SerialName("completion_tokens") val outputTokens: Int,
    val totalTokens: Int? = null
)

@InternalLLMClientApi
@Serializable
public data class MistralChatCompletionsResponse(
    val id: String,
    val `object`: String,
    val model: String,
    val usage: UsageInfo,
    val created: Long,
    val choices: List<MistralChatCompletionsResponseChoice>
)

@InternalLLMClientApi
@Serializable
public data class MistralChatCompletionsResponseChoice(
    val index: Long,
    val message: MistralChatCompletionsResponseChoiceMessage,
    val finishReason: String
)

@InternalLLMClientApi
@Serializable
public data class MistralChatCompletionsResponseChoiceMessage(
    val content: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val prefix: Boolean = false,
    val role: String
)