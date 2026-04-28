package ai.koog.agents.core.environment

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONObject
import ai.koog.serialization.kotlinx.toKoogJSONElement
import ai.koog.serialization.kotlinx.toKoogJSONObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
) {
    @Deprecated("Use the constructor with JSONElement instead of JsonElement")
    public constructor(
        id: String?,
        tool: String,
        toolArgs: JsonObject,
        toolDescription: String?,
        output: String,
        resultKind: ToolResultKind,
        result: JsonElement?
    ) : this(
        id = id,
        tool = tool,
        toolArgs = toolArgs.toKoogJSONObject(),
        toolDescription = toolDescription,
        output = output,
        resultKind = resultKind,
        result = result?.toKoogJSONElement()
    )

    /**
     * Converts the current `ReceivedToolResult` instance into a `MessagePart.Tool.Result` object.
     *
     * @return A `MessagePart.Tool.Result` instance representing the tool result with the current data and metadata.
     */
    public fun toMessagePart(): MessagePart.Tool.Result = MessagePart.Tool.Result(
        id = id,
        tool = tool,
        output = output,
        isError = resultKind !is ToolResultKind.Success // Failure and ValidationError both represent tool errors
    )
}
