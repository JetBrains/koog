package ai.koog.prompt.executor.clients.openrouter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmInline

@Serializable
internal data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val temperature: Double? = null,
    val tools: List<OpenRouterTool>? = null,
    val stream: Boolean = false,
    val toolChoice: OpenRouterToolChoice? = null
)

@Serializable
internal data class OpenRouterMessage(
    val role: String,
    val content: String? = "",
    val toolCalls: List<OpenRouterToolCall>? = null,
    val name: String? = null,
    val toolCallId: String? = null
)

@Serializable
internal data class OpenRouterToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenRouterFunction
)

@Serializable
internal data class OpenRouterFunction(
    val name: String,
    val arguments: String
)

@Serializable
internal data class OpenRouterTool(
    val type: String = "function",
    val function: OpenRouterToolFunction
)

@Serializable
internal data class OpenRouterToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
internal data class OpenRouterResponse(
    val id: String,
    @SerialName("object") val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<OpenRouterChoice>,
    val usage: OpenRouterUsage? = null
)

@Serializable
internal data class OpenRouterChoice(
    val index: Int,
    val message: OpenRouterMessage,
    val finishReason: String? = null
)

@Serializable
internal data class OpenRouterUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

@Serializable
internal data class OpenRouterStreamResponse(
    val id: String,
    @SerialName("object") val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<OpenRouterStreamChoice>
)

@Serializable
internal data class OpenRouterStreamChoice(
    val index: Int,
    val delta: OpenRouterStreamDelta,
    val finishReason: String? = null
)

@Serializable
internal data class OpenRouterStreamDelta(
    val role: String? = null,
    val content: String? = null,
    val toolCalls: List<OpenRouterToolCall>? = null
)


@Serializable
internal sealed interface OpenRouterToolChoice{
    @JvmInline
    @Serializable
    value class Choice internal constructor(val value: String): OpenRouterToolChoice

    @Serializable
    data class FunctionName(val name: String)
    @Serializable
    data class Function(val name: FunctionName) : OpenRouterToolChoice {
        val type: String = "function"
    }


    companion object {
        // OpenAI api is too "dynamic", have to inline value here, so alas, no proper classes hierarchy, creating "objects" instead
        val Auto = Choice("auto")
        val Required = Choice("required")
        val None = Choice("none")
    }
}