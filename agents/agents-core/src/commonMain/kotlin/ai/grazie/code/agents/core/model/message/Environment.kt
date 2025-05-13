package ai.grazie.code.agents.core.model.message

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.agent.config.AgentConfig
import ai.grazie.code.agents.core.agent.entity.AgentStrategy

data class AgentEnvironmentInitializeMessageContent(
    override val agentId: String,
    override val message: String,
    val config: AgentConfig
) : EnvironmentInitializeToAgentContent()

data class AgentEnvironmentToAgentInitializeMessage(
    override val content: AgentEnvironmentInitializeMessageContent,
    val toolsForStages: Map<String, List<ToolDescriptor>>,
    val agent: AgentStrategy,
) : EnvironmentInitializeToAgentMessage()

data class AgentEnvironmentToolResultToAgentContent(
    override val toolCallId: String?,
    override val toolName: String,
    override val agentId: String,
    override val message: String,
    val toolResult: ToolResult? = null
) : EnvironmentToolResultToAgentContent()