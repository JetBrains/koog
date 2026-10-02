package ai.koog.a2a.exceptions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExceptionsTest {
    @Test
    fun testCreateMapsA2AErrorCodesToExceptions() {
        val expected = mapOf(
            -32001 to A2ATaskNotFoundException::class,
            -32002 to A2ATaskNotCancelableException::class,
            -32003 to A2APushNotificationNotSupportedException::class,
            -32004 to A2AUnsupportedOperationException::class,
            -32005 to A2AContentTypeNotSupportedException::class,
            -32006 to A2AInvalidAgentResponseException::class,
            -32007 to A2AExtendedAgentCardNotConfiguredException::class,
            -32008 to A2AExtensionSupportRequiredException::class,
            -32009 to A2AVersionNotSupportedException::class,
        )

        expected.forEach { (code, exceptionClass) ->
            val exception = A2AException.create("error", code, emptyList())

            assertEquals(exceptionClass, exception::class, "code $code")
            assertEquals(code, exception.errorCode)
        }
    }

    @Test
    fun testA2AErrorsHaveErrorInfoReasons() {
        val expected = mapOf(
            A2ATaskNotFoundException() to "TASK_NOT_FOUND",
            A2ATaskNotCancelableException() to "TASK_NOT_CANCELABLE",
            A2APushNotificationNotSupportedException() to "PUSH_NOTIFICATION_NOT_SUPPORTED",
            A2AUnsupportedOperationException() to "UNSUPPORTED_OPERATION",
            A2AContentTypeNotSupportedException() to "CONTENT_TYPE_NOT_SUPPORTED",
            A2AInvalidAgentResponseException() to "INVALID_AGENT_RESPONSE",
            A2AExtendedAgentCardNotConfiguredException() to "EXTENDED_AGENT_CARD_NOT_CONFIGURED",
            A2AExtensionSupportRequiredException() to "EXTENSION_SUPPORT_REQUIRED",
            A2AVersionNotSupportedException() to "VERSION_NOT_SUPPORTED",
        )

        expected.forEach { (exception, reason) ->
            assertEquals(reason, exception.reason, exception::class.simpleName)
        }
    }

    @Test
    fun testStandardJsonRpcErrorsHaveNoReason() {
        assertNull(A2AParseException().reason)
        assertNull(A2AInvalidRequestException().reason)
        assertNull(A2AMethodNotFoundException().reason)
        assertNull(A2AInvalidParamsException().reason)
        assertNull(A2AInternalErrorException().reason)
        assertNull(A2AUnknownException("error", -32050).reason)
    }
}
