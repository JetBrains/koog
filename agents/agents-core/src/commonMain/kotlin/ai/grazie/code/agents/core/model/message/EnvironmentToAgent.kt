package ai.grazie.code.agents.core.model.message

import ai.grazie.code.agents.core.model.AgentServiceError
import ai.grazie.utils.mpp.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Base class of all messages sent from the environment to the `IdeFormer` server.
 * All client messages, except for [init][EnvironmentInitializeToAgentMessage],
 * are associated with a specific session.
 */
@Serializable
sealed interface EnvironmentToAgentMessage

/**
 * Represents the content of messages sent from the environment.
 *
 * @property agentId Identifier for the agent receiving the message.
 * @property message Textual representation of environment changes, or user's prompt that initiates the conversation.
 */
@Serializable
sealed interface EnvironmentToAgentContent {
    val agentId: String
    val message: String
}

@Serializable
abstract class EnvironmentInitializeToAgentContent : EnvironmentToAgentContent {
    abstract override val agentId: String
    abstract override val message: String
}

@Serializable
abstract class EnvironmentInitializeToAgentMessage : EnvironmentToAgentMessage {
    abstract val content: EnvironmentInitializeToAgentContent
}

/**
 * Marker interface for tool call results
 */
@Serializable
sealed interface EnvironmentToolResultToAgentMessage : EnvironmentToAgentMessage {
    val sessionUuid: UUID
}


/**
 * Content of tool call result messages sent to the agent.
 *
 * @property agentId Identifier for the agent receiving the message.
 * @property message Tool output
 * @property toolCallId Id to identify tool call when calling multiple tools at once.
 */
@Serializable
abstract class EnvironmentToolResultToAgentContent : EnvironmentToAgentContent {
    abstract val toolCallId: String?
    abstract val toolName: String
    abstract override val agentId: String
    abstract override val message: String
}

/**
 * Represents a message sent after a tool call.
 * Encapsulates execution outcomes: if and how exactly the environment changed,
 * were there any errors while executing, etc.
 *
 * @property sessionUuid Unique identifier for the session.
 * @property content Content of the message.
 */
// FIXME actually is redundant except for compatibility with IdeFormer. Maybe we can get rid of this custom case?
@Serializable
@SerialName("OBSERVATION") // it's called OBSERVATION for compatibility with IdeFormer
data class EnvironmentToolResultSingleToAgentMessage(
    override val sessionUuid: UUID,
    val content: EnvironmentToolResultToAgentContent,
) : EnvironmentToolResultToAgentMessage

/**
 * Represents a message sent after multiple tool calls.
 * Bundles multiple execution outcomes: environment changes, errors encountered, etc.
 *
 * @property sessionUuid Unique identifier for the session.
 * @property content List of content messages representing multiple tool results.
 */
@Serializable
@SerialName("OBSERVATIONS_MULTIPLE")
data class EnvironmentToolResultMultipleToAgentMessage(
    override val sessionUuid: UUID,
    val content: List<EnvironmentToolResultToAgentContent>,
) : EnvironmentToolResultToAgentMessage

/**
 * Represents the content of [TERMINATION][EnvironmentToAgentTerminationMessage] messages sent from the environment.
 *
 * @property agentId Identifier for the agent receiving the message.
 * @property message Textual representation of the environment changes.
 */
@Serializable
data class EnvironmentToAgentTerminationContent(
    override val agentId: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val message: String = "Terminating on client behalf",
) : EnvironmentToAgentContent

/**
 * Represents a termination request sent on behalf of the environment.
 * These are a communication essential, as they signal to the server
 * that it should perform cleanup for a particular session.
 *
 * @property sessionUuid Unique identifier for the session.
 * @property error Optional environment error details.
 */
@Serializable
@SerialName("TERMINATION")
data class EnvironmentToAgentTerminationMessage(
    val sessionUuid: UUID,
    val content: EnvironmentToAgentTerminationContent? = null,
    val error: AgentServiceError? = null,
) : EnvironmentToAgentMessage

/**
 * Represents an environment error that occurred **outside tool execution**.
 * For errors resulting from failed [Tool][ai.grazie.code.agents.core.tools.Tool] executions,
 * use [EnvironmentToolResultSingleToAgentMessage] instead.
 *
 * @property sessionUuid Unique identifier for the session.
 * @property error Environment error details.
 * @see <a href="https://youtrack.jetbrains.com/articles/JBRes-A-102/#:~:text=ERROR%20messages%20are%20mostly%20not%20used%20now">Knowledge Base Article</a>
 */
@Serializable
@SerialName("ERROR")
data class EnvironmentToAgentErrorMessage(
    val sessionUuid: UUID,
    val error: AgentServiceError,
) : EnvironmentToAgentMessage