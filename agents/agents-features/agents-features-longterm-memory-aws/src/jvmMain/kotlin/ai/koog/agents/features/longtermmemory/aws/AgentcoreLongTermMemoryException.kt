package ai.koog.agents.features.longtermmemory.aws

/**
 * Base exception for AgentCore Memory operations.
 *
 * Wraps AWS SDK failures so that callers of [AgentcoreSearchStorage]
 * do not need to depend on AWS-specific exception types.
 */
public open class AgentcoreLongTermMemoryException : Exception {
    /**
     * Creates an exception with the given error [message].
     */
    public constructor(message: String) : super(message)

    /**
     * Creates an exception with the given error [message] and [cause].
     */
    public constructor(message: String, cause: Throwable) : super(message, cause)

    /**
     * Thrown when a memory retrieve operation fails.
     */
    public class RetrieveException(message: String, cause: Throwable) :
        AgentcoreLongTermMemoryException(message, cause)

    /**
     * Thrown when memory configuration is invalid.
     */
    public class ConfigurationException(message: String) :
        AgentcoreLongTermMemoryException(message)
}
