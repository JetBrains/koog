package ai.koog.a2a.exceptions

/**
 * Object containing all A2A error codes.
 */
@Suppress("MissingKDocForPublicAPI")
public object A2AErrorCodes {
    // Inherited from JSON-RPC
    public const val PARSE_ERROR: Int = -32700
    public const val INVALID_REQUEST: Int = -32600
    public const val METHOD_NOT_FOUND: Int = -32601
    public const val INVALID_PARAMS: Int = -32602
    public const val INTERNAL_ERROR: Int = -32603

    // A2A protocol specific
    public const val TASK_NOT_FOUND: Int = -32001
    public const val TASK_NOT_CANCELABLE: Int = -32002
    public const val PUSH_NOTIFICATION_NOT_SUPPORTED: Int = -32003
    public const val UNSUPPORTED_OPERATION: Int = -32004
    public const val CONTENT_TYPE_NOT_SUPPORTED: Int = -32005
    public const val INVALID_AGENT_RESPONSE: Int = -32006
    public const val EXTENDED_AGENT_CARD_NOT_CONFIGURED: Int = -32007
    public const val EXTENSION_SUPPORT_REQUIRED: Int = -32008
    public const val VERSION_NOT_SUPPORTED: Int = -32009
}

/**
 * `google.rpc.ErrorInfo.reason` values for A2A errors: the UPPER_SNAKE_CASE error name without the `Error` suffix.
 * They are used together with the [ErrorInfo.domain] `a2a-protocol.org`.
 */
public object A2AErrorReasons {
    public const val TASK_NOT_FOUND: String = "TASK_NOT_FOUND"
    public const val TASK_NOT_CANCELABLE: String = "TASK_NOT_CANCELABLE"
    public const val PUSH_NOTIFICATION_NOT_SUPPORTED: String = "PUSH_NOTIFICATION_NOT_SUPPORTED"
    public const val UNSUPPORTED_OPERATION: String = "UNSUPPORTED_OPERATION"
    public const val CONTENT_TYPE_NOT_SUPPORTED: String = "CONTENT_TYPE_NOT_SUPPORTED"
    public const val INVALID_AGENT_RESPONSE: String = "INVALID_AGENT_RESPONSE"
    public const val EXTENDED_AGENT_CARD_NOT_CONFIGURED: String = "EXTENDED_AGENT_CARD_NOT_CONFIGURED"
    public const val EXTENSION_SUPPORT_REQUIRED: String = "EXTENSION_SUPPORT_REQUIRED"
    public const val VERSION_NOT_SUPPORTED: String = "VERSION_NOT_SUPPORTED"

    /**
     * Returns the [ErrorInfo.reason] for the given A2A error [errorCode], or `null` if the protocol defines none
     * (e.g. for the standard JSON-RPC error codes).
     */
    public fun forCode(errorCode: Int): String? = when (errorCode) {
        A2AErrorCodes.TASK_NOT_FOUND -> TASK_NOT_FOUND
        A2AErrorCodes.TASK_NOT_CANCELABLE -> TASK_NOT_CANCELABLE
        A2AErrorCodes.PUSH_NOTIFICATION_NOT_SUPPORTED -> PUSH_NOTIFICATION_NOT_SUPPORTED
        A2AErrorCodes.UNSUPPORTED_OPERATION -> UNSUPPORTED_OPERATION
        A2AErrorCodes.CONTENT_TYPE_NOT_SUPPORTED -> CONTENT_TYPE_NOT_SUPPORTED
        A2AErrorCodes.INVALID_AGENT_RESPONSE -> INVALID_AGENT_RESPONSE
        A2AErrorCodes.EXTENDED_AGENT_CARD_NOT_CONFIGURED -> EXTENDED_AGENT_CARD_NOT_CONFIGURED
        A2AErrorCodes.EXTENSION_SUPPORT_REQUIRED -> EXTENSION_SUPPORT_REQUIRED
        A2AErrorCodes.VERSION_NOT_SUPPORTED -> VERSION_NOT_SUPPORTED
        else -> null
    }
}

/**
 * Base class for all A2A exceptions.
 */
public sealed class A2AException(
    public override val message: String,
    public val errorCode: Int,
    public val details: List<ErrorData>,
) : Exception(message) {
    /**
     * The `google.rpc.ErrorInfo.reason` value for this error as defined by the A2A protocol
     * (e.g. `TASK_NOT_FOUND`), or `null` if the protocol defines none for this error.
     */
    public val reason: String? get() = A2AErrorReasons.forCode(errorCode)

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
                A2AErrorCodes.EXTENDED_AGENT_CARD_NOT_CONFIGURED -> A2AExtendedAgentCardNotConfiguredException(message, details)
                A2AErrorCodes.EXTENSION_SUPPORT_REQUIRED -> A2AExtensionSupportRequiredException(message, details)
                A2AErrorCodes.VERSION_NOT_SUPPORTED -> A2AVersionNotSupportedException(message, details)
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
 * The agent declares support for the extended agent card (`capabilities.extendedAgentCard`),
 * but does not have one configured.
 */
public class A2AExtendedAgentCardNotConfiguredException(
    message: String = "Extended agent card not configured",
    details: List<ErrorData> = emptyList(),
) : A2AServerException(message, A2AErrorCodes.EXTENDED_AGENT_CARD_NOT_CONFIGURED, details)

/**
 * The client did not declare support for an extension that the agent marked as required.
 */
public class A2AExtensionSupportRequiredException(
    message: String = "Extension support required",
    details: List<ErrorData> = emptyList(),
) : A2AServerException(message, A2AErrorCodes.EXTENSION_SUPPORT_REQUIRED, details)

/**
 * The agent doesn't support provided A2A protocol version.
 */
public class A2AVersionNotSupportedException(
    message: String = "Version not supported",
    details: List<ErrorData> = emptyList(),
) : A2AServerException(message, A2AErrorCodes.VERSION_NOT_SUPPORTED, details)

/**
 * Server returned some unknown error code.
 */
public class A2AUnknownException(
    message: String,
    errorCode: Int,
    details: List<ErrorData> = emptyList(),
) : A2AException(message, errorCode, details)
