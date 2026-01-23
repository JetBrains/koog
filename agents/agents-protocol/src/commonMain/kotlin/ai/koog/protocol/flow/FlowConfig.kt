package ai.koog.protocol.flow

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.tool.FlowTool
import ai.koog.protocol.transition.FlowTransition

/**
 * Runtime configuration object for a flow after parsing from serializable format.
 *
 * This represents the complete configuration needed to execute a flow, including
 * all agents, available tools, and the transition graph connecting them.
 *
 * @property id Optional unique identifier for the flow
 * @property version Optional version string for the flow configuration
 * @property defaultModel Optional default LLM model identifier for agents that don't specify their own
 * @property agents List of runtime agent instances that make up the flow
 * @property tools List of runtime tool instances available to agents
 * @property transitions List of transitions defining how control flows between agents
 */
public data class FlowConfig(
    val id: String? = null,
    val version: String? = null,
    val defaultModel: String? = null,
    val agents: List<FlowAgent> = emptyList(),
    val tools: List<FlowTool> = emptyList(),
    val transitions: List<FlowTransition> = emptyList()
)

/**
 * Converts this [FlowConfig] to a [KoogFlow] instance that can be executed.
 *
 * @return A new [KoogFlow] instance configured with this config's properties
 */
public fun FlowConfig.toKoogFlow(
    id: String? = null,
    agents: List<FlowAgent>? = null,
    tools: List<FlowTool>? = null,
    transitions: List<FlowTransition>? = null,
): KoogFlow {
    return KoogFlow(
        id = id ?: this.id ?: "koog_flow",
        agents = agents ?: this.agents,
        tools = tools ?: this.tools,
        transitions = transitions ?: this.transitions,
        promptExecutor = null,
    )
}
