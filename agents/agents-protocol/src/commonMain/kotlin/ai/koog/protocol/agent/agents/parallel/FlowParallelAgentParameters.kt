package ai.koog.protocol.agent.agents.parallel

import ai.koog.protocol.agent.FlowAgentParameters
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class FlowParallelAgentParameters(
    val agents: List<String>
) : FlowAgentParameters
