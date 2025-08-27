package ai.koog.a2a.exceptions

/**
 * Base class for all A2A exceptions.
 */
public sealed class A2AException(
    message: String,
    public val errorCode: Int
) : Exception(message)

/**
 * Server received JSON that was not well-formed.
 */
public class ParseException(
    message: String = "Invalid JSON payload",
) : A2AException(message, errorCode = -32700)

/**
 * The JSON payload was valid JSON, but not a valid JSON-RPC Request object.
 */
public class InvalidRequestException(
    message: String = "Invalid JSON-RPC Request",
) : A2AException(message, errorCode = -32600)

/**
 * The requested A2A RPC method does not exist or is not supported.
 */
public class MethodNotFoundException(
    message: String = "Method not found",
) : A2AException(message, errorCode = -32601)

/**
 * The params provided for the method are invalid.
 */
public class InvalidParamsException(
    message: String = "Invalid method parameters",
) : A2AException(message, errorCode = -32602)

/**
 * An unexpected error occurred on the server during processing.
 */
public class InternalErrorException(
    message: String = "Internal server error",
) : A2AException(message, errorCode = -32603)

/**
 * Reserved for implementation-defined server exceptions. A2A-specific exceptions use this range.
 */
public open class A2AServerException(
    message: String,
    errorCode: Int,
) : A2AException(message, errorCode) {
    init {
        require(errorCode in -32000..-32099) { "Server error code must be in -32000..-32099" }
    }
}

/**
 * The specified task id does not correspond to an existing or active task.
 * It might be invalid, expired, or already completed and purged.
 */
public class TaskNotFoundException(
    message: String = "Task not found",
) : A2AServerException(message, errorCode = -32001)

/**
 * An attempt was made to cancel a task that is not in a cancelable state.
 * The task has already reached a terminal state like completed, failed, or canceled.
 */
public class TaskNotCancelableException(
    message: String = "Task cannot be canceled",
) : A2AServerException(message, errorCode = -32002)

/**
 * Client attempted to use push notification features but the server agent does not support them.
 * The server's AgentCard.capabilities.pushNotifications is false.
 */
public class PushNotificationNotSupportedException(
    message: String = "Push Notification is not supported",
) : A2AServerException(message, errorCode = -32003)

/**
 * The requested operation or a specific aspect of it is not supported by this server agent implementation.
 * This is broader than just method not found.
 */
public class UnsupportedOperationException(
    message: String = "This operation is not supported",
) : A2AServerException(message, errorCode = -32004)

/**
 * A Media Type provided in the request's message.parts or implied for an artifact is not supported
 * by the agent or the specific skill being invoked.
 */
public class ContentTypeNotSupportedException(
    message: String = "Incompatible content types",
) : A2AServerException(message, errorCode = -32005)

/**
 * Agent generated an invalid response for the requested method.
 */
public class InvalidAgentResponseException(
    message: String = "Invalid agent response type",
) : A2AServerException(message, errorCode = -32006)

/**
 * The agent does not have an Authenticated Extended Card configured.
 */
public class AuthenticatedExtendedCardNotConfiguredException(
    message: String = "Authenticated Extended Card not configured",
) : A2AServerException(message, errorCode = -32007)
