package ai.koog.prompt.executor.clients.anthropic

import ai.koog.prompt.executor.clients.InternalAPI
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@InternalAPI
@Serializable
public data class AnthropicMessageRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val maxTokens: Int = 2048,
    val temperature: Double? = null,
    val system: List<SystemAnthropicMessage>? = null,
    val tools: List<AnthropicTool>? = null,
    val stream: Boolean = false,
    val toolChoice: AnthropicToolChoice? = null,
)

@InternalAPI
@Serializable
public data class AnthropicMessage(
    val role: String,
    val content: List<AnthropicContent>
)

@InternalAPI
@Serializable
public data class SystemAnthropicMessage(
    val text: String,
    @EncodeDefault(ALWAYS) val type: String = "text"
)

@InternalAPI
@Serializable
public sealed class AnthropicContent {
    @Serializable
    @SerialName("text")
    public data class Text(val text: String) : AnthropicContent()

    @Serializable
    @SerialName("image")
    public data class Image(val source: ImageSource) : AnthropicContent()

    @Serializable
    @SerialName("document")
    public data class Document(val source: DocumentSource) : AnthropicContent()

    @Serializable
    @SerialName("tool_use")
    public data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonObject
    ) : AnthropicContent()

    @Serializable
    @SerialName("tool_result")
    public data class ToolResult(
        val toolUseId: String,
        val content: String
    ) : AnthropicContent()
}

@InternalAPI
@Serializable
public sealed interface ImageSource {
    @Serializable
    @SerialName("url")
    public data class Url(val url: String) : ImageSource

    @Serializable
    @SerialName("base64")
    public data class Base64(val data: String, val mediaType: String) : ImageSource
}

@InternalAPI
@Serializable
public sealed interface DocumentSource {
    @Serializable
    @SerialName("url")
    public data class Url(val url: String) : DocumentSource

    @Serializable
    @SerialName("base64")
    public data class Base64(val data: String, val mediaType: String) : DocumentSource

    @Serializable
    @SerialName("text")
    public data class PlainText(val data: String, val mediaType: String) : DocumentSource
}

@InternalAPI
@Serializable
public data class AnthropicTool(
    val name: String,
    val description: String,
    val inputSchema: AnthropicToolSchema
)

@InternalAPI
@Serializable
public data class AnthropicToolSchema(
    val type: String = "object",
    val properties: JsonObject,
    val required: List<String>
)

@InternalAPI
@Serializable
public data class AnthropicResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<AnthropicResponseContent>,
    val model: String,
    val stopReason: String? = null,
    val usage: AnthropicUsage? = null
)

@InternalAPI
@Serializable
public sealed class AnthropicResponseContent {
    @Serializable
    @SerialName("text")
    public data class Text(val text: String) : AnthropicResponseContent()

    @Serializable
    @SerialName("tool_use")
    public data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonObject
    ) : AnthropicResponseContent()
}

@InternalAPI
@Serializable
public data class AnthropicUsage(
    val inputTokens: Int,
    val outputTokens: Int
)

@InternalAPI
@Serializable
public data class AnthropicStreamResponse(
    val type: String,
    val delta: AnthropicStreamDelta? = null,
    val message: AnthropicResponse? = null
)

@InternalAPI
@Serializable
public data class AnthropicStreamDelta(
    val type: String,
    val text: String? = null,
    val toolUse: AnthropicResponseContent.ToolUse? = null
)


@InternalAPI
@Serializable
public sealed interface AnthropicToolChoice {
    @Serializable
    @SerialName("auto")
    public data object Auto : AnthropicToolChoice

    @Serializable
    @SerialName("any")
    public data object Any : AnthropicToolChoice

    @Serializable
    @SerialName("none")
    public data object None : AnthropicToolChoice

    @Serializable
    @SerialName("tool")
    public data class Tool(val name: String) : AnthropicToolChoice
}