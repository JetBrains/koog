package ai.koog.agents.core.exception

// TODO: do we really need all these exceptions being public?

/**
 * Base class for all agent runtime exceptions.
 */
public sealed class AgentRuntimeException(message: String) : RuntimeException(message)

/**
 * Thrown when the [ai.koog.agents.core.tools.ToolRegistry] cannot locate the requested [ai.koog.agents.core.tools.Tool] for execution.
 *
 * @param name Name of the tool that was not found.
 */
public class ToolNotRegisteredException(name: String) : AgentRuntimeException("Tool not registered: \"$name\"")

/**
 * Base class for representing an [ai.koog.agents.core.model.AgentServiceError] response from the server.
 */
public sealed class AgentEngineException(message: String) : AgentRuntimeException(message)

/**
 * Exception indicating that an unexpected server error occurred during an operation.
 */
public class UnexpectedServerException(message: String) : AgentEngineException(message)

/**
 * Exception thrown when an unexpected type of message is encountered in the agent service.
 */
public class UnexpectedMessageTypeException(message: String) : AgentEngineException(message)

/**
 * Exception indicating that a received message is malformed.
 */
public class MalformedMessageException(message: String) : AgentEngineException(message)

/**
 * Thrown to indicate that the requested agent could not be found.
 *
 * This exception is used when the system cannot locate an agent that matches
 * the provided identifier. Possible causes include the agent being unavailable,
 * unregistered, or the identifier being incorrect. It corresponds to the `AGENT_NOT_FOUND`
 * error type in the agent service error categorization.
 *
 * @param message The detail message explaining the error.
 */
public class AgentNotFoundException(message: String) : AgentEngineException(message)
