package ai.koog.model

import ai.koog.flow.TransitionCondition
import ai.koog.tools.FlowTool
import kotlinx.serialization.Serializable

/**
 * JSON model for flow configuration deserialization.
 */
@Serializable
public data class FlowConfigModel(
    val id: String? = null,
    val version: String? = null,
    val agents: List<FlowAgentModel> = emptyList(),
    val tools: List<FlowTool> = emptyList(),
    val transitions: List<Transition> = emptyList()
)

/**
 * Flow transitions section.
 */
@Serializable
public data class Transition(
    public val from: String,

    public val to: String,

    public val condition: TransitionCondition? = null
)
