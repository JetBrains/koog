package ai.grazie.code.agents.core.model.message

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.agent.entity.LocalAgentStrategy

/**
 * Represents the content of a message used to initialize a AI agent environment.
 *
 * This data class combines the base content needed to initialize an AI agent environment.
 *
 * @property agentId Unique identifier for the agent being initialized.
 * @property message A message providing context or additional details about the initialization.
 * @property config Configuration settings specific to the AI agent, encapsulated in the `LocalAgentConfig` class.
 */
public data class LocalAgentEnvironmentInitializeMessageContent(
    override val agentId: String,
    override val message: String,
    val config: LocalAgentConfig
) : EnvironmentInitializeToAgentContent()

/**
 * Represents a message sent from the AI agent environment to initialize the agent with necessary configurations,
 * tools, and strategy information.
 *
 * @property content The initialization content containing agent-specific details, configuration, and metadata.
 * @property toolsForStages A mapping of stage identifiers to the list of tool descriptors available for each stage.
 * @property agent The strategy or behavior implementation that the AI agent will use.
 */
public data class LocalAgentEnvironmentToAgentInitializeMessage(
    override val content: LocalAgentEnvironmentInitializeMessageContent,
    val toolsForStages: Map<String, List<ToolDescriptor>>,
    val agent: LocalAgentStrategy,
) : EnvironmentInitializeToAgentMessage()

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
public data class LocalAgentEnvironmentToolResultToAgentContent(
    override val toolCallId: String?,
    override val toolName: String,
    override val agentId: String,
    override val message: String,
    val toolResult: ToolResult? = null
) : EnvironmentToolResultToAgentContent()
