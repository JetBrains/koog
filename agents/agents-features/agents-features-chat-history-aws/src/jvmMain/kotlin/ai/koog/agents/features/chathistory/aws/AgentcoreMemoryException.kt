package ai.koog.agents.features.chathistory.aws

/**
 * Base exception for AgentCore Memory operations.
 *
 * Wraps AWS SDK failures so that callers of [AgentcoreChatHistoryProvider]
 * do not need to depend on AWS-specific exception types.
 */
public open class AgentcoreMemoryException : RuntimeException {
    /**
     * Creates an exception with the given error [message].
     */
    public constructor(message: String) : super(message)

    /**
     * Creates an exception with the given error [message] and [cause].
     */
    public constructor(message: String, cause: Throwable) : super(message, cause)

    /**
     * Thrown when a memory read operation fails.
     */
    public class ReadException(message: String, cause: Throwable) :
        AgentcoreMemoryException(message, cause)

    /**
     * Thrown when a memory write operation fails.
     */
    public class WriteException : AgentcoreMemoryException {
        public constructor(message: String, cause: Throwable) : super(message, cause)
        public constructor(message: String) : super(message)
    }

    /**
     * Thrown when memory configuration is invalid.
     */
    public class ConfigurationException(message: String) :
        AgentcoreMemoryException(message)
}
