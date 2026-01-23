package ai.koog.flow

import ai.koog.agent.FlowAgent
import ai.koog.model.Transition
import kotlinx.serialization.Serializable

/**
 * flow:
 *      id: string
 *      version: string
 */
@Serializable
public data class FlowConfig(
    val id: String? = null,
    val version: String? = null,
    val agents: List<FlowAgent> = emptyList(),
    val transitions: List<Transition> = emptyList()
)
