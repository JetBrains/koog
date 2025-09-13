package ai.koog.a2a.server.exceptions

import ai.koog.a2a.server.session.SessionEventProcessor

/**
 * Indicates an error with task-related operations.
 */
public class TaskOperationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Indicates an error with message-related operations.
 */
public class MessageOperationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Indicates a failure in sending an event through the [SessionEventProcessor] because of invalid event.
 */
public class InvalidEventException(message: String, cause: Throwable? = null) : Exception(message, cause)
