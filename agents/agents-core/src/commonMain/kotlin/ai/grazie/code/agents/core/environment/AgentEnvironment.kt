package ai.grazie.code.agents.core.environment

import ai.jetbrains.code.prompt.message.Message

interface AgentEnvironment {
    suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult>
    suspend fun reportProblem(exception: Throwable)
    suspend fun sendTermination(result: String?)
}

suspend fun AgentEnvironment.executeTool(toolCall: Message.Tool.Call): ReceivedToolResult =
    executeTools(listOf(toolCall)).first()