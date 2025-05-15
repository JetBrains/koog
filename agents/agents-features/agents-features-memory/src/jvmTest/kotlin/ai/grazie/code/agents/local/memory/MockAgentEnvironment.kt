package ai.grazie.code.agents.local.memory

import ai.grazie.code.agents.core.environment.AIAgentEnvironment
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.jetbrains.code.prompt.message.Message

class MockAgentEnvironment: AIAgentEnvironment {
    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> = emptyList()

    override suspend fun reportProblem(exception: Throwable) = Unit

    override suspend fun sendTermination(result: String?) = Unit
}