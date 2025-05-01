package ai.grazie.code.agents.local.model.message

import ai.grazie.code.agents.core.model.message.EnvironmentInitializeToAgentContent
import ai.grazie.code.agents.core.model.message.EnvironmentInitializeToAgentMessage
import ai.grazie.code.agents.core.model.message.EnvironmentToolResultToAgentContent
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.local.agent.LocalAgentStrategy
import ai.grazie.code.agents.local.agent.LocalAgentConfig

data class LocalAgentEnvironmentInitializeMessageContent(
    override val agentId: String,
    override val message: String,
    val config: LocalAgentConfig
) : EnvironmentInitializeToAgentContent()

data class LocalAgentEnvironmentToAgentInitializeMessage(
    override val content: LocalAgentEnvironmentInitializeMessageContent,
    val toolsForStages: Map<String, List<ToolDescriptor>>,
    val agent: LocalAgentStrategy,
) : EnvironmentInitializeToAgentMessage()

data class LocalAgentEnvironmentToolResultToAgentContent(
    override val toolCallId: String?,
    override val toolName: String,
    override val agentId: String,
    override val message: String,
    val toolResult: ToolResult? = null
) : EnvironmentToolResultToAgentContent()