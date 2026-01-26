package ai.koog.protocol.model

import kotlinx.serialization.Serializable

/**
 * Top level model for flow configuration deserialization.
 */
@Serializable
public data class FlowModel(
    val id: String,
    val version: String,
    val defaultModel: String? = null,
    val agents: List<FlowAgentModel> = emptyList(),
    val tools: List<FlowToolModel> = emptyList(),
    val transitions: List<FlowTransitionModel> = emptyList()
)
