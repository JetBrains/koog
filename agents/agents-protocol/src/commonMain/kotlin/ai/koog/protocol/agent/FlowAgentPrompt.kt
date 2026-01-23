package ai.koog.protocol.agent

import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class FlowAgentPrompt(
    public val system: String,
    public val user: String? = null
)
