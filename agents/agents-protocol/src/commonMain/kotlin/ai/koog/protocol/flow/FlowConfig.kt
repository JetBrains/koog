package ai.koog.protocol.flow

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.tool.FlowTool
import ai.koog.protocol.transition.FlowTransition

/**
 *
 */
public data class FlowConfig(
    val id: String? = null,
    val version: String? = null,
    val defaultModel: String? = null,
    val agents: List<FlowAgent> = emptyList(),
    val tools: List<FlowTool> = emptyList(),
    val transitions: List<FlowTransition> = emptyList()
)
