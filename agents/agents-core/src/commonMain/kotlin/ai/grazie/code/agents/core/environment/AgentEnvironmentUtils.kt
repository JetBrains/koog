package ai.grazie.code.agents.core.environment

import ai.grazie.code.agents.core.agent.AgentTerminationByClientException
import ai.grazie.code.agents.core.engine.UnexpectedAgentMessageException
import ai.grazie.code.agents.core.engine.UnexpectedDoubleInitializationException
import ai.grazie.code.agents.core.model.message.EnvironmentToAgentErrorMessage
import ai.grazie.code.agents.core.model.message.EnvironmentToAgentMessage
import ai.grazie.code.agents.core.model.message.EnvironmentToAgentTerminationMessage
import ai.grazie.code.agents.core.model.message.EnvironmentToolResultMultipleToAgentMessage
import ai.grazie.code.agents.core.model.message.EnvironmentToolResultSingleToAgentMessage
import ai.grazie.code.agents.core.model.message.AgentEnvironmentToAgentInitializeMessage

object AgentEnvironmentUtils {
    fun EnvironmentToAgentMessage.mapToToolResult(): List<ReceivedToolResult> {
        return when (this) {
            is EnvironmentToolResultSingleToAgentMessage -> {
                listOf(this.content.toResult())
            }

            is EnvironmentToolResultMultipleToAgentMessage -> {
                this.content.map { it.toResult() }
            }

            is EnvironmentToAgentErrorMessage -> {
                throw AgentTerminationByClientException(this.error.message)
            }

            is EnvironmentToAgentTerminationMessage -> {
                throw AgentTerminationByClientException(
                    this.content?.message ?: this.error?.message ?: ""
                )
            }

            is AgentEnvironmentToAgentInitializeMessage -> {
                throw UnexpectedDoubleInitializationException()
            }

            else -> {
                throw UnexpectedAgentMessageException()
            }
        }
    }
}