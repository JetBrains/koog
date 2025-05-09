package ai.grazie.code.agents.core.model

import ai.grazie.code.agents.core.exception.*
import kotlinx.serialization.Serializable

@Serializable
enum class AgentServiceErrorType {
    UNEXPECTED_MESSAGE_TYPE,
    MALFORMED_MESSAGE,
    AGENT_NOT_FOUND,
    UNEXPECTED_ERROR,
}

@Serializable
data class AgentServiceError(
    val type: AgentServiceErrorType,
    val message: String,
) {
    fun asException(): AgentEngineException {
        return when (type) {
            AgentServiceErrorType.UNEXPECTED_ERROR -> UnexpectedServerException(message)
            AgentServiceErrorType.UNEXPECTED_MESSAGE_TYPE -> UnexpectedMessageTypeException(message)
            AgentServiceErrorType.MALFORMED_MESSAGE -> MalformedMessageException(message)
            AgentServiceErrorType.AGENT_NOT_FOUND -> AgentNotFoundException(message)
        }
    }
}
