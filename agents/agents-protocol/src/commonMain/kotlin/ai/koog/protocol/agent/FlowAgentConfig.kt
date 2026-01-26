package ai.koog.protocol.agent

import kotlinx.serialization.Serializable

/**
 * config:
 *      temperature: number
 *      max_iterations: number
 *      max_tokens: number
 *      top_p: number
 * 	    tool_choice: agent.config.tool_choice
 * 	    speculation: string
 */
@Serializable
public data class FlowAgentConfig(
    val temperature: Double? = null,
    val maxIterations: Int? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val toolChoice: ToolChoiceKind? = null,
    val speculation: String? = null
)
