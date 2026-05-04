package ai.koog.agents.core.environment

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ToolResultKindSerializationTest {

    private val json = Json.Default

    @Test
    fun testFailureEncodesThrowableAsAIAgentError() {
        val kind: ToolResultKind = ToolResultKind.Failure(IllegalStateException("Test error"))

        val errorElement = json.encodeToJsonElement(ToolResultKind.serializer(), kind).jsonObject
        val errorObject = assertNotNull(errorElement["error"]?.jsonObject, "expected nested error object")

        val actualMessage = errorObject["message"]?.jsonPrimitive?.content
        assertEquals("Test error", actualMessage)

        val actualType = errorObject["type"]?.jsonPrimitive?.content
        assertEquals("java.lang.IllegalStateException", actualType)

        val actualStackTrace = assertNotNull(errorObject["stackTrace"]?.jsonPrimitive?.content)
        assertTrue(actualStackTrace.startsWith("java.lang.IllegalStateException: Test error"))
        assertTrue(
            actualStackTrace.contains(
                "ai.koog.agents.core.environment.ToolResultKindSerializationTest.testFailureEncodesThrowableAsAIAgentError"
            )
        )
    }

    @Test
    fun testValidationErrorEncodesThrowableAsAIAgentError() {
        val kind: ToolResultKind = ToolResultKind.ValidationError(IllegalArgumentException("Test error"))

        val errorElement = json.encodeToJsonElement(ToolResultKind.serializer(), kind).jsonObject
        val errorObject = assertNotNull(errorElement["error"]?.jsonObject, "expected nested error object")

        val actualMessage = errorObject["message"]?.jsonPrimitive?.content
        assertEquals("Test error", actualMessage)

        val actualType = errorObject["type"]?.jsonPrimitive?.content
        assertEquals("java.lang.IllegalArgumentException", actualType)
    }

    @Test
    fun testFailureWithNullErrorEncodesAsNull() {
        val kind: ToolResultKind = ToolResultKind.Failure(null)

        val errorElement = json.encodeToJsonElement(ToolResultKind.serializer(), kind).jsonObject

        assertSame(
            JsonNull,
            errorElement["error"],
            "expected error: null, got ${errorElement["error"]}"
        )
    }
}
