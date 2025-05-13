package ai.grazie.code.agents.core.environment

import ai.grazie.code.agents.core.model.message.EnvironmentToolResultToAgentContent
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.model.message.AgentEnvironmentToolResultToAgentContent
import ai.jetbrains.code.prompt.dsl.PromptBuilder
import ai.jetbrains.code.prompt.message.Message

data class ReceivedToolResult(
    val id: String?,
    val tool: String,
    val content: String,
    val result: ToolResult?
) {
    fun toMessage(): Message.Tool.Result = Message.Tool.Result(
        id = id,
        tool = tool,
        content = content,
    )
}

fun EnvironmentToolResultToAgentContent.toResult(): ReceivedToolResult {
    check(this is AgentEnvironmentToolResultToAgentContent) {
        "Agent must receive AgentEnvironmentToolResultToAgentContent," +
                " but ${this::class.simpleName} was received"
    }

    return toResult()
}

fun AgentEnvironmentToolResultToAgentContent.toResult(): ReceivedToolResult = ReceivedToolResult(
    id = toolCallId,
    tool = toolName,
    content = message,
    result = toolResult
)

fun PromptBuilder.ToolMessageBuilder.result(result: ReceivedToolResult) {
    result(result.toMessage())
}