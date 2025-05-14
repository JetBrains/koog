package ai.grazie.code.agents.core.environment

import ai.grazie.code.agents.core.model.message.EnvironmentToolResultToAgentContent
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.model.message.LocalAgentEnvironmentToolResultToAgentContent
import ai.jetbrains.code.prompt.dsl.PromptBuilder
import ai.jetbrains.code.prompt.message.Message

public data class ReceivedToolResult(
    val id: String?,
    val tool: String,
    val content: String,
    val result: ToolResult?
) {
    public fun toMessage(): Message.Tool.Result = Message.Tool.Result(
        id = id,
        tool = tool,
        content = content,
    )
}

public fun EnvironmentToolResultToAgentContent.toResult(): ReceivedToolResult {
    check(this is LocalAgentEnvironmentToolResultToAgentContent) {
        "Local agen't must receive LocalAgentEnvironmentToolResultToAgentContent," +
                " but ${this::class.simpleName} was received"
    }

    return toResult()
}

public fun LocalAgentEnvironmentToolResultToAgentContent.toResult(): ReceivedToolResult = ReceivedToolResult(
    id = toolCallId,
    tool = toolName,
    content = message,
    result = toolResult
)

public fun PromptBuilder.ToolMessageBuilder.result(result: ReceivedToolResult) {
    result(result.toMessage())
}
