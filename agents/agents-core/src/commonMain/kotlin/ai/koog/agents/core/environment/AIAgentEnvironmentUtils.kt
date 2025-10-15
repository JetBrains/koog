package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.exception.AIAgentTerminationByClientException
import ai.koog.agents.core.model.message.EnvironmentToAgentErrorMessage
import ai.koog.agents.core.model.message.EnvironmentToAgentMessage
import ai.koog.agents.core.model.message.EnvironmentToAgentTerminationMessage
import ai.koog.agents.core.model.message.EnvironmentToolResultMultipleToAgentMessage
import ai.koog.agents.core.model.message.EnvironmentToolResultSingleToAgentMessage

internal object AIAgentEnvironmentUtils {
    fun EnvironmentToAgentMessage.mapToToolResult(): List<ReceivedToolResult> {
        return when (this) {
            is EnvironmentToolResultSingleToAgentMessage -> {
                listOf(this.content.toResult())
            }

            is EnvironmentToolResultMultipleToAgentMessage -> {
                this.content.map { it.toResult() }
            }

            is EnvironmentToAgentErrorMessage -> {
                throw AIAgentTerminationByClientException(this.error.message)
            }

            is EnvironmentToAgentTerminationMessage -> {
                throw AIAgentTerminationByClientException(
                    this.content?.message ?: this.error?.message ?: ""
                )
            }

            else -> {
                throw IllegalStateException("Unexpected message for agent")
            }
        }
    }
}
