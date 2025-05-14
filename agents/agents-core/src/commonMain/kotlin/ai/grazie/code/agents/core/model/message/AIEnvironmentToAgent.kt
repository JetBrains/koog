package ai.grazie.code.agents.core.model.message

import ai.grazie.code.agents.core.agent.config.AIAgentConfig
import ai.grazie.code.agents.core.agent.entity.AIAgentStrategy
import ai.grazie.code.agents.core.model.AIAgentServiceError
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.utils.mpp.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Base class of all messages sent from the environment to the AI agent.
 * All client messages, except for [init][AIEnvironmentInitializeToAgentMessage],
 * are associated with a specific session.
 */
@Serializable
sealed interface AIEnvironmentToAgentMessage

/**
 * Represents the content of messages sent from the environment.
 *
 * @property agentId Identifier for the agent receiving the message.
 * @property message Textual representation of environment changes, or user's prompt that initiates the conversation.
 */
@Serializable
sealed interface AIEnvironmentToAgentContent {
    val agentId: String
    val message: String
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
abstract class AIEnvironmentInitializeToAgentContent : AIEnvironmentToAgentContent {
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
abstract class AIEnvironmentInitializeToAgentMessage : AIEnvironmentToAgentMessage {
    abstract val content: AIEnvironmentInitializeToAgentContent
}

/**
 * Marker interface for messages sent from the environment to an agent, specifically related to tool results.
 * These messages convey the outcome of tool executions, including session-specific context.
 *
 * This interface extends [AIEnvironmentToAgentMessage], inheriting the characteristics of environment-to-agent communication.
 * Implementations of this interface provide additional context, such as single or multiple tool results.
 *
 * @property sessionUuid Unique identifier for the session. This ensures that the message is linked
 * to a specific session within which the tool results are relevant.
 */
@Serializable
sealed interface AIEnvironmentToolResultToAgentMessage : AIEnvironmentToAgentMessage {
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
abstract class AIEnvironmentToolResultToAgentContent : AIEnvironmentToAgentContent {
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
@Serializable
@SerialName("OBSERVATION")
data class AIEnvironmentToolResultSingleToAgentMessage(
    override val sessionUuid: UUID,
    val content: AIEnvironmentToolResultToAgentContent,
) : AIEnvironmentToolResultToAgentMessage

/**
 * Represents a message sent after multiple tool calls.
 * Bundles multiple execution outcomes: environment changes, errors encountered, etc.
 *
 * @property sessionUuid Unique identifier for the session.
 * @property content List of content messages representing multiple tool results.
 */
@Serializable
@SerialName("OBSERVATIONS_MULTIPLE")
data class AIEnvironmentToolResultMultipleToAgentMessage(
    override val sessionUuid: UUID,
    val content: List<AIEnvironmentToolResultToAgentContent>,
) : AIEnvironmentToolResultToAgentMessage

/**
 * Represents the content of [TERMINATION][AIEnvironmentToAgentTerminationMessage] messages sent from the environment.
 *
 * @property agentId Identifier for the agent receiving the message.
 * @property message Textual representation of the environment changes.
 */
@Serializable
data class AIEnvironmentToAgentTerminationContent(
    override val agentId: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val message: String = "Terminating on client behalf",
) : AIEnvironmentToAgentContent

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
data class AIEnvironmentToAgentTerminationMessage(
    val sessionUuid: UUID,
    val content: AIEnvironmentToAgentTerminationContent? = null,
    val error: AIAgentServiceError? = null,
) : AIEnvironmentToAgentMessage

/**
 * Represents an environment error that occurred **outside tool execution**.
 * For errors resulting from failed [Tool][ai.grazie.code.agents.core.tools.Tool] executions,
 * use [AIEnvironmentToolResultSingleToAgentMessage] instead.
 *
 * @property sessionUuid Unique identifier for the session.
 * @property error Environment error details.
 * @see <a href="https://youtrack.jetbrains.com/articles/JBRes-A-102/#:~:text=ERROR%20messages%20are%20mostly%20not%20used%20now">Knowledge Base Article</a>
 */
@Serializable
@SerialName("ERROR")
data class AIEnvironmentToAgentErrorMessage(
    val sessionUuid: UUID,
    val error: AIAgentServiceError,
) : AIEnvironmentToAgentMessage

/**
 * Represents the content of a message used to initialize a AI agent environment.
 *
 * This data class combines the base content needed to initialize an AI agent environment.
 *
 * @property agentId Unique identifier for the agent being initialized.
 * @property message A message providing context or additional details about the initialization.
 * @property config Configuration settings specific to the AI agent, encapsulated in the `LocalAgentConfig` class.
 */
data class AIAgentEnvironmentInitializeMessageContent(
    override val agentId: String,
    override val message: String,
    val config: AIAgentConfig
) : AIEnvironmentInitializeToAgentContent()

/**
 * Represents a message sent from the AI agent environment to initialize the agent with necessary configurations,
 * tools, and strategy information.
 *
 * @property content The initialization content containing agent-specific details, configuration, and metadata.
 * @property toolsForStages A mapping of stage identifiers to the list of tool descriptors available for each stage.
 * @property agent The strategy or behavior implementation that the AI agent will use.
 */
data class AIAgentEnvironmentToAgentInitializeMessage(
    override val content: AIAgentEnvironmentInitializeMessageContent,
    val toolsForStages: Map<String, List<ToolDescriptor>>,
    val agent: AIAgentStrategy,
) : AIEnvironmentInitializeToAgentMessage()

/**
 * Represents the content of tool result messages sent to an agent after a tool call is executed within
 * the local environment. This provides the result of the tool execution alongside metadata such as
 * the tool's name, the related agent identifier, and the tool call identifier if applicable.
 *
 * @property toolCallId Identifier for the specific tool call, used when invoking multiple tools simultaneously.
 * @property toolName Name of the tool associated with the result.
 * @property agentId Identifier for the agent that receives this message.
 * @property message Output message describing the result of the tool execution.
 * @property toolResult The result of the tool call, encapsulated as an optional `ToolResult` object.
 */
data class AIAgentEnvironmentToolResultToAgentMessageContent(
    override val toolCallId: String?,
    override val toolName: String,
    override val agentId: String,
    override val message: String,
    val toolResult: ToolResult? = null
) : AIEnvironmentToolResultToAgentContent()
