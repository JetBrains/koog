package ai.koog.protocol.flow

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.transition.FlowTransition

/**
 *
 */
public object FlowUtil {

    /**
     *
     */
    public fun getFirstAgent(agents: List<FlowAgent>, transitions: List<FlowTransition>): FlowAgent {
        return getFirstAgentOrNull(agents, transitions)
            ?: error(
                "Unable to get first agent from provided data:\n" +
                    "Agents:\n${agents.joinToString { " - ${it.name}" }},\n" +
                    "Transitions:\n${transitions.joinToString { " - ${it.transitionString}" }}"
            )
    }

    /**
     *
     */
    public fun getFirstAgentOrNull(agents: List<FlowAgent>, transitions: List<FlowTransition>): FlowAgent? {
        return transitions.firstOrNull()?.let { firstTransaction ->
            agents.find { it.name == firstTransaction.from } ?: agents.firstOrNull()
        } ?: agents.firstOrNull()
    }
}
