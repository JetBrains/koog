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

public class UnexpectedServerException(message: String) : AgentEngineException(message)

public class UnexpectedMessageTypeException(message: String) : AgentEngineException(message)

public class MalformedMessageException(message: String) : AgentEngineException(message)

public class AgentNotFoundException(message: String) : AgentEngineException(message)
