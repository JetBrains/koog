package ai.koog.a2a.transport.jsonrpc

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2AMethodNotFoundException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.ErrorInfo
import ai.koog.a2a.exceptions.GenericErrorData
import ai.koog.a2a.model.Task
import ai.koog.a2a.transport.ClientCallContext
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCResponse
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JSONRPCClientTransportTest {
    private class TestJSONRPCClientTransport : JSONRPCClientTransport() {
        override suspend fun request(request: JSONRPCRequest, ctx: ClientCallContext): JSONRPCResponse =
            error("Not used")

        override fun requestStreaming(request: JSONRPCRequest, ctx: ClientCallContext): Flow<JSONRPCResponse> =
            error("Not used")

        override fun close() = Unit

        fun decodeResponse(responseJson: String): Task =
            toResponse(JSONRPCJson.decodeFromString<JSONRPCResponse>(responseJson), Task.serializer())
    }

    private val transport = TestJSONRPCClientTransport()

    private inline fun <reified T : A2AException> assertA2AError(responseJson: String): T =
        assertFailsWith<T> { transport.decodeResponse(responseJson) }

    @Test
    fun testErrorWithoutData() {
        //language=JSON
        val responseJson = """
            {"jsonrpc": "2.0", "id": "1", "error": {"code": -32601, "message": "Method not found"}}
        """.trimIndent()

        val exception = assertA2AError<A2AMethodNotFoundException>(responseJson)

        assertEquals("Method not found", exception.message)
        assertEquals(emptyList(), exception.details)
    }

    @Test
    fun testErrorWithStringData() {
        //language=JSON
        val responseJson = """
            {"jsonrpc": "2.0", "id": "1", "error": {"code": -32602, "message": "Invalid params", "data": "Field required"}}
        """.trimIndent()

        val exception = assertA2AError<A2AInvalidParamsException>(responseJson)

        assertEquals("Invalid params", exception.message)
        assertEquals(emptyList(), exception.details)
    }

    @Test
    fun testErrorWithSingleObjectData() {
        //language=JSON
        val responseJson = """
            {
              "jsonrpc": "2.0",
              "id": "1",
              "error": {
                "code": -32001,
                "message": "Task not found",
                "data": {
                  "@type": "type.googleapis.com/google.rpc.ErrorInfo",
                  "reason": "TASK_NOT_FOUND",
                  "domain": "a2a-protocol.org"
                }
              }
            }
        """.trimIndent()

        val exception = assertA2AError<A2ATaskNotFoundException>(responseJson)

        assertEquals(listOf(ErrorInfo(reason = "TASK_NOT_FOUND")), exception.details)
    }

    @Test
    fun testErrorWithArrayDataSkipsInvalidEntries() {
        //language=JSON
        val responseJson = """
            {
              "jsonrpc": "2.0",
              "id": "1",
              "error": {
                "code": -32001,
                "message": "Task not found",
                "data": [
                  {
                    "@type": "type.googleapis.com/google.rpc.ErrorInfo",
                    "reason": "TASK_NOT_FOUND",
                    "domain": "a2a-protocol.org",
                    "metadata": {"taskId": "task-1"}
                  },
                  "not an object",
                  {"reason": "NO_TYPE"},
                  {"@type": 42},
                  {"@type": "type.example.com/Custom", "value": 1}
                ]
              }
            }
        """.trimIndent()

        val exception = assertA2AError<A2ATaskNotFoundException>(responseJson)

        val expectedDetails = listOf(
            ErrorInfo(reason = "TASK_NOT_FOUND", metadata = mapOf("taskId" to "task-1")),
            GenericErrorData(
                raw = JSONRPCJson.parseToJsonElement("""{"@type": "type.example.com/Custom", "value": 1}""").jsonObject,
                type = "type.example.com/Custom",
            ),
        )
        assertEquals(expectedDetails, exception.details)
    }

    @Test
    fun testErrorWithUndecodableKnownTypeKeepsRawEntry() {
        //language=JSON
        val responseJson = """
            {
              "jsonrpc": "2.0",
              "id": "1",
              "error": {
                "code": -32001,
                "message": "Task not found",
                "data": [
                  {
                    "@type": "type.googleapis.com/google.rpc.ErrorInfo",
                    "reason": "TASK_NOT_FOUND",
                    "domain": "a2a-protocol.org",
                    "metadata": {"attempt": 1}
                  }
                ]
              }
            }
        """.trimIndent()

        val exception = assertA2AError<A2ATaskNotFoundException>(responseJson)

        val rawEntry = JSONRPCJson.parseToJsonElement(responseJson)
            .jsonObject.getValue("error")
            .jsonObject.getValue("data")
            .jsonArray.single() as JsonObject
        assertEquals(listOf(GenericErrorData(raw = rawEntry, type = ErrorInfo.TYPE)), exception.details)
    }
}
