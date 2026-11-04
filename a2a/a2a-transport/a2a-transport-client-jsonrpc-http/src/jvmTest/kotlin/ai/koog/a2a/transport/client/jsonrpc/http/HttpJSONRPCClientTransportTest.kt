package ai.koog.a2a.transport.client.jsonrpc.http

import ai.koog.a2a.exceptions.A2AErrorCodes
import ai.koog.a2a.exceptions.A2AErrorReasons
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.exceptions.ErrorData
import ai.koog.a2a.exceptions.ErrorInfo
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.transport.A2AMethod
import ai.koog.a2a.transport.ClientTransport
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCError
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPC_VERSION
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class HttpJSONRPCClientTransportTest {

    private val json = JSONRPCJson

    /**
     * Starts a fake JSON-RPC server at `/a2a` in [ApplicationTestBuilder] and returns a transport connected to it.
     * [handler] is invoked for each request with the decoded [JSONRPCRequest].
     */
    private fun ApplicationTestBuilder.createTransport(
        handler: suspend ApplicationCall.(JSONRPCRequest) -> Unit,
    ): HttpJSONRPCClientTransport {
        routing {
            post("/a2a") {
                assertEquals(HttpMethod.Post, call.request.httpMethod)
                assertEquals(ContentType.Application.Json, call.request.contentType().withoutParameters())

                call.handler(json.decodeFromString<JSONRPCRequest>(call.receiveText()))
            }
        }

        return HttpJSONRPCClientTransport("http://localhost/a2a", createClient {})
    }

    private suspend fun ApplicationCall.respondJsonRpcSuccess(
        request: JSONRPCRequest,
        result: JsonElement,
    ) {
        respondText(
            json.encodeToString(JSONRPCSuccessResponse(id = request.id, result = result, jsonrpc = JSONRPC_VERSION)),
            ContentType.Application.Json,
        )
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

    private fun successEventData(request: JSONRPCRequest, event: Event): String =
        json.encodeToString(
            JSONRPCSuccessResponse(id = request.id, result = json.encodeToJsonElement<Event>(event), jsonrpc = JSONRPC_VERSION)
        )

    private inline fun <reified TRequest, reified TResponse> testAPIMethod(
        method: A2AMethod,
        request: TRequest,
        expectedResponse: TResponse,
        noinline invoke: suspend ClientTransport.(TRequest) -> TResponse,
    ) {
        testApplication {
            var receivedRequest: JSONRPCRequest? = null

            val transport = createTransport { jsonRpcRequest ->
                receivedRequest = jsonRpcRequest
                respondJsonRpcSuccess(jsonRpcRequest, json.encodeToJsonElement<TResponse>(expectedResponse))
            }

            val actualResponse = transport.invoke(request)

            assertEquals(method.value, receivedRequest?.method)
            assertEquals(request, json.decodeFromJsonElement<TRequest>(receivedRequest!!.params))
            assertEquals(expectedResponse, actualResponse)

            transport.close()
        }
    }

    private inline fun <reified TRequest> testAPIMethodStreaming(
        method: A2AMethod,
        request: TRequest,
        expectedEvents: List<Event>,
        noinline invoke: ClientTransport.(TRequest) -> Flow<Event>,
    ) {
        testApplication {
            var receivedRequest: JSONRPCRequest? = null

            val transport = createTransport { jsonRpcRequest ->
                receivedRequest = jsonRpcRequest

                respondTextWriter(ContentType.Text.EventStream) {
                    expectedEvents.forEach { event ->
                        val response = JSONRPCSuccessResponse(
                            id = jsonRpcRequest.id,
                            result = json.encodeToJsonElement<Event>(event),
                            jsonrpc = JSONRPC_VERSION,
                        )

                        write("data: ${json.encodeToString(response)}\n\n")
                        flush()
                    }
                }
            }

            val actualEvents = transport.invoke(request).toList()

            assertEquals(method.value, receivedRequest?.method)
            assertEquals(request, json.decodeFromJsonElement<TRequest>(receivedRequest!!.params))
            assertEquals(expectedEvents, actualEvents)

            transport.close()
        }
    }

    @Test
    fun testUnaryRequestEndToEnd() = runTest {
        testAPIMethod(
            method = A2AMethod.GetTask,
            request = GetTaskRequest(id = "task-123", historyLength = 10),
            expectedResponse = Task(
                id = "task-123",
                contextId = "context-456",
                status = TaskStatus(
                    state = TaskState.TASK_STATE_WORKING,
                    message = Message(
                        messageId = "status-message",
                        role = Role.ROLE_AGENT,
                        parts = listOf(TextPart("Working on your request..."))
                    )
                ),
            ),
            invoke = { getTask(it) }
        )
    }

    @Test
    fun testStreamingRequestEndToEnd() = runTest {
        val request = SendMessageRequest(
            message = Message(
                messageId = "msg-1",
                role = Role.ROLE_USER,
                parts = listOf(TextPart("Hello, agent!")),
                taskId = "task-123"
            )
        )

        val expectedEvents = listOf<Event>(
            Message(
                messageId = "msg-stream-1",
                role = Role.ROLE_AGENT,
                parts = listOf(TextPart("Streaming response part 1")),
                taskId = "task-123"
            ),
            Message(
                messageId = "msg-stream-2",
                role = Role.ROLE_AGENT,
                parts = listOf(TextPart("Streaming response part 2")),
                taskId = "task-123"
            )
        )

        testAPIMethodStreaming(
            method = A2AMethod.SendMessageStreaming,
            request = request,
            expectedEvents = expectedEvents,
            invoke = { sendMessageStreaming(it) }
        )
    }

    @Test
    fun testSubscribeToTerminalTaskPlainJsonError() = runTest {
        val expectedDetails = listOf<ErrorData>(
            ErrorInfo(reason = A2AErrorReasons.UNSUPPORTED_OPERATION)
        )

        testApplication {
            val transport = createTransport { jsonRpcRequest ->
                assertEquals(A2AMethod.SubscribeToTask.value, jsonRpcRequest.method)
                respondJsonRpcError(jsonRpcRequest, A2AErrorCodes.UNSUPPORTED_OPERATION, "Task is terminal", expectedDetails)
            }

            val e = assertFailsWith<A2AUnsupportedOperationException> {
                transport.subscribeToTask(SubscribeToTaskRequest(id = "task-123")).toList()
            }

            assertEquals("Task is terminal", e.message)
            assertEquals(expectedDetails, e.details)

            transport.close()
        }
    }

    @Test
    fun testStreamingNonJsonNonSseResponseFails() = runTest {
        testApplication {
            val transport = createTransport {
                respondText("Not an event stream", ContentType.Text.Plain)
            }

            assertFailsWith<SSEClientException> {
                transport.subscribeToTask(SubscribeToTaskRequest(id = "task-123")).toList()
            }

            transport.close()
        }
    }

    private class CollectorStop : RuntimeException("Stopped by the collector")

    private val streamedMessage = Message(
        messageId = "msg-stream-1",
        role = Role.ROLE_AGENT,
        parts = listOf(TextPart("Still working...")),
        taskId = "task-123"
    )

    /**
     * Streams [streamedMessage] until the client disconnects, then completes [streamEnded].
     */
    private suspend fun ApplicationCall.respondEndlessStream(request: JSONRPCRequest, streamEnded: CompletableDeferred<Unit>) {
        respondTextWriter(ContentType.Text.EventStream) {
            try {
                while (true) {
                    write("data: ${successEventData(request, streamedMessage)}\n\n")
                    flush()
                    delay(10.milliseconds)
                }
            } finally {
                streamEnded.complete(Unit)
            }
        }
    }

    private suspend fun awaitStreamsEnded(deferreds: List<CompletableDeferred<Unit>>) {
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                deferreds.forEach { it.await() }
            }
        }
    }

    @Test
    fun testStreamingCollectorExceptionPropagatesUnchanged() = runTest {
        testApplication {
            val streamEnded = CompletableDeferred<Unit>()
            val transport = createTransport { jsonRpcRequest -> respondEndlessStream(jsonRpcRequest, streamEnded) }

            val thrown = CollectorStop()
            val e = assertFailsWith<CollectorStop> {
                transport.subscribeToTask(SubscribeToTaskRequest(id = "task-123")).collect { throw thrown }
            }

            assertSame(thrown, e)
            awaitStreamsEnded(listOf(streamEnded))

            transport.close()
        }
    }

    @Test
    fun testStreamingErrorEventThrowsA2AException() = runTest {
        val expectedDetails = listOf<ErrorData>(ErrorInfo(reason = A2AErrorReasons.TASK_NOT_FOUND))

        testApplication {
            val transport = createTransport { jsonRpcRequest ->
                val error = JSONRPCErrorResponse(
                    id = jsonRpcRequest.id,
                    error = JSONRPCError(
                        code = A2AErrorCodes.TASK_NOT_FOUND,
                        message = "Task not found",
                        data = json.encodeToJsonElement(expectedDetails),
                    ),
                    jsonrpc = JSONRPC_VERSION,
                )

                respondTextWriter(ContentType.Text.EventStream) {
                    write("data: ${successEventData(jsonRpcRequest, streamedMessage)}\n\n")
                    write("data: ${json.encodeToString(error)}\n\n")
                    flush()
                }
            }

            val received = mutableListOf<Event>()
            val e = assertFailsWith<A2ATaskNotFoundException> {
                transport.subscribeToTask(SubscribeToTaskRequest(id = "task-123")).collect { received += it }
            }

            assertEquals(listOf<Event>(streamedMessage), received)
            assertEquals("Task not found", e.message)
            assertEquals(expectedDetails, e.details)

            transport.close()
        }
    }

    @Test
    fun testStreamingEventWithoutDataFails() = runTest {
        testApplication {
            val transport = createTransport {
                respondTextWriter(ContentType.Text.EventStream) {
                    write("event: no-data\n\n")
                    flush()
                }
            }

            val e = assertFailsWith<IllegalArgumentException> {
                transport.subscribeToTask(SubscribeToTaskRequest(id = "task-123")).toList()
            }

            assertEquals("SSE data must not be null", e.message)

            transport.close()
        }
    }

    @Test
    fun testStreamingFirstAndTakeCloseTheConnection() = runTest {
        testApplication {
            val streamsEnded = List(2) { CompletableDeferred<Unit>() }
            var call = 0
            val transport = createTransport { jsonRpcRequest -> respondEndlessStream(jsonRpcRequest, streamsEnded[call++]) }

            assertEquals(streamedMessage, transport.subscribeToTask(SubscribeToTaskRequest(id = "task-123")).first())
            assertEquals(
                listOf<Event>(streamedMessage),
                transport.subscribeToTask(SubscribeToTaskRequest(id = "task-123")).take(1).toList()
            )
            awaitStreamsEnded(streamsEnded)

            transport.close()
        }
    }
}
