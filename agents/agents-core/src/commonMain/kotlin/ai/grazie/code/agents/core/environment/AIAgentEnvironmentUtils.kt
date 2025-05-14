package ai.grazie.code.agents.core.environment

import ai.grazie.code.agents.core.agent.AIAgentTerminationByClientException
import ai.grazie.code.agents.core.engine.UnexpectedAIAgentMessageException
import ai.grazie.code.agents.core.engine.UnexpectedDoubleInitializationException
import ai.grazie.code.agents.core.model.message.AIEnvironmentToAgentErrorMessage
import ai.grazie.code.agents.core.model.message.AIEnvironmentToAgentMessage
import ai.grazie.code.agents.core.model.message.AIEnvironmentToAgentTerminationMessage
import ai.grazie.code.agents.core.model.message.AIEnvironmentToolResultMultipleToAgentMessage
import ai.grazie.code.agents.core.model.message.AIEnvironmentToolResultSingleToAgentMessage
import ai.grazie.code.agents.core.model.message.AIAgentEnvironmentToAgentInitializeMessage

object AIAgentEnvironmentUtils {
    fun AIEnvironmentToAgentMessage.mapToToolResult(): List<ReceivedToolResult> {
        return when (this) {
            is AIEnvironmentToolResultSingleToAgentMessage -> {
                listOf(this.content.toResult())
            }

            is AIEnvironmentToolResultMultipleToAgentMessage -> {
                this.content.map { it.toResult() }
            }

            is AIEnvironmentToAgentErrorMessage -> {
                throw AIAgentTerminationByClientException(this.error.message)
            }

            is AIEnvironmentToAgentTerminationMessage -> {
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