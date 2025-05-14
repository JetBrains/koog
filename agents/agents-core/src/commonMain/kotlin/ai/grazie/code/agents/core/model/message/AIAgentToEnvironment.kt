package ai.grazie.code.agents.core.model.message

import ai.grazie.code.agents.core.model.AIAgentServiceError
import ai.grazie.utils.mpp.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents a message sent from an agent to the environment.
 * This is a base interface for all communication from agents to their respective environments.
 * Each message under this interface is tied to a specific session identified by a universally unique identifier.
 *
 * @property sessionUuid A unique identifier for the session associated with the message.
 */
@Serializable
sealed interface AIAgentToEnvironmentMessage {
    val sessionUuid: UUID
}

/**
 * Marker interface for tool calls (single and multiple)
 */
@Serializable
sealed interface AIAgentToolCallToEnvironmentMessage : AIAgentToEnvironmentMessage

/**
 * Content of tool call messages sent from the agent.
 *
 * @property agentId Identifier for the agent receiving the message.
 * @property toolName Name of the tool to call.
 * @property toolArgs Arguments for the called tool.
 * @property toolCallId Id to identify tool call when calling multiple tools at once.
 * Not all implementations support it, it will be `null` in this case.
 */
@Serializable
data class AIAgentToolCallToEnvironmentContent(
    val agentId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject,
)

/**
 * Represents a message sent from the server to the environment to perform multiple tool calls.
 *
 * @property sessionUuid Unique identifier for the session.
 * @property content List of individual tool call requests, each containing details about
 * the agent, tool name, arguments, and an optional tool call identifier.
 */
@Serializable
@SerialName("ACTION_MULTIPLE")
data class AIAgentToolCallsToEnvironmentMessage(
    override val sessionUuid: UUID,
    val content: List<AIAgentToolCallToEnvironmentContent>
) : AIAgentToolCallToEnvironmentMessage

/**
 * Represents a termination message sent on behalf of the server.
 * Indicates that the server has no further actions for the client to perform,
 * and is usually accompanied by the final result of the inquiry.
 * Can also be received as an acknowledgement of a client-initiated termination,
 * in which case the result will be absent.
 *
 * @property sessionUuid Unique identifier for the session.
 * @property content Optional content of the message.
 * @property error Optional error details.
 */
@Serializable
@SerialName("TERMINATION")
data class AIAgentTerminationToEnvironmentMessage(
    override val sessionUuid: UUID,
    val content: AIAgentToolCallToEnvironmentContent? = null,
    val error: AIAgentServiceError? = null,
) : AIAgentToEnvironmentMessage

/**
 * Represents an error response from the server.
 * These may occur for several reasons:
 *
 * - [Sending unsupported types of messages][ai.grazie.code.agents.core.model.AIAgentServiceErrorType.UNEXPECTED_MESSAGE_TYPE];
 * - [Sending incorrect or incomplete messages][ai.grazie.code.agents.core.model.AIAgentServiceErrorType.MALFORMED_MESSAGE];
 * - [Trying to use an agent that is not available][ai.grazie.code.agents.core.model.AIAgentServiceErrorType.AGENT_NOT_FOUND];
 * - [Other, unexpected errors][ai.grazie.code.agents.core.model.AIAgentServiceErrorType.UNEXPECTED_ERROR].
 *
 * @property sessionUuid Unique identifier for the session.
 * @property error Error details.
 */
@Serializable
@SerialName("ERROR")
data class AIAgentErrorToEnvironmentMessage(
    override val sessionUuid: UUID,
    val error: AIAgentServiceError
) : AIAgentToEnvironmentMessage
