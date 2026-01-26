package ai.koog.protocol.flow

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowAgentInput
import ai.koog.protocol.tool.FlowTool
import ai.koog.protocol.transition.FlowTransition

/**
 * Framework-agnostic interface for defining and executing agent flows.
 *
 * A flow represents a directed graph of agents connected by transitions.
 * Each agent performs a specific task, and transitions define how control
 * flows between agents based on conditions.
 */
public interface Flow {

    /**
     * Unique identifier for this flow.
     */
    public val id: String

    /**
     * List of agents that participate in this flow.
     */
    public val agents: List<FlowAgent>

    /**
     * List of tools available to agents in this flow.
     */
    public val tools: List<FlowTool>

    /**
     * List of transitions that define the flow between agents.
     */
    public val transitions: List<FlowTransition>

    /**
     * Runs the flow with the provided input and returns the result.
     *
     * @param input The input to pass to the first agent in the flow
     * @return The output from the final agent in the flow
     */
    public suspend fun run(input: FlowAgentInput?): FlowAgentInput
}
