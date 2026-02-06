package ai.koog.protocol.agent.agents.verify

import ai.koog.protocol.agent.FlowAgentParameters
import kotlinx.serialization.Serializable

/**
 * Parameters for verification agents.
 */
@Serializable
public data class FlowVerifyAgentParameters(
    val task: String,
    val toolNames: List<String>? = null
) : FlowAgentParameters
