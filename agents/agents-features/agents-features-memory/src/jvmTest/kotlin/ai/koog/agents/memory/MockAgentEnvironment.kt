package ai.koog.agents.memory

import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.prompt.message.Message

class MockAgentEnvironment: AIAgentEnvironment {
    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> = emptyList()

    override suspend fun reportProblem(exception: Throwable) = Unit

    override suspend fun sendTermination(result: String?) = Unit
}