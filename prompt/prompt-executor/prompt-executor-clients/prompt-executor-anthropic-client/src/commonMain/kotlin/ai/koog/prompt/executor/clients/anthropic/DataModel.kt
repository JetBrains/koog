package ai.koog.prompt.executor.clients.anthropic

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class AnthropicMessageRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val maxTokens: Int = 2048,
    val temperature: Double? = null,
    val system: List<SystemAnthropicMessage>? = null,
    val tools: List<AnthropicTool>? = null,
    val stream: Boolean = false,
    val toolChoice: AnthropicToolChoice? = null,
)

@Serializable
internal data class AnthropicMessage(
    val role: String,
    val content: List<AnthropicContent>
)

@Serializable
internal data class SystemAnthropicMessage(
    val text: String,
    @EncodeDefault(ALWAYS) val type: String = "text"
)

@Serializable
internal sealed class AnthropicContent {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : AnthropicContent()

    @Serializable
    @SerialName("image")
    data class Image(val source: ImageSource) : AnthropicContent()

    @Serializable
    @SerialName("document")
    data class Document(val source: DocumentSource) : AnthropicContent()

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
        val toolUseId: String,
        val content: String
    ) : AnthropicContent()
}

@Serializable
internal sealed class ImageSource {
    @Serializable
    @SerialName("url")
    data class Url(val url: String) : ImageSource()

    @Serializable
    @SerialName("base64")
    data class Base64(val data: String, val mediaType: String) : ImageSource()
}

@Serializable
internal sealed class DocumentSource {
    @Serializable
    @SerialName("url")
    data class PDFUrl(val url: String) : DocumentSource()

    @Serializable
    @SerialName("base64")
    data class PDFBase64(val data: String, val mediaType: String = "application/pdf") : DocumentSource()

    @Serializable
    @SerialName("text")
    data class PlainText(val data: String, val mediaType: String = "text/plain") : DocumentSource()
}

@Serializable
internal data class AnthropicTool(
    val name: String,
    val description: String,
    val inputSchema: AnthropicToolSchema
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
    val stopReason: String? = null,
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
    val inputTokens: Int,
    val outputTokens: Int
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
    val toolUse: AnthropicResponseContent.ToolUse? = null
)


@Serializable
internal sealed interface AnthropicToolChoice {
    @Serializable
    @SerialName("auto")
    data object Auto : AnthropicToolChoice

    @Serializable
    @SerialName("any")
    data object Any : AnthropicToolChoice

    @Serializable
    @SerialName("none")
    data object None : AnthropicToolChoice

    @Serializable
    @SerialName("tool")
    data class Tool(val name: String) : AnthropicToolChoice
}