package ai.koog.protocol.agent.agents.task

import ai.koog.protocol.agent.FlowAgentParameters
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class FlowTaskAgentParameters(
    val task: String,
    val toolNames: List<String> = emptyList()
) : FlowAgentParameters
