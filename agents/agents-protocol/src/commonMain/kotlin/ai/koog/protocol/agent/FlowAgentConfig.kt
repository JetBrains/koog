package ai.koog.protocol.agent

import kotlinx.serialization.Serializable

/**
 * Configuration parameters for controlling an agent's LLM behavior and execution.
 *
 * These settings control how the agent interacts with the underlying language model
 * and manages its execution lifecycle.
 *
 * @property temperature Controls randomness in LLM responses (0.0 = deterministic, higher = more random). Typically 0.0-2.0
 * @property maxIterations Maximum number of iterations the agent can execute before stopping
 * @property maxTokens Maximum number of tokens the LLM can generate in a single response
 * @property topP Nucleus sampling parameter controlling diversity (0.0-1.0). Lower values = more focused outputs
 * @property toolChoice Controls whether and how the agent can use tools (Auto, None, Required, or specific tool name)
 * @property speculation Optional speculation strategy for advanced execution optimization
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
