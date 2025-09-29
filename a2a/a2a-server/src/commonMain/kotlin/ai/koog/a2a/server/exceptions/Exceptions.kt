package ai.koog.a2a.server.exceptions

/**
 * Indicates an error with task-related operations.
 */
public class TaskOperationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Indicates an error with message-related operations.
 */
public class MessageOperationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Indicates a failure in sending an event because it was invalid.
 */
public class InvalidEventException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Indicates errors occurring during push notification operations.
 */
public class PushNotificationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Indicates a session is not in the active state.
 */
public class SessionNotActiveException(message: String, cause: Throwable? = null) : Exception(message, cause)
