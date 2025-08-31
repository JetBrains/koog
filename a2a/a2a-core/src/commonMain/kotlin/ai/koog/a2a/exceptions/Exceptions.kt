package ai.koog.a2a.exceptions

/**
 * Enum containing all A2A error codes.
 */
public enum class A2AErrorCode(public val value: Int) {
    PARSE_ERROR(-32700),
    INVALID_REQUEST(-32600),
    METHOD_NOT_FOUND(-32601),
    INVALID_PARAMS(-32602),
    INTERNAL_ERROR(-32603),
    TASK_NOT_FOUND(-32001),
    TASK_NOT_CANCELABLE(-32002),
    PUSH_NOTIFICATION_NOT_SUPPORTED(-32003),
    UNSUPPORTED_OPERATION(-32004),
    CONTENT_TYPE_NOT_SUPPORTED(-32005),
    INVALID_AGENT_RESPONSE(-32006),
    AUTHENTICATED_EXTENDED_CARD_NOT_CONFIGURED(-32007)
}

/**
 * Base class for all A2A exceptions.
 */
public sealed class A2AException(
    public override val message: String,
    public val errorCode: Int
) : Exception(message)

/**
 * Server received JSON that was not well-formed.
 */
public class A2AParseException(
    message: String = "Invalid JSON payload",
) : A2AException(message, errorCode = -32700)

/**
 * The JSON payload was valid JSON, but not a valid JSON-RPC Request object.
 */
public class A2AInvalidRequestException(
    message: String = "Invalid JSON-RPC Request",
) : A2AException(message, errorCode = -32600)

/**
 * The requested A2A RPC method does not exist or is not supported.
 */
public class A2AMethodNotFoundException(
    message: String = "Method not found",
) : A2AException(message, errorCode = -32601)

/**
 * The params provided for the method are invalid.
 */
public class A2AInvalidParamsException(
    message: String = "Invalid method parameters",
) : A2AException(message, errorCode = -32602)

/**
 * An unexpected error occurred on the server during processing.
 */
public class A2AInternalErrorException(
    message: String = "Internal server error",
) : A2AException(message, errorCode = -32603)

/**
 * Reserved for implementation-defined server exceptions. A2A-specific exceptions use this range.
 */
public sealed class A2AServerException(
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
public class A2ATaskNotFoundException(
    message: String = "Task not found",
) : A2AServerException(message, errorCode = -32001)

/**
 * An attempt was made to cancel a task that is not in a cancelable state.
 * The task has already reached a terminal state like completed, failed, or canceled.
 */
public class A2ATaskNotCancelableException(
    message: String = "Task cannot be canceled",
) : A2AServerException(message, errorCode = -32002)

/**
 * Client attempted to use push notification features but the server agent does not support them.
 * The server's AgentCard.capabilities.pushNotifications is false.
 */
public class A2APushNotificationNotSupportedException(
    message: String = "Push Notification is not supported",
) : A2AServerException(message, errorCode = -32003)

/**
 * The requested operation or a specific aspect of it is not supported by this server agent implementation.
 * This is broader than just method not found.
 */
public class A2AUnsupportedOperationException(
    message: String = "This operation is not supported",
) : A2AServerException(message, errorCode = -32004)

/**
 * A Media Type provided in the request's message.parts or implied for an artifact is not supported
 * by the agent or the specific skill being invoked.
 */
public class A2AContentTypeNotSupportedException(
    message: String = "Incompatible content types",
) : A2AServerException(message, errorCode = -32005)

/**
 * Agent generated an invalid response for the requested method.
 */
public class A2AInvalidAgentResponseException(
    message: String = "Invalid agent response type",
) : A2AServerException(message, errorCode = -32006)

/**
 * The agent does not have an Authenticated Extended Card configured.
 */
public class A2AAuthenticatedExtendedCardNotConfiguredException(
    message: String = "Authenticated Extended Card not configured",
) : A2AServerException(message, errorCode = -32007)

/**
 * Server returned some unknown error code.
 */
public class A2AUnknownException(
    message: String,
    errorCode: Int,
) : A2AException(message, errorCode)

/**
 * Create appropriate [A2AException] based on the provided errorCode.
 */
public fun createA2AException(
    message: String,
    errorCode: Int,
): A2AException {
    return when (errorCode) {
        A2AErrorCode.PARSE_ERROR.value -> A2AParseException(message)
        A2AErrorCode.INVALID_REQUEST.value -> A2AInvalidRequestException(message)
        A2AErrorCode.METHOD_NOT_FOUND.value -> A2AMethodNotFoundException(message)
        A2AErrorCode.INVALID_PARAMS.value -> A2AInvalidParamsException(message)
        A2AErrorCode.INTERNAL_ERROR.value -> A2AInternalErrorException(message)
        A2AErrorCode.TASK_NOT_FOUND.value -> A2ATaskNotFoundException(message)
        A2AErrorCode.TASK_NOT_CANCELABLE.value -> A2ATaskNotCancelableException(message)
        A2AErrorCode.PUSH_NOTIFICATION_NOT_SUPPORTED.value -> A2APushNotificationNotSupportedException(message)
        A2AErrorCode.UNSUPPORTED_OPERATION.value -> A2AUnsupportedOperationException(message)
        A2AErrorCode.CONTENT_TYPE_NOT_SUPPORTED.value -> A2AContentTypeNotSupportedException(message)
        A2AErrorCode.INVALID_AGENT_RESPONSE.value -> A2AInvalidAgentResponseException(message)
        A2AErrorCode.AUTHENTICATED_EXTENDED_CARD_NOT_CONFIGURED.value -> A2AAuthenticatedExtendedCardNotConfiguredException(message)
        else -> A2AUnknownException(message, errorCode)
    }
}
