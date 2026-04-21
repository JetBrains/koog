package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy

/**
 * Provides functionality to generate diagram representations of AI agent strategies configured as graphs.
 * Implementations of this interface are responsible for translating an `AIAgentGraphStrategy` into a textual
 * or visual format suitable for understanding and visualization of agent workflows.
 */
public interface DiagramGenerator {

    /**
     * Generates a diagram representation of the provided AI agent graph strategy.
     *
     * @param graph The AI agent graph strategy to be translated into a diagram format.
     *              This strategy consists of interconnected nodes defining the workflow
     *              of an AI agent.
     * @return A string representation of the diagram generated from the given graph strategy.
     */
    public fun generate(graph: AIAgentGraphStrategy<*, *>): String
}
