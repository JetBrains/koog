package ai.grazie.code.agents.core.exception

/**
 * Base class for all agent runtime exceptions.
 */
sealed class AgentRuntimeException(message: String) : RuntimeException(message)

/**
 * Thrown when the [ai.grazie.code.agents.core.tools.ToolRegistry] cannot locate the requested [ai.grazie.code.agents.core.tools.Tool] for execution.
 *
 * @param name Name of the tool that was not found.
 */
class ToolNotRegisteredException(name: String) : AgentRuntimeException("Tool not registered: \"$name\"")

/**
 * Base class for representing an [ai.grazie.code.agents.core.model.AgentServiceError] response from the server.
 */
sealed class AgentEngineException(message: String) : AgentRuntimeException(message)

class UnexpectedServerException(message: String) : AgentEngineException(message)

class UnexpectedMessageTypeException(message: String) : AgentEngineException(message)

class MalformedMessageException(message: String) : AgentEngineException(message)

class AgentNotFoundException(message: String) : AgentEngineException(message)
