package ai.koog.agents.core.environment

import ai.koog.prompt.message.Message

public interface AIAgentEnvironment {
    public suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult>
    public suspend fun reportProblem(exception: Throwable)
    public suspend fun sendTermination(result: String?)
}

public suspend fun AIAgentEnvironment.executeTool(toolCall: Message.Tool.Call): ReceivedToolResult =
    executeTools(listOf(toolCall)).first()
