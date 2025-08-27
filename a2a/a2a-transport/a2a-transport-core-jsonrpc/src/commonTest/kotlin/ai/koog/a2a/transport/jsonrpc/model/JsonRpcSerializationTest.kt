package ai.koog.a2a.transport.jsonrpc.model

import ai.koog.a2a.model.RequestId
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonRpcSerializationTest {
    @Test
    fun testJSONRPCError() {
        val error = JSONRPCError(code = -32700, message = "Parse error", data = JsonPrimitive("some data"))
        //language=JSON
        val errorJson = """{"code":-32700,"message":"Parse error","data":"some data"}"""

        val serialized = JSONRPCJson.encodeToString(error)
        assertEquals(errorJson, serialized)

        val deserialized = JSONRPCJson.decodeFromString<JSONRPCError>(errorJson)
        assertEquals(error, deserialized)
    }

    @Test
    fun testJSONRPCRequest() {
        val request: JSONRPCMessage = JSONRPCRequest(
            id = RequestId.NumberId(42),
            method = "add",
            params = null
        )

        //language=JSON
        val requestJson = """{"id":42,"method":"add","jsonrpc":"2.0"}"""

        val serialized = JSONRPCJson.encodeToString(request)
        assertEquals(requestJson, serialized)

        val deserialized = JSONRPCJson.decodeFromString<JSONRPCMessage>(requestJson)
        assertEquals(request, deserialized)
    }

    @Test
    fun testJSONRPCNotification() {
        val request: JSONRPCMessage = JSONRPCNotification(
            method = "update",
            params = JsonPrimitive("notification-params")
        )

        //language=JSON
        val notificationJson = """{"method":"update","params":"notification-params","jsonrpc":"2.0"}"""

        val serialized = JSONRPCJson.encodeToString(request)
        assertEquals(notificationJson, serialized)

        val deserialized = JSONRPCJson.decodeFromString<JSONRPCMessage>(notificationJson)
        assertEquals(request, deserialized)
    }

    @Test
    fun testJSONRPCNotificationWithoutParams() {
        val request: JSONRPCMessage = JSONRPCNotification(
            method = "notify",
            params = null
        )

        //language=JSON
        val notificationWithoutParamsJson = """{"method":"notify","jsonrpc":"2.0"}"""

        val serialized = JSONRPCJson.encodeToString(request)
        assertEquals(notificationWithoutParamsJson, serialized)

        val deserialized = JSONRPCJson.decodeFromString<JSONRPCMessage>(notificationWithoutParamsJson)
        assertEquals(request, deserialized)
    }

    @Test
    fun testJSONRPCSuccessResponse() {
        val response: JSONRPCMessage = JSONRPCSuccessResponse(
            id = RequestId.NumberId(99),
            result = JsonPrimitive(100)
        )

        //language=JSON
        val successResponseJson = """{"id":99,"result":100,"jsonrpc":"2.0"}"""

        val serialized = JSONRPCJson.encodeToString(response)
        assertEquals(successResponseJson, serialized)

        val deserialized = JSONRPCJson.decodeFromString<JSONRPCMessage>(successResponseJson)
        assertEquals(response, deserialized)
    }

    @Test
    fun testJSONRPCErrorResponse() {
        val response: JSONRPCMessage = JSONRPCErrorResponse(
            id = RequestId.NumberId(123),
            error = JSONRPCError(code = -32602, message = "Invalid params")
        )

        //language=JSON
        val errorResponseJson = """{"id":123,"error":{"code":-32602,"message":"Invalid params"},"jsonrpc":"2.0"}"""

        val serialized = JSONRPCJson.encodeToString(response)
        assertEquals(errorResponseJson, serialized)

        val deserialized = JSONRPCJson.decodeFromString<JSONRPCMessage>(errorResponseJson)
        assertEquals(response, deserialized)
    }

    @Test
    fun testJSONRPCErrorResponseWithoutId() {
        val response: JSONRPCMessage = JSONRPCErrorResponse(
            id = null,
            error = JSONRPCError(code = -32700, message = "Parse error")
        )

        //language=JSON
        val errorResponseWithoutIdJson = """{"error":{"code":-32700,"message":"Parse error"},"jsonrpc":"2.0"}"""

        val serialized = JSONRPCJson.encodeToString(response)
        assertEquals(errorResponseWithoutIdJson, serialized)

        val deserialized = JSONRPCJson.decodeFromString<JSONRPCMessage>(errorResponseWithoutIdJson)
        assertEquals(response, deserialized)
    }
}
