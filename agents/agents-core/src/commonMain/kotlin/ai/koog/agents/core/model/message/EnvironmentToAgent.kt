@file:OptIn(ExperimentalUuidApi::class)

package ai.koog.agents.core.model.message

import ai.koog.agents.core.model.AgentServiceError
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Base class of all messages sent from the environment to the AI agent.
 * All client messages, except for [init][EnvironmentInitializeToAgentMessage],
 * are associated with a specific session.
 */
@Serializable
public sealed interface EnvironmentToAgentMessage

/**
 * Represents the content of messages sent from the environment.
 *
 * @property agentId Identifier for the agent receiving the message.
 * @property message Textual representation of environment changes, or user's prompt that initiates the conversation.
 */
@Serializable
public sealed interface EnvironmentToAgentContent {
    public val agentId: String
    public val message: String
}

/**
 * Represents the abstract base class for the content of environment-to-agent initialization messages.
 *
 * This class provides a structure for initializing an agent within the environment and requires
 * implementations to specify the agent identifier and a contextual message.
 *
 * @property agentId Unique identifier for the agent being initialized.
 * @property message A message providing context or details relevant to the initialization process.
 */
@Serializable
public abstract class EnvironmentInitializeToAgentContent : EnvironmentToAgentContent {
    /**
     * Unique identifier for the agent receiving the message.
     *
     * This property represents the agent's unique ID and is used to route messages or data
     * to the specific agent. It ensures that each message is associated with the correct
     * entity in multi-agent systems or when multiple agents operate within the same environment.
     */
    abstract override val agentId: String
    /**
     * A contextual message providing details related to the interaction between the environment
     * and an agent.
     *
     * This property is used to convey information that is relevant to the initialization process,
     * environment changes, or tool execution results in the communication pipeline between an
     * environment and an agent.
     */
    abstract override val message: String
}

/**
 * Abstract class representing an initialization message sent from the environment to an AI agent.
 * This message serves as a starting point for initializing an agent's session or setting up
 * its operational context. It is part of the communication flow between the environment and the agent.
 *
 * @property content The content of the initialization message, defining the details and parameters
 * needed for the agent to be initialized. This includes agent-specific configurations or metadata.
 */
@Serializable
public abstract class EnvironmentInitializeToAgentMessage : EnvironmentToAgentMessage {
    public abstract val content: EnvironmentInitializeToAgentContent
}

/**
 * Marker interface for messages sent from the environment to an agent, specifically related to tool results.
 * These messages convey the outcome of tool executions, including session-specific context.
 *
 * This interface extends [EnvironmentToAgentMessage], inheriting the characteristics of environment-to-agent communication.
 * Implementations of this interface provide additional context, such as single or multiple tool results.
 *
 * @property sessionUuid Unique identifier for the session. This ensures that the message is linked
 * to a specific session within which the tool results are relevant.
 */
@Serializable
public sealed interface EnvironmentToolResultToAgentMessage : EnvironmentToAgentMessage {
    public val sessionUuid: Uuid
}

/**
 * Content of tool call result messages sent to the agent.
 *
 * @property agentId Identifier for the agent receiving the message.
 * @property message Tool output
 * @property toolCallId Id to identify tool call when calling multiple tools at once.
 */
@Serializable
public abstract class EnvironmentToolResultToAgentContent : EnvironmentToAgentContent {
    public abstract val toolCallId: String?
    public abstract val toolName: String
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
@Serializable
@SerialName("OBSERVATION")
public data class EnvironmentToolResultSingleToAgentMessage(
    override val sessionUuid: Uuid,
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
public data class EnvironmentToolResultMultipleToAgentMessage(
    override val sessionUuid: Uuid,
    val content: List<EnvironmentToolResultToAgentContent>,
) : EnvironmentToolResultToAgentMessage

/**
 * Represents the content of [TERMINATION][EnvironmentToAgentTerminationMessage] messages sent from the environment.
 *
 * @property agentId Identifier for the agent receiving the message.
 * @property message Textual representation of the environment changes.
 */
@Serializable
public data class EnvironmentToAgentTerminationContent(
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
public data class EnvironmentToAgentTerminationMessage(
    val sessionUuid: Uuid,
    val content: EnvironmentToAgentTerminationContent? = null,
    val error: AgentServiceError? = null,
) : EnvironmentToAgentMessage

/**
 * Represents an environment error that occurred **outside tool execution**.
 * For errors resulting from failed [Tool][ai.koog.agents.core.tools.Tool] executions,
 * use [EnvironmentToolResultSingleToAgentMessage] instead.
 *
 * @property sessionUuid Unique identifier for the session.
 * @property error Environment error details.
 * @see <a href="https://youtrack.jetbrains.com/articles/JBRes-A-102/#:~:text=ERROR%20messages%20are%20mostly%20not%20used%20now">Knowledge Base Article</a>
 */
@Serializable
@SerialName("ERROR")
public data class EnvironmentToAgentErrorMessage(
    val sessionUuid: Uuid,
    val error: AgentServiceError,
) : EnvironmentToAgentMessage
