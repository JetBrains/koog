package ai.koog.agents.core.model

import ai.koog.agents.core.exception.AgentEngineException
import ai.koog.agents.core.exception.AgentNotFoundException
import ai.koog.agents.core.exception.MalformedMessageException
import ai.koog.agents.core.exception.UnexpectedMessageTypeException
import ai.koog.agents.core.exception.UnexpectedServerException
import kotlinx.serialization.Serializable

/**
 * Represents different types of service errors that can occur in the context of an agent.
 * This enum is used to categorize the errors for better error handling and reporting.
 */
@Serializable
public enum class AgentServiceErrorType {
    /**
     * Indicates that an unexpected type of message was sent to the agent service.
     *
     * This error type is used when the agent encounters a message that does not conform to
     * the expected structure, format, or type, making it unprocessable within the service.
     *
     * Typically, this may occur if the message type is unsupported or improperly defined
     * in the communication protocol.
     */
    UNEXPECTED_MESSAGE_TYPE,

    /**
     * Indicates that a received message is malformed. This can occur when the message does not adhere
     * to the expected format, structure, or content requirements.
     *
     * Common scenarios include missing required fields, incorrect data types, or invalid values
     * in the message body.
     *
     * Errors of this type usually require client-side corrections to the message before resending.
     */
    MALFORMED_MESSAGE,

    /**
     * Represents a specific error type indicating that the requested agent could not be found.
     * This error may occur if the agent is unavailable, unregistered, or the identifier provided
     * does not correspond to any existing agent in the system.
     */
    AGENT_NOT_FOUND,

    /**
     * Represents an unexpected error encountered by the AI Agent.
     *
     * This error type is used to categorize issues that cannot be classified into other predefined
     * error types such as malformed message, agent not found, etc. It typically serves as a fallback
     * for unanticipated scenarios or server-related failures.
     */
    UNEXPECTED_ERROR,
}

/**
 * Represents an error response from the agent service.
 *
 * This class encapsulates details about errors that may occur during interactions
 * with the agent service. Each error is characterized by a specific [type] and an
 * associated human-readable [message] providing additional context about the error.
 *
 * The error type is defined using the [AgentServiceErrorType] enum, which categorizes
 * errors into different types such as unexpected errors, malformed messages, or agent
 * not found scenarios.
 */
@Serializable
public data class AgentServiceError(
    val type: AgentServiceErrorType,
    val message: String,
) {
    /**
     * Converts the current `AgentServiceError` instance into a corresponding `AgentEngineException` instance.
     *
     * This function maps the `type` property of the `AgentServiceError` to a specific subclass of
     * `AgentEngineException` based on the error type. Each error type corresponds to a specific exception:
     *
     * - `UNEXPECTED_ERROR` maps to `UnexpectedServerException`.
     * - `UNEXPECTED_MESSAGE_TYPE` maps to `UnexpectedMessageTypeException`.
     * - `MALFORMED_MESSAGE` maps to `MalformedMessageException`.
     * - `AGENT_NOT_FOUND` maps to `AgentNotFoundException`.
     *
     * The `message` property of the `AgentServiceError` is passed as the exception's message.
     *
     * @return An `AgentEngineException` instance that corresponds to the current error type.
     */
    public fun asException(): AgentEngineException {
        return when (type) {
            AgentServiceErrorType.UNEXPECTED_ERROR -> UnexpectedServerException(message)
            AgentServiceErrorType.UNEXPECTED_MESSAGE_TYPE -> UnexpectedMessageTypeException(message)
            AgentServiceErrorType.MALFORMED_MESSAGE -> MalformedMessageException(message)
            AgentServiceErrorType.AGENT_NOT_FOUND -> AgentNotFoundException(message)
        }
    }
}
