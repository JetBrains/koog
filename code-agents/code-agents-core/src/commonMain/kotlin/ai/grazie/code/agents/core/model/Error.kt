package ai.grazie.code.agents.core.model

import ai.grazie.code.agents.core.exception.AgentNotFoundException
import ai.grazie.code.agents.core.exception.AIAgentEngineException
import ai.grazie.code.agents.core.exception.MalformedMessageException
import ai.grazie.code.agents.core.exception.UnexpectedMessageTypeException
import ai.grazie.code.agents.core.exception.UnexpectedServerException
import kotlinx.serialization.Serializable

@Serializable
enum class AIAgentServiceErrorType {
    UNEXPECTED_MESSAGE_TYPE,
    MALFORMED_MESSAGE,
    AGENT_NOT_FOUND,
    UNEXPECTED_ERROR,
}

@Serializable
data class AIAgentServiceError(
    val type: AIAgentServiceErrorType,
    val message: String,
) {
    fun asException(): AIAgentEngineException {
        return when (type) {
            AIAgentServiceErrorType.UNEXPECTED_ERROR -> UnexpectedServerException(message)
            AIAgentServiceErrorType.UNEXPECTED_MESSAGE_TYPE -> UnexpectedMessageTypeException(message)
            AIAgentServiceErrorType.MALFORMED_MESSAGE -> MalformedMessageException(message)
            AIAgentServiceErrorType.AGENT_NOT_FOUND -> AgentNotFoundException(message)
        }
    }
}
