package ai.koog.a2a.transport.jsonrpc.model

import ai.koog.a2a.model.ListTasksResponse
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonRpcSerializationTest {
    @Test
    fun testRequestIdStringId() {
        val requestId = RequestId.StringId("test-id")
        val requestIdJson = """"test-id""""

        val serialized = JSONRPCJson.encodeToString<RequestId>(requestId)
        assertEquals(requestIdJson, serialized)

        val deserialized = JSONRPCJson.decodeFromString<RequestId>(requestIdJson)
        assertEquals(requestId, deserialized)
    }

    @Test
    fun testRequestIdNumberId() {
        val requestId = RequestId.NumberId(123L)
        val requestIdJson = """123"""

        val serialized = JSONRPCJson.encodeToString<RequestId>(requestId)
        assertEquals(requestIdJson, serialized)

        val deserialized = JSONRPCJson.decodeFromString<RequestId>(requestIdJson)
        assertEquals(requestId, deserialized)
    }

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
            jsonrpc = JSONRPC_VERSION,
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
            params = JsonPrimitive("notification-params"),
            jsonrpc = JSONRPC_VERSION,
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
            jsonrpc = JSONRPC_VERSION,
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
            result = JsonPrimitive(100),
            jsonrpc = JSONRPC_VERSION,
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
            error = JSONRPCError(code = -32602, message = "Invalid params"),
            jsonrpc = JSONRPC_VERSION,
        )

        //language=JSON
        val errorResponseJson = """{"id":123,"error":{"code":-32602,"message":"Invalid params"},"jsonrpc":"2.0"}"""

        val serialized = JSONRPCJson.encodeToString(response)
        assertEquals(errorResponseJson, serialized)

        val deserialized = JSONRPCJson.decodeFromString<JSONRPCMessage>(errorResponseJson)
        assertEquals(response, deserialized)
    }

    @Test
    fun testJSONRPCErrorResponseNullId() {
        val response: JSONRPCMessage = JSONRPCErrorResponse(
            id = null,
            error = JSONRPCError(code = -32700, message = "Parse error"),
            jsonrpc = JSONRPC_VERSION,
        )

        // JSON-RPC 2.0 requires an explicit null id when the request id is unknown
        //language=JSON
        val errorResponseNullIdJson = """{"id":null,"error":{"code":-32700,"message":"Parse error"},"jsonrpc":"2.0"}"""

        val serialized = JSONRPCJson.encodeToString(response)
        assertEquals(errorResponseNullIdJson, serialized)

        val deserialized = JSONRPCJson.decodeFromString<JSONRPCMessage>(errorResponseNullIdJson)
        assertEquals(response, deserialized)

        // Be lenient and accept a missing id too
        //language=JSON
        val errorResponseMissingIdJson = """{"error":{"code":-32700,"message":"Parse error"},"jsonrpc":"2.0"}"""

        assertEquals(response, JSONRPCJson.decodeFromString<JSONRPCMessage>(errorResponseMissingIdJson))
    }

    @Test
    fun testEmptyNextPageTokenOfListTasksResponseIsEncoded() {
        // `nextPageToken` is REQUIRED in the ProtoJSON response: it must be present as an empty string on the last page.
        val response = ListTasksResponse(tasks = emptyList(), nextPageToken = "", pageSize = 50, totalSize = 0)
        //language=JSON
        val responseJson = """{"tasks":[],"nextPageToken":"","pageSize":50,"totalSize":0}"""

        assertEquals(responseJson, JSONRPCJson.encodeToString(response))
        assertEquals(response, JSONRPCJson.decodeFromString<ListTasksResponse>(responseJson))
    }
}
