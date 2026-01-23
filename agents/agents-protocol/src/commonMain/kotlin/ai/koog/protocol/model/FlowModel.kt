package ai.koog.protocol.model

import kotlinx.serialization.Serializable

/**
 * Top-level serializable model representing a complete flow configuration.
 *
 * A flow defines a graph of agents connected by transitions, along with the tools
 * available to those agents and default configuration settings.
 *
 * @property id Unique identifier for the flow
 * @property version Version string for the flow configuration format
 * @property description Optional human-readable description of the flow's purpose
 * @property defaultModel Optional default LLM model identifier used by agents that don't specify their own model
 * @property agents List of agent configurations that make up the flow
 * @property tools List of tool configurations available to agents in the flow
 * @property transitions List of transitions defining how control flows between agents
 */
@Serializable
public data class FlowModel(
    val id: String,
    val version: String,
    val description: String? = null,
    val defaultModel: String? = null,
    val agents: List<FlowAgentModel> = emptyList(),
    val tools: List<FlowToolModel> = emptyList(),
    val transitions: List<FlowTransitionModel> = emptyList()
)
