package ai.koog.agents.core.agent.entity

/**
 * Represents a node in the execution graph of an AI agent that can explicitly enforce execution
 * at a specified node with optional input data.
 * This interface provides the ability to define a forced node and input,
 * overriding default execution behavior.
 */
public interface ExecutionPointNode {
    /**
     * Retrieves the current execution point, which consists of a specific node in the execution
     * graph and an optional input. If no forced node is defined, the method returns null.
     *
     * @return The execution point containing the forced node and input, or null if no forced node is set.
     */
    public fun getExecutionPoint(): ExecutionPoint?

    /**
     * Resets the currently enforced execution point for the AI agent, including clearing
     * any forced node and input data. Once the execution point is reset, the system will
     * revert to its default execution behavior without targeting a specific node explicitly.
     */
    public fun resetExecutionPoint()

    /**
     * Enforces execution to start at the specified node with the given input, overriding the default execution flow.
     *
     * @param node The node at which execution should be forced to start.
     * @param input The optional input data to provide to the forced node.
     * @throws IllegalStateException if an execution point has already been set and not yet reset.
     */
    public fun enforceExecutionPoint(node: AIAgentNodeBase<*, *>, input: Any? = null)
}

/**
 * Represents a point of execution within the AI agent's strategy graph.
 * An execution point consists of a specific node and an optional input value.
 *
 * @property node The node within the AI agent's strategy graph to be executed.
 * The node is an instance of [AIAgentNodeBase], which defines the operation to be performed
 * and its associated metadata.
 *
 * @property input The optional input data provided to the execution point.
 * The node will use this data during its execution.
 */
public data class ExecutionPoint(val node: AIAgentNodeBase<*, *>, val input: Any? = null)
