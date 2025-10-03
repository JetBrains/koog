package ai.koog.a2a.exceptions

import ai.koog.a2a.transport.RequestId

/**
 * Object containing all A2A error codes.
 */
@Suppress("MissingKDocForPublicAPI")
public object A2AErrorCodes {
    public const val PARSE_ERROR: Int = -32700
    public const val INVALID_REQUEST: Int = -32600
    public const val METHOD_NOT_FOUND: Int = -32601
    public const val INVALID_PARAMS: Int = -32602
    public const val INTERNAL_ERROR: Int = -32603
    public const val TASK_NOT_FOUND: Int = -32001
    public const val TASK_NOT_CANCELABLE: Int = -32002
    public const val PUSH_NOTIFICATION_NOT_SUPPORTED: Int = -32003
    public const val UNSUPPORTED_OPERATION: Int = -32004
    public const val CONTENT_TYPE_NOT_SUPPORTED: Int = -32005
    public const val INVALID_AGENT_RESPONSE: Int = -32006
    public const val AUTHENTICATED_EXTENDED_CARD_NOT_CONFIGURED: Int = -32007
}

/**
 * Base class for all A2A exceptions.
 */
public sealed class A2AException(
    public override val message: String,
    public val errorCode: Int,
    public val requestId: RequestId? = null,
) : Exception(message)

/**
 * Server received JSON that was not well-formed.
 */
public class A2AParseException(
    message: String = "Invalid JSON payload",
    requestId: RequestId? = null,
) : A2AException(message, A2AErrorCodes.PARSE_ERROR, requestId)

/**
 * The JSON payload was valid JSON, but not a valid JSON-RPC Request object.
 */
public class A2AInvalidRequestException(
    message: String = "Invalid JSON-RPC Request",
    requestId: RequestId? = null,
) : A2AException(message, A2AErrorCodes.INVALID_REQUEST, requestId)

/**
 * The requested A2A RPC method does not exist or is not supported.
 */
public class A2AMethodNotFoundException(
    message: String = "Method not found",
    requestId: RequestId? = null,
) : A2AException(message, A2AErrorCodes.METHOD_NOT_FOUND, requestId)

/**
 * The params provided for the method are invalid.
 */
public class A2AInvalidParamsException(
    message: String = "Invalid method parameters",
    requestId: RequestId? = null,
) : A2AException(message, A2AErrorCodes.INVALID_PARAMS, requestId)

/**
 * An unexpected error occurred on the server during processing.
 */
public class A2AInternalErrorException(
    message: String = "Internal server error",
    requestId: RequestId? = null,
) : A2AException(message, A2AErrorCodes.INTERNAL_ERROR, requestId)

/**
 * Reserved for implementation-defined server exceptions. A2A-specific exceptions use this range.
 */
public sealed class A2AServerException(
    message: String,
    errorCode: Int,
    requestId: RequestId? = null,
) : A2AException(message, errorCode, requestId) {
    init {
        require(errorCode in -32099..-32000) { "Server error code must be in -32099..-32000" }
    }
}

/**
 * The specified task id does not correspond to an existing or active task.
 * It might be invalid, expired, or already completed and purged.
 */
public class A2ATaskNotFoundException(
    message: String = "Task not found",
    requestId: RequestId? = null,
) : A2AServerException(message, A2AErrorCodes.TASK_NOT_FOUND, requestId)

/**
 * An attempt was made to cancel a task that is not in a cancelable state.
 * The task has already reached a terminal state like completed, failed, or canceled.
 */
public class A2ATaskNotCancelableException(
    message: String = "Task cannot be canceled",
    requestId: RequestId? = null,
) : A2AServerException(message, A2AErrorCodes.TASK_NOT_CANCELABLE, requestId)

/**
 * Client attempted to use push notification features but the server agent does not support them.
 * The server's AgentCard.capabilities.pushNotifications is false.
 */
public class A2APushNotificationNotSupportedException(
    message: String = "Push Notification is not supported",
    requestId: RequestId? = null,
) : A2AServerException(message, A2AErrorCodes.PUSH_NOTIFICATION_NOT_SUPPORTED, requestId)

/**
 * The requested operation or a specific aspect of it is not supported by this server agent implementation.
 * This is broader than just method not found.
 */
public class A2AUnsupportedOperationException(
    message: String = "This operation is not supported",
    requestId: RequestId? = null,
) : A2AServerException(message, A2AErrorCodes.UNSUPPORTED_OPERATION, requestId)

/**
 * A Media Type provided in the request's message.parts or implied for an artifact is not supported
 * by the agent or the specific skill being invoked.
 */
public class A2AContentTypeNotSupportedException(
    message: String = "Incompatible content types",
    requestId: RequestId? = null,
) : A2AServerException(message, A2AErrorCodes.CONTENT_TYPE_NOT_SUPPORTED, requestId)

/**
 * Agent generated an invalid response for the requested method.
 */
public class A2AInvalidAgentResponseException(
    message: String = "Invalid agent response type",
    requestId: RequestId? = null,
) : A2AServerException(message, A2AErrorCodes.INVALID_AGENT_RESPONSE, requestId)

/**
 * The agent does not have an Authenticated Extended Card configured.
 */
public class A2AAuthenticatedExtendedCardNotConfiguredException(
    message: String = "Authenticated Extended Card not configured",
    requestId: RequestId? = null,
) : A2AServerException(message, A2AErrorCodes.AUTHENTICATED_EXTENDED_CARD_NOT_CONFIGURED, requestId)

/**
 * Server returned some unknown error code.
 */
public class A2AUnknownException(
    message: String,
    errorCode: Int,
    requestId: RequestId? = null,
) : A2AException(message, errorCode, requestId)

/**
 * Create appropriate [A2AException] based on the provided errorCode.
 */
public fun createA2AException(
    message: String,
    errorCode: Int,
    requestId: RequestId?,
): A2AException {
    return when (errorCode) {
        A2AErrorCodes.PARSE_ERROR -> A2AParseException(message, requestId)
        A2AErrorCodes.INVALID_REQUEST -> A2AInvalidRequestException(message, requestId)
        A2AErrorCodes.METHOD_NOT_FOUND -> A2AMethodNotFoundException(message, requestId)
        A2AErrorCodes.INVALID_PARAMS -> A2AInvalidParamsException(message, requestId)
        A2AErrorCodes.INTERNAL_ERROR -> A2AInternalErrorException(message, requestId)
        A2AErrorCodes.TASK_NOT_FOUND -> A2ATaskNotFoundException(message, requestId)
        A2AErrorCodes.TASK_NOT_CANCELABLE -> A2ATaskNotCancelableException(message, requestId)
        A2AErrorCodes.PUSH_NOTIFICATION_NOT_SUPPORTED -> A2APushNotificationNotSupportedException(message, requestId)
        A2AErrorCodes.UNSUPPORTED_OPERATION -> A2AUnsupportedOperationException(message, requestId)
        A2AErrorCodes.CONTENT_TYPE_NOT_SUPPORTED -> A2AContentTypeNotSupportedException(message, requestId)
        A2AErrorCodes.INVALID_AGENT_RESPONSE -> A2AInvalidAgentResponseException(message, requestId)
        A2AErrorCodes.AUTHENTICATED_EXTENDED_CARD_NOT_CONFIGURED -> A2AAuthenticatedExtendedCardNotConfiguredException(message, requestId)
        else -> A2AUnknownException(message, errorCode, requestId)
    }
}
