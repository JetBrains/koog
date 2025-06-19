package ai.koog.agents.core.agent.entity

/**
 * Represents the result of a node execution in an agent.
 * This can either be a successful execution with a result or an interrupted execution with a reason.
 */
public interface NodeExecutionResult<T>

/**
 * Represents a successful node execution.
 * @param result The result of the node execution.
 */
public data class NodeExecutionSuccess<T>(
    val result: T
) : NodeExecutionResult<T>

/**
 * Represents a failure in node execution.
 * @param errorMessage The error message describing the failure.
 */
public data class NodeExecutionInterrupted<T>(
    val reason: String
) : NodeExecutionResult<T>
