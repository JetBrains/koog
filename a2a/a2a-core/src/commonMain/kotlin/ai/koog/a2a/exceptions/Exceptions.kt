package ai.koog.a2a.exceptions

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
    public val details: List<ErrorData>,
) : Exception(message) {
    public companion object {
        /**
         * Create appropriate [A2AException] based on the provided errorCode.
         * Used to, e.g., restore a concrete exception type from a server response.
         */
        public fun create(
            message: String,
            errorCode: Int,
            details: List<ErrorData>,
        ): A2AException {
            return when (errorCode) {
                A2AErrorCodes.PARSE_ERROR -> A2AParseException(message, details)
                A2AErrorCodes.INVALID_REQUEST -> A2AInvalidRequestException(message, details)
                A2AErrorCodes.METHOD_NOT_FOUND -> A2AMethodNotFoundException(message, details)
                A2AErrorCodes.INVALID_PARAMS -> A2AInvalidParamsException(message, details)
                A2AErrorCodes.INTERNAL_ERROR -> A2AInternalErrorException(message, details)
                A2AErrorCodes.TASK_NOT_FOUND -> A2ATaskNotFoundException(message, details)
                A2AErrorCodes.TASK_NOT_CANCELABLE -> A2ATaskNotCancelableException(message, details)
                A2AErrorCodes.PUSH_NOTIFICATION_NOT_SUPPORTED -> A2APushNotificationNotSupportedException(message, details)
                A2AErrorCodes.UNSUPPORTED_OPERATION -> A2AUnsupportedOperationException(message, details)
                A2AErrorCodes.CONTENT_TYPE_NOT_SUPPORTED -> A2AContentTypeNotSupportedException(message, details)
                A2AErrorCodes.INVALID_AGENT_RESPONSE -> A2AInvalidAgentResponseException(message, details)
                A2AErrorCodes.AUTHENTICATED_EXTENDED_CARD_NOT_CONFIGURED -> A2AAuthenticatedExtendedCardNotConfiguredException(message, details)
                else -> A2AUnknownException(message, errorCode, details)
            }
        }
    }
}

/**
 * Server received JSON that was not well-formed.
 */
public class A2AParseException(
    message: String = "Invalid JSON payload",
    details: List<ErrorData> = emptyList(),
) : A2AException(message, A2AErrorCodes.PARSE_ERROR, details)

/**
 * The JSON payload was valid JSON, but not a valid JSON-RPC Request object.
 */
public class A2AInvalidRequestException(
    message: String = "Invalid JSON-RPC Request",
    details: List<ErrorData> = emptyList(),
) : A2AException(message, A2AErrorCodes.INVALID_REQUEST, details)

/**
 * The requested A2A RPC method does not exist or is not supported.
 */
public class A2AMethodNotFoundException(
    message: String = "Method not found",
    details: List<ErrorData> = emptyList(),
) : A2AException(message, A2AErrorCodes.METHOD_NOT_FOUND, details)

/**
 * The params provided for the method are invalid.
 */
public class A2AInvalidParamsException(
    message: String = "Invalid method parameters",
    details: List<ErrorData> = emptyList(),
) : A2AException(message, A2AErrorCodes.INVALID_PARAMS, details)

/**
 * An unexpected error occurred on the server during processing.
 */
public class A2AInternalErrorException(
    message: String = "Internal server error",
    details: List<ErrorData> = emptyList(),
) : A2AException(message, A2AErrorCodes.INTERNAL_ERROR, details)

/**
 * Reserved for implementation-defined server exceptions. A2A-specific exceptions use this range.
 */
public sealed class A2AServerException(
    message: String,
    errorCode: Int,
    details: List<ErrorData>,
) : A2AException(message, errorCode, details) {
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
    details: List<ErrorData> = emptyList(),
) : A2AServerException(message, A2AErrorCodes.TASK_NOT_FOUND, details)

/**
 * An attempt was made to cancel a task that is not in a cancelable state.
 * The task has already reached a terminal state like completed, failed, or canceled.
 */
public class A2ATaskNotCancelableException(
    message: String = "Task cannot be canceled",
    details: List<ErrorData> = emptyList(),
) : A2AServerException(message, A2AErrorCodes.TASK_NOT_CANCELABLE, details)

/**
 * Client attempted to use push notification features but the server agent does not support them.
 * The server's AgentCard.capabilities.pushNotifications is false.
 */
public class A2APushNotificationNotSupportedException(
    message: String = "Push Notification is not supported",
    details: List<ErrorData> = emptyList(),
) : A2AServerException(message, A2AErrorCodes.PUSH_NOTIFICATION_NOT_SUPPORTED, details)

/**
 * The requested operation or a specific aspect of it is not supported by this server agent implementation.
 * This is broader than just method not found.
 */
public class A2AUnsupportedOperationException(
    message: String = "This operation is not supported",
    details: List<ErrorData> = emptyList(),
) : A2AServerException(message, A2AErrorCodes.UNSUPPORTED_OPERATION, details)

/**
 * A Media Type provided in the request's message.parts or implied for an artifact is not supported
 * by the agent or the specific skill being invoked.
 */
public class A2AContentTypeNotSupportedException(
    message: String = "Incompatible content types",
    details: List<ErrorData> = emptyList(),
) : A2AServerException(message, A2AErrorCodes.CONTENT_TYPE_NOT_SUPPORTED, details)

/**
 * Agent generated an invalid response for the requested method.
 */
public class A2AInvalidAgentResponseException(
    message: String = "Invalid agent response type",
    details: List<ErrorData> = emptyList(),
) : A2AServerException(message, A2AErrorCodes.INVALID_AGENT_RESPONSE, details)

/**
 * The agent does not have an Authenticated Extended Card configured.
 */
public class A2AAuthenticatedExtendedCardNotConfiguredException(
    message: String = "Authenticated Extended Card not configured",
    details: List<ErrorData> = emptyList(),
) : A2AServerException(message, A2AErrorCodes.AUTHENTICATED_EXTENDED_CARD_NOT_CONFIGURED, details)

/**
 * Server returned some unknown error code.
 */
public class A2AUnknownException(
    message: String,
    errorCode: Int,
    details: List<ErrorData> = emptyList(),
) : A2AException(message, errorCode, details)
