package ai.koog.protocol.agent

import kotlinx.serialization.Serializable

/**
 * Prompt configuration for a flow agent including system and optional user prompts.
 */
@Serializable
public data class FlowAgentPrompt(
    public val system: String,
    public val user: String? = null
)
