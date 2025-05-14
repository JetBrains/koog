package ai.grazie.code.agents.core.environment

import ai.grazie.code.agents.core.agent.AIAgentTerminationByClientException
import ai.grazie.code.agents.core.engine.UnexpectedAIAgentMessageException
import ai.grazie.code.agents.core.engine.UnexpectedDoubleInitializationException
import ai.grazie.code.agents.core.model.message.EnvironmentToAgentErrorMessage
import ai.grazie.code.agents.core.model.message.EnvironmentToAgentMessage
import ai.grazie.code.agents.core.model.message.EnvironmentToAgentTerminationMessage
import ai.grazie.code.agents.core.model.message.EnvironmentToolResultMultipleToAgentMessage
import ai.grazie.code.agents.core.model.message.EnvironmentToolResultSingleToAgentMessage
import ai.grazie.code.agents.core.model.message.AIAgentEnvironmentToAgentInitializeMessage

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

            is AIAgentEnvironmentToAgentInitializeMessage -> {
                throw UnexpectedDoubleInitializationException()
            }

            else -> {
                throw UnexpectedAIAgentMessageException()
            }
        }
    }
}
