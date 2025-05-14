package ai.grazie.code.agents.core.environment

import ai.jetbrains.code.prompt.message.Message

public interface AgentEnvironment {
    public suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult>
    public suspend fun reportProblem(exception: Throwable)
    public suspend fun sendTermination(result: String?)
}

public suspend fun AgentEnvironment.executeTool(toolCall: Message.Tool.Call): ReceivedToolResult =
    executeTools(listOf(toolCall)).first()
