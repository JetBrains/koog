package ai.koog.prompt.executor.clients.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmInline

@Serializable
internal data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double? = null,
    val tools: List<OpenAITool>? = null,
    val stream: Boolean = false,
    val toolChoice: OpenAIToolChoice? = null
)

@Serializable
internal data class OpenAIMessage(
    val role: String,
    val content: String? = "",
    val toolCalls: List<OpenAIToolCall>? = null,
    val name: String? = null,
    val toolCallId: String? = null
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
    val finishReason: String? = null
)

@Serializable
internal data class OpenAIUsage(
    val promptTokens: Int,
    val completionTokens: Int? = null,
    val totalTokens: Int
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
    val finishReason: String? = null
)

@Serializable
internal data class OpenAIStreamDelta(
    val role: String? = null,
    val content: String? = null,
    val toolCalls: List<OpenAIToolCall>? = null
)

@Serializable
internal sealed interface OpenAIToolChoice{
    @JvmInline
    @Serializable
    value class Choice internal constructor(val value: String): OpenAIToolChoice

    @Serializable
    data class FunctionName(val name: String)
    @Serializable
    data class Function(val function: FunctionName): OpenAIToolChoice {
        val type: String = "function"
    }

    companion object {
        // OpenAI api is too "dynamic", have to inline value here, so alas, no proper classes hierarchy, creating "objects" instead
        val Auto = Choice("auto")
        val Required = Choice("required")
        val None = Choice("none")
    }
}