package ai.koog.agents.core.agent

import ai.koog.agents.utils.Closeable

/**
 * Represents a basic interface for AI agent.
 */
public interface AIAgent<Input, Output> : Closeable {
    /**
     * Represents the unique identifier for the AI agent.
     */
    public val id: String

    /**
     * Executes the AI agent with the given input and retrieves the resulting output.
     *
     * @param agentInput The input for the agent.
     * @return The output produced by the agent.
     */
    public suspend fun run(agentInput: Input): Output
}
