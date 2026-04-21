package ai.koog.agents.core.agent.exception

import ai.koog.agents.core.agent.entity.AIAgentNodeBase

// TODO: how it differs from AgentRuntimeException?

/**
 * Represents a custom exception class for use in AI Agent-related processes.
 *
 * This exception is thrown when the AI Agent encounters a specific problem
 * that requires handling or reporting. It extends the base `Exception` class
 * and provides a detailed message for easier identification of the issue.
 *
 * @constructor Creates an instance of `AgentException`.
 * @param problem Description of the problem encountered by the AI Agent.
 * @param throwable Optional cause of the exception, which can provide additional
 * context about the error.
 */
public open class AIAgentException(problem: String, throwable: Throwable? = null) :
    Exception("AI Agent has run into a problem: $problem", throwable)

/**
 * Exception thrown when an agent becomes stuck in a specific node during the execution
 * of the agent graph. This typically occurs when the output produced by the node does not
 * match any conditions on the available edges, preventing further progress in the graph execution.
 *
 * @param node The node in which the agent becomes stuck.
 * @param output The output produced by the node that doesn't match any edge conditions.
 */
public class AIAgentStuckInTheNodeException(node: AIAgentNodeBase<*, *>, output: Any?) :
    AIAgentException(
        "When executing agent graph, stuck in node ${node.name} " +
            "because output $output doesn't match any condition on available edges."
    )

/**
 * Exception thrown when an agent exceeds the maximum allowed number of iterations during execution.
 *
 * This exception indicates that the agent could not complete its task within the specified number
 * of steps, as defined by the `maxAgentIterations` parameter in the agent's configuration. To
 * resolve this, consider increasing the value of `maxAgentIterations` to accommodate more
 * iterations for the agent's task completion.
 *
 * @constructor Creates an instance of this exception with the specified maximum number of iterations.
 * @param maxNumberOfIterations The maximum number of iterations allowed for the agent before the exception is triggered.
 */
public class AIAgentMaxNumberOfIterationsReachedException(maxNumberOfIterations: Int) :
    AIAgentException(
        "Agent couldn't finish in given number of steps ($maxNumberOfIterations). " +
            "Please, consider increasing `maxAgentIterations` value in agent's configuration"
    )

/**
 * This exception is thrown when an agent is terminated by a client request.
 * It extends the [AIAgentException] class, providing a specific message
 * indicating that the agent's operation was canceled by the client.
 *
 * @param message A descriptive message explaining the reason for termination.
 */
public class AIAgentTerminationByClientException(message: String) :
    AIAgentException("Agent was canceled by the client ($message)")
