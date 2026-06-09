package ai.koog.a2a.exceptions

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorDataSerializationTest {
    private val json = Json { encodeDefaults = false }

    @Test
    fun testErrorInfo() {
        val data: ErrorData = ErrorInfo(
            reason = "INVALID_ARGUMENT",
            metadata = mapOf("field" to "name"),
        )

        //language=JSON
        val expectedJson = """
            {
                "@type": "type.googleapis.com/google.rpc.ErrorInfo",
                "reason": "INVALID_ARGUMENT",
                "metadata": {
                    "field": "name"
                },
                "domain": "a2a-protocol.org"
            }
        """.trimIndent()

        json.encodeToString<ErrorData>(data) shouldEqualJson expectedJson
        assertEquals(data, json.decodeFromString<ErrorData>(expectedJson))
    }

    @Test
    fun testBadRequest() {
        val data: ErrorData = BadRequest(
            fieldViolations = listOf(
                BadRequest.FieldViolation(
                    field = "name",
                    description = "must not be empty",
                    reason = "REQUIRED",
                ),
            ),
        )

        //language=JSON
        val expectedJson = """
            {
                "@type": "type.googleapis.com/google.rpc.BadRequest",
                "fieldViolations": [
                    {
                        "field": "name",
                        "description": "must not be empty",
                        "reason": "REQUIRED"
                    }
                ]
            }
        """.trimIndent()

        json.encodeToString<ErrorData>(data) shouldEqualJson expectedJson
        assertEquals(data, json.decodeFromString<ErrorData>(expectedJson))
    }

    @Test
    fun testGenericErrorData() {
        val raw = buildJsonObject {
            put("@type", "type.example.com/custom.Error")
            put("detail", "something went wrong")
        }
        val data: ErrorData = GenericErrorData(raw = raw, type = "type.example.com/custom.Error")

        //language=JSON
        val expectedJson = """
            {
                "@type": "type.example.com/custom.Error",
                "detail": "something went wrong"
            }
        """.trimIndent()

        json.encodeToString<ErrorData>(data) shouldEqualJson expectedJson
        assertEquals(data, json.decodeFromString<ErrorData>(expectedJson))
    }
}
