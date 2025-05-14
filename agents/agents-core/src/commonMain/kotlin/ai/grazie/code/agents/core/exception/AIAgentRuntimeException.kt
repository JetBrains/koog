package ai.grazie.code.agents.core.exception

/**
 * Base class for all agent runtime exceptions.
 */
sealed class AIAgentRuntimeException(message: String) : RuntimeException(message)

/**
 * Thrown when the [ai.grazie.code.agents.core.tools.ToolRegistry] cannot locate the requested [ai.grazie.code.agents.core.tools.Tool] for execution.
 *
 * @param name Name of the tool that was not found.
 */
class ToolNotRegisteredException(name: String) : AIAgentRuntimeException("Tool not registered: \"$name\"")

/**
 * Base class for representing an [ai.grazie.code.agents.core.model.AIAgentServiceError] response from the server.
 */
sealed class AIAgentEngineException(message: String) : AIAgentRuntimeException(message)

class UnexpectedServerException(message: String) : AIAgentEngineException(message)

class UnexpectedMessageTypeException(message: String) : AIAgentEngineException(message)

class MalformedMessageException(message: String) : AIAgentEngineException(message)

class AIAgentNotFoundException(message: String) : AIAgentEngineException(message)
