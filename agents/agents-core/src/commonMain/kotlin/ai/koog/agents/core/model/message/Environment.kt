package ai.koog.agents.core.model.message


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
public data class AIAgentEnvironmentToolResultToAgentContent(
    override val toolCallId: String?,
    override val toolName: String,
    override val agentId: String,
    override val message: String,
    val toolResult: Any? = null
) : EnvironmentToolResultToAgentContent()
