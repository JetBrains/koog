package ai.koog.agents.core.environment

import ai.koog.agents.core.model.message.EnvironmentToolResultToAgentContent
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.model.message.AIAgentEnvironmentToolResultToAgentContent
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import kotlinx.datetime.Clock

public data class ReceivedToolResult(
    val id: String?,
    val tool: String,
    val content: String,
    val result: ToolResult?
) {
    internal fun toMessage(clock: Clock): Message.Tool.Result = Message.Tool.Result(
        id = id,
        tool = tool,
        content = content,
        metaInfo = RequestMetaInfo.create(clock)
    )
}

internal fun EnvironmentToolResultToAgentContent.toResult(): ReceivedToolResult {
    check(this is AIAgentEnvironmentToolResultToAgentContent) {
        "AI agent must receive AIAgentEnvironmentToolResultToAgentContent," +
                " but ${this::class.simpleName} was received"
    }

    return toResult()
}

internal fun AIAgentEnvironmentToolResultToAgentContent.toResult(): ReceivedToolResult = ReceivedToolResult(
    id = toolCallId,
    tool = toolName,
    content = message,
    result = toolResult
)

public fun PromptBuilder.ToolMessageBuilder.result(result: ReceivedToolResult) {
    result(result.toMessage(clock))
}
