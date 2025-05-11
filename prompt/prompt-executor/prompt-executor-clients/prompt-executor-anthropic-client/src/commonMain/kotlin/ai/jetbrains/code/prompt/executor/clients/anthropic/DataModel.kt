package ai.jetbrains.code.prompt.executor.clients.anthropic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class AnthropicMessageRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val max_tokens: Int = 2048,
    val temperature: Double? = null,
    val system: String? = null,
    val tools: List<AnthropicTool>? = null,
    val stream: Boolean = false
)

@Serializable
internal data class AnthropicMessage(
    val role: String,
    val content: List<AnthropicContent>
)

@Serializable
internal sealed class AnthropicContent {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : AnthropicContent()

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonObject
    ) : AnthropicContent()

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val tool_use_id: String,
        val content: String
    ) : AnthropicContent()
}

@Serializable
internal data class AnthropicTool(
    val name: String,
    val description: String,
    val input_schema: AnthropicToolSchema
)

@Serializable
internal data class AnthropicToolSchema(
    val type: String = "object",
    val properties: JsonObject,
    val required: List<String>
)

@Serializable
internal data class AnthropicResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<AnthropicResponseContent>,
    val model: String,
    val stop_reason: String? = null,
    val usage: AnthropicUsage? = null
)

@Serializable
internal sealed class AnthropicResponseContent {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : AnthropicResponseContent()

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonObject
    ) : AnthropicResponseContent()
}

@Serializable
internal data class AnthropicUsage(
    val input_tokens: Int,
    val output_tokens: Int
)

@Serializable
internal data class AnthropicStreamResponse(
    val type: String,
    val delta: AnthropicStreamDelta? = null,
    val message: AnthropicResponse? = null
)

@Serializable
internal data class AnthropicStreamDelta(
    val type: String,
    val text: String? = null,
    val tool_use: AnthropicResponseContent.ToolUse? = null
)