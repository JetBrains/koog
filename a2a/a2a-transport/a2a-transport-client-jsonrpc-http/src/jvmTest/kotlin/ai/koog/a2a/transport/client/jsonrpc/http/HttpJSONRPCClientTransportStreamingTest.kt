package ai.koog.a2a.transport.client.jsonrpc.http

import ai.koog.a2a.exceptions.A2AErrorCodes
import ai.koog.a2a.exceptions.A2AErrorReasons
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.exceptions.ErrorData
import ai.koog.a2a.exceptions.ErrorInfo
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.transport.jsonrpc.A2AMethod
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCError
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPC_VERSION
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Streaming tests that need an engine with SSE support, which `MockEngine` lacks.
 */
class HttpJSONRPCClientTransportStreamingTest {

    private val json = JSONRPCJson

    private fun ApplicationTestBuilder.transport(
        handler: suspend ApplicationCall.(JSONRPCRequest) -> Unit,
    ): HttpJSONRPCClientTransport {
        routing {
            post("/a2a") {
                call.handler(json.decodeFromString<JSONRPCRequest>(call.receiveText()))
            }
        }

        return HttpJSONRPCClientTransport("http://localhost/a2a", createClient {})
    }

    private suspend fun ApplicationCall.respondJsonRpcError(
        request: JSONRPCRequest,
        code: Int,
        message: String,
        details: List<ErrorData>,
    ) {
        val response = JSONRPCErrorResponse(
            id = request.id,
            error = JSONRPCError(code = code, message = message, data = json.encodeToJsonElement(details)),
            jsonrpc = JSONRPC_VERSION,
        )

        respondText(json.encodeToString(response), ContentType.Application.Json)
    }

    @Test
    fun testSendMessageStreamingPlainJsonError() = testApplication {
        val expectedDetails = listOf<ErrorData>(
            ErrorInfo(reason = "INVALID_PARAMETERS", metadata = mapOf("field" to "message"))
        )

        val transport = transport { request ->
            assertEquals(A2AMethod.SendMessageStreaming.value, request.method)
            respondJsonRpcError(request, A2AErrorCodes.INVALID_PARAMS, "Invalid method parameters", expectedDetails)
        }

        val request = SendMessageRequest(
            message = Message(
                messageId = "msg-1",
                role = Role.ROLE_USER,
                parts = listOf(TextPart("Hello, agent!")),
            )
        )

        val e = assertFailsWith<A2AInvalidParamsException> {
            transport.sendMessageStreaming(request).toList()
        }

        assertEquals("Invalid method parameters", e.message)
        assertEquals(expectedDetails, e.details)

        transport.close()
    }

    @Test
    fun testSubscribeToTerminalTaskPlainJsonError() = testApplication {
        val expectedDetails = listOf<ErrorData>(
            ErrorInfo(reason = A2AErrorReasons.UNSUPPORTED_OPERATION)
        )

        val transport = transport { request ->
            assertEquals(A2AMethod.SubscribeToTask.value, request.method)
            respondJsonRpcError(request, A2AErrorCodes.UNSUPPORTED_OPERATION, "Task is terminal", expectedDetails)
        }

        val e = assertFailsWith<A2AUnsupportedOperationException> {
            transport.subscribeToTask(SubscribeToTaskRequest(id = "task-123")).toList()
        }

        assertEquals("Task is terminal", e.message)
        assertEquals(expectedDetails, e.details)

        transport.close()
    }

    @Test
    fun testStreamingNonJsonNonSseResponseFails() = testApplication {
        val transport = transport {
            respondText("Not an event stream", ContentType.Text.Plain)
        }

        assertFailsWith<SSEClientException> {
            transport.subscribeToTask(SubscribeToTaskRequest(id = "task-123")).toList()
        }

        transport.close()
    }
}
