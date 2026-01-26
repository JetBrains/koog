package ai.koog.protocol.agent.agents.verify

import ai.koog.protocol.agent.FlowAgentParameters
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class FlowVerifyAgentParameters(
    val task: String,
    val toolNames: List<String> = emptyList()
) : FlowAgentParameters
