package ai.jetbrains.code.prompt.executor.clients.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double? = null,
    val tools: List<OpenAITool>? = null,
    val stream: Boolean = false
)

@Serializable
internal data class OpenAIMessage(
    val role: String,
    val content: String? = "",
    val tool_calls: List<OpenAIToolCall>? = null,
    val name: String? = null,
    val tool_call_id: String? = null
)

@Serializable
internal data class OpenAIToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAIFunction
)

@Serializable
internal data class OpenAIFunction(
    val name: String,
    val arguments: String
)

@Serializable
internal data class OpenAITool(
    val type: String = "function",
    val function: OpenAIToolFunction
)

@Serializable
internal data class OpenAIToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
internal data class OpenAIResponse(
    val id: String,
    @SerialName("object") val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage? = null
)

@Serializable
internal data class OpenAIChoice(
    val index: Int,
    val message: OpenAIMessage,
    val finish_reason: String? = null
)

@Serializable
internal data class OpenAIUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int? = null,
    val total_tokens: Int
)

@Serializable
internal data class OpenAIEmbeddingRequest(
    val model: String,
    val input: String
)

@Serializable
internal data class OpenAIEmbeddingResponse(
    val data: List<OpenAIEmbeddingData>,
    val model: String,
    val usage: OpenAIUsage? = null
)

@Serializable
internal data class OpenAIEmbeddingData(
    val embedding: List<Double>,
    val index: Int
)

@Serializable
internal data class OpenAIStreamResponse(
    val id: String,
    @SerialName("object") val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAIStreamChoice>
)

@Serializable
internal data class OpenAIStreamChoice(
    val index: Int,
    val delta: OpenAIStreamDelta,
    val finish_reason: String? = null
)

@Serializable
internal data class OpenAIStreamDelta(
    val role: String? = null,
    val content: String? = null,
    val tool_calls: List<OpenAIToolCall>? = null
)
