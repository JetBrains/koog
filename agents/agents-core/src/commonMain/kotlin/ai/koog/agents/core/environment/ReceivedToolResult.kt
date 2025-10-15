package ai.koog.agents.core.environment

import ai.koog.agents.core.model.message.AIAgentEnvironmentToolResultToAgentContent
import ai.koog.agents.core.model.message.EnvironmentToolResultToAgentContent
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represents the result or response received from a tool operation.
 *
 * @property id An optional identifier for the tool result.
 * @property tool The name or type of the tool that generated the result.
 * @property content The main content or message associated with the tool result.
 * @property result The detailed result produced by the tool, implementing the [ToolResult] interface.
 */
@Serializable
public data class ReceivedToolResult(
    val id: String?,
    val tool: String,
    val content: String,
    val result: JsonElement?
) {
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
 * Converts an instance of AIAgentEnvironmentToolResultToAgentContent to a ReceivedToolResult.
 *
 * @return A ReceivedToolResult containing the tool call identifier, tool name, message content,
 *         and optional tool result extracted from the current instance.
 * @throws IllegalStateException if the instance is not of type AIAgentEnvironmentToolResultToAgentContent.
 */
public fun EnvironmentToolResultToAgentContent.toResult(): ReceivedToolResult {
    check(this is AIAgentEnvironmentToolResultToAgentContent) {
        "AI agent must receive AIAgentEnvironmentToolResultToAgentContent," +
            " but ${this::class.simpleName} was received"
    }

    return toResult()
}

/**
 * Converts an instance of `AIAgentEnvironmentToolResultToAgentContent` to a `ReceivedToolResult`.
 *
 * @return A `ReceivedToolResult` containing the tool call identifier, tool name, message content,
 *         and the result of the tool execution.
 */
public fun AIAgentEnvironmentToolResultToAgentContent.toResult(): ReceivedToolResult = ReceivedToolResult(
    id = toolCallId,
    tool = toolName,
    content = message,
    result = toolResult
)

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
