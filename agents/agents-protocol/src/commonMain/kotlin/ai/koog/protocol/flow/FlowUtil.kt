package ai.koog.protocol.flow

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.transition.FlowTransition

/**
 * Utility functions for resolving the starting point of a flow.
 *
 * Provides methods to determine which agent should execute first based on the flow's
 * transition configuration.
 */
public object FlowUtil {

    /**
     * Determines and returns the first agent in a flow based on transitions.
     *
     * The first agent is identified by finding the source agent of the first transition,
     * or the first agent in the list if no transitions are defined.
     *
     * @param agents List of all agents in the flow
     * @param transitions List of transitions defining the flow graph
     * @return The agent that should execute first
     * @throws IllegalStateException if no first agent can be determined
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
     * Safely attempts to determine the first agent in a flow, returning null if unsuccessful.
     *
     * This is a null-safe variant of [getFirstAgent] that returns null instead of throwing
     * an exception when no first agent can be determined.
     *
     * @param agents List of all agents in the flow
     * @param transitions List of transitions defining the flow graph
     * @return The agent that should execute first or null if none can be determined
     */
    public fun getFirstAgentOrNull(agents: List<FlowAgent>, transitions: List<FlowTransition>): FlowAgent? {
        return transitions.firstOrNull()?.let { firstTransaction ->
            agents.find { it.name == firstTransaction.from } ?: agents.firstOrNull()
        } ?: agents.firstOrNull()
    }
}
