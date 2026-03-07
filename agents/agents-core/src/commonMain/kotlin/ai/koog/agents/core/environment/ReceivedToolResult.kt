package ai.koog.agents.core.environment

import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONObject
import ai.koog.serialization.kotlinx.toKoogJSONElement
import ai.koog.serialization.kotlinx.toKoogJSONObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * Represents the result or response received from a tool operation.
 *
 * @property id An optional identifier for the tool result.
 * @property tool The name or type of the tool that generated the result.
 * @property toolArgs The arguments provided to the tool during execution.
 * @property toolDescription An optional description of the tool's functionality.
 * @property content The main content or message associated with the tool result.
 * @property resultKind The kind of result produced by the tool, indicating success, failure, or validation error.
 * @property result The result produced by the tool.
 */
@Serializable
public data class ReceivedToolResult(
    val id: String?,
    val tool: String,
    val toolArgs: JSONObject,
    val toolDescription: String?,
    val content: String,
    val resultKind: ToolResultKind,
    val result: JSONElement?
) {
    @Deprecated("Use the constructor with JSONElement instead of JsonElement")
    public constructor(
        id: String?,
        tool: String,
        toolArgs: JsonObject,
        toolDescription: String?,
        content: String,
        resultKind: ToolResultKind,
        result: JsonElement?
    ) : this(
        id = id,
        tool = tool,
        toolArgs = toolArgs.toKoogJSONObject(),
        toolDescription = toolDescription,
        content = content,
        resultKind = resultKind,
        result = result?.toKoogJSONElement()
    )

    /**
     * Converts the current `ReceivedToolResult` instance into a `Message.Tool.Result` object.
     *
     * @param clock The clock to use for generating the timestamp in the metadata. Defaults to `Clock.System`.
     * @return A `Message.Tool.Result` instance representing the tool result with the current data and metadata.
     */
    public fun toMessage(clock: Clock = Clock.System): Message.Tool.Result = Message.Tool.Result(
        id = id,
        tool = tool,
        content = content,
        metaInfo = RequestMetaInfo.create(clock)
    )
}

/**
 * Adds a tool result to the prompt.
 *
 * This method converts a `ReceivedToolResult` into a `Message.Tool.Result` and adds it to the message list.
 *
 * @param result The result from a tool execution to be added as a tool result message
 */
public fun PromptBuilder.ToolMessageBuilder.result(result: ReceivedToolResult) {
    result(result.toMessage(clock))
}
