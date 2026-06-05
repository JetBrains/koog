package ai.koog.agents.core.environment

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represents the result or response received from a tool operation.
 *
 * @property id An optional identifier for the tool result.
 * @property tool The name or type of the tool that generated the result.
 * @property toolArgs The arguments provided to the tool during execution.
 * @property toolDescription An optional description of the tool's functionality.
 * @property output The main content or message associated with the tool result.
 * @property resultKind The kind of result produced by the tool, indicating success, failure, or validation error.
 * @property result The result produced by the tool.
 * @property resultObject The raw result object produced by the tool. This value will not survive serialization, hence it should be used with caution and is marked as `@InternalAgentsApi`.
 * @property parts The content parts of the tool result. When non-null, these are forwarded to the LLM
 *   client and may include non-text content such as images. When null, [output] is used as plain text.
 */
@Serializable
public data class ReceivedToolResult(
    val id: String?,
    val tool: String,
    val toolArgs: JSONObject,
    val toolDescription: String?,
    val output: String,
    val resultKind: ToolResultKind,
    val result: JSONElement?,
    @property:InternalAgentsApi @property:Transient val resultObject: Any? = null,
    val parts: List<MessagePart.ContentPart>? = null,
) {
    /**
     * Converts the current `ReceivedToolResult` instance into a `MessagePart.Tool.Result` object.
     *
     * @return A `MessagePart.Tool.Result` instance representing the tool result with the current data and metadata.
     */
    public fun toMessagePart(): MessagePart.Tool.Result = MessagePart.Tool.Result(
        id = id,
        tool = tool,
        parts = parts ?: listOf(MessagePart.Text(output)),
        isError = resultKind !is ToolResultKind.Success // Failure and ValidationError both represent tool errors
    )
}
