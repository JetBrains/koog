package ai.koog.a2a.transport.server.jsonrpc.http

import ai.koog.a2a.consts.A2AHeaders
import ai.koog.a2a.consts.A2AVersions
import ai.koog.a2a.exceptions.A2AErrorCodes
import ai.koog.a2a.exceptions.A2AErrorReasons
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.exceptions.ErrorData
import ai.koog.a2a.exceptions.ErrorInfo
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetExtendedAgentCardRequest
import ai.koog.a2a.model.GetTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsResponse
import ai.koog.a2a.model.ListTasksRequest
import ai.koog.a2a.model.ListTasksResponse
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.ResponseEvent
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.transport.A2AMethod
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.StateKey
import ai.koog.a2a.transport.jsonrpc.JSONRPCServerTransport
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCError
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPC_VERSION
import ai.koog.a2a.transport.jsonrpc.model.RequestId
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.sse.SSE
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as SSEClient

/**
 * Tests what the HTTP layer adds to [JSONRPCServerTransport]: headers to [ServerCallContext], the plain JSON and SSE
 * responses and the standalone server. Protocol behavior is covered by `JSONRPCServerTransportTest`.
 */
class HttpJSONRPCServerTransportTest {
    /** Answers the fixtures the tests below need; any other method is not expected to be called. */
    private object StubRequestHandler : RequestHandler {
        val task = Task(
            id = "task-1",
            contextId = "test-context-1",
            status = TaskStatus(state = TaskState.TASK_STATE_WORKING)
        )

        val events: List<Event> = listOf(
            Message(
                messageId = "message-stream-1",
                role = Role.ROLE_AGENT,
                parts = listOf(TextPart("Streaming response part 1")),
                taskId = "task-1"
            ),
            Message(
                messageId = "message-stream-2",
                role = Role.ROLE_AGENT,
                parts = listOf(TextPart("Streaming response part 2")),
                taskId = "task-1"
            )
        )

        override suspend fun onGetTask(request: GetTaskRequest, ctx: ServerCallContext): Task = task

        override fun onSendMessageStreaming(request: SendMessageRequest, ctx: ServerCallContext): Flow<Event> =
            events.asFlow()

        override fun onSubscribeToTask(request: SubscribeToTaskRequest, ctx: ServerCallContext): Flow<Event> =
            events.asFlow()

        override suspend fun onGetExtendedAgentCard(
            request: GetExtendedAgentCardRequest,
            ctx: ServerCallContext
        ): AgentCard = error("Not used")

        override suspend fun onSendMessage(request: SendMessageRequest, ctx: ServerCallContext): ResponseEvent =
            error("Not used")

        override suspend fun onListTasks(request: ListTasksRequest, ctx: ServerCallContext): ListTasksResponse =
            error("Not used")

        override suspend fun onCancelTask(request: CancelTaskRequest, ctx: ServerCallContext): Task = error("Not used")

        override suspend fun onCreateTaskPushNotificationConfig(
            request: TaskPushNotificationConfig,
            ctx: ServerCallContext
        ): TaskPushNotificationConfig = error("Not used")

        override suspend fun onGetTaskPushNotificationConfig(
            request: GetTaskPushNotificationConfigRequest,
            ctx: ServerCallContext
        ): TaskPushNotificationConfig = error("Not used")

        override suspend fun onListTaskPushNotificationConfigs(
            request: ListTaskPushNotificationConfigsRequest,
            ctx: ServerCallContext
        ): ListTaskPushNotificationConfigsResponse = error("Not used")

        override suspend fun onDeleteTaskPushNotificationConfig(
            request: DeleteTaskPushNotificationConfigRequest,
            ctx: ServerCallContext
        ) = error("Not used")
    }

    private val json = JSONRPCJson

    private fun rawRequest(method: A2AMethod, params: JsonElement, id: String): String =
        json.encodeToString(
            JSONRPCRequest(id = RequestId.StringId(id), method = method.value, params = params, jsonrpc = JSONRPC_VERSION)
        )

    @Test
    fun testUnaryRequestEndToEnd() = runTest {
        testApplication {
            install(SSE)

            routing {
                a2aJsonRpcTransportRoute("/a2a", HttpJSONRPCServerTransport(StubRequestHandler))
            }

            val response = client.post("/a2a") {
                contentType(ContentType.Application.Json)
                header(A2AHeaders.A2A_VERSION, A2AVersions.VERSION_1_0)
                setBody(rawRequest(A2AMethod.GetTask, json.encodeToJsonElement(GetTaskRequest(id = "task-1")), "unary-1"))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

            val jsonRpcResponse = json.decodeFromString<JSONRPCSuccessResponse>(response.bodyAsText())

            assertEquals(RequestId.StringId("unary-1"), jsonRpcResponse.id)
            assertEquals(StubRequestHandler.task, json.decodeFromJsonElement<Task>(jsonRpcResponse.result))
        }
    }

    @Test
    fun testStreamingRequestEndToEnd() = runTest {
        testApplication {
            install(SSE)

            val client = createClient {
                install(SSEClient)
            }

            routing {
                a2aJsonRpcTransportRoute("/a2a", HttpJSONRPCServerTransport(StubRequestHandler))
            }

            val jsonRpcResponses = buildList {
                client.sse(
                    urlString = "/a2a",
                    request = {
                        method = HttpMethod.Post

                        contentType(ContentType.Application.Json)
                        header(A2AHeaders.A2A_VERSION, A2AVersions.VERSION_1_0)
                        setBody(
                            rawRequest(
                                A2AMethod.SendMessageStreaming,
                                json.encodeToJsonElement(
                                    SendMessageRequest(
                                        Message(messageId = "msg-1", role = Role.ROLE_USER, parts = listOf(TextPart("Hi")))
                                    )
                                ),
                                "stream-1"
                            )
                        )
                    },
                ) {
                    assertEquals(HttpStatusCode.OK, call.response.status)

                    incoming
                        .map { event -> json.decodeFromString<JSONRPCSuccessResponse>(event.data!!) }
                        .collect { add(it) }
                }
            }

            assertEquals(List(2) { RequestId.StringId("stream-1") }, jsonRpcResponses.map { it.id })
            assertEquals(
                StubRequestHandler.events,
                jsonRpcResponses.map { json.decodeFromJsonElement<Event>(it.result) }
            )
        }
    }

    @Test
    fun testStreamingErrorBeforeFirstEventIsPlainJson() = runTest {
        val failingRequestHandler = object : RequestHandler by StubRequestHandler {
            override fun onSubscribeToTask(request: SubscribeToTaskRequest, ctx: ServerCallContext): Flow<Event> =
                flow { throw A2AUnsupportedOperationException("Task is in a terminal state: ${request.id}") }
        }

        testApplication {
            install(SSE)

            routing {
                a2aJsonRpcTransportRoute("/a2a", HttpJSONRPCServerTransport(failingRequestHandler))
            }

            val response = client.post("/a2a") {
                contentType(ContentType.Application.Json)
                header(A2AHeaders.A2A_VERSION, A2AVersions.VERSION_1_0)
                accept(ContentType.Text.EventStream)
                setBody(
                    rawRequest(
                        A2AMethod.SubscribeToTask,
                        json.encodeToJsonElement(SubscribeToTaskRequest(id = "task-1")),
                        "test-pre-stream-error"
                    )
                )
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

            val jsonRpcResponse = json.decodeFromString<JSONRPCErrorResponse>(response.bodyAsText())

            assertEquals(RequestId.StringId("test-pre-stream-error"), jsonRpcResponse.id)
            assertEquals(A2AErrorCodes.UNSUPPORTED_OPERATION, jsonRpcResponse.error.code)
        }
    }

    private class RawResponse(val status: HttpStatusCode, val contentType: ContentType?, val body: String)

    /**
     * Posts [method] to a transport with [handler], sending the given [headers] as they are (no `A2A-Version` is
     * added), asking for SSE if [streaming].
     */
    private suspend fun postRaw(
        method: A2AMethod,
        params: JsonElement,
        headers: Map<String, String>,
        streaming: Boolean = false,
        handler: RequestHandler = StubRequestHandler,
        customizeCallContext: suspend ApplicationCall.(ServerCallContext) -> ServerCallContext = { it },
    ): RawResponse {
        var result: RawResponse? = null

        testApplication {
            install(SSE)

            routing {
                a2aJsonRpcTransportRoute(
                    "/a2a",
                    HttpJSONRPCServerTransport(handler),
                    customizeCallContext = customizeCallContext
                )
            }

            val response = client.post("/a2a") {
                contentType(ContentType.Application.Json)
                headers.forEach { (name, value) -> header(name, value) }
                if (streaming) accept(ContentType.Text.EventStream)
                setBody(rawRequest(method, params, "version-test"))
            }

            result = RawResponse(response.status, response.contentType()?.withoutParameters(), response.bodyAsText())
        }

        return result!!
    }

    private val versionNotSupportedError = JSONRPCErrorResponse(
        id = RequestId.StringId("version-test"),
        error = JSONRPCError(
            code = A2AErrorCodes.VERSION_NOT_SUPPORTED,
            message = "A2A version '0.3' is not supported; supported: 1.0",
            data = json.encodeToJsonElement<List<ErrorData>>(
                listOf(ErrorInfo(reason = A2AErrorReasons.VERSION_NOT_SUPPORTED))
            ),
        ),
        jsonrpc = JSONRPC_VERSION,
    )

    private fun decodeTask(response: RawResponse): Task =
        json.decodeFromJsonElement<Task>(json.decodeFromString<JSONRPCSuccessResponse>(response.body).result)

    private val getTaskParams = json.encodeToJsonElement(GetTaskRequest(id = "task-1"))
    private val subscribeParams = json.encodeToJsonElement(SubscribeToTaskRequest(id = "task-1"))

    @Test
    fun testMissingVersionHeaderOnStreamingMethodsIsPlainJsonError() = runTest {
        listOf(
            A2AMethod.SendMessageStreaming to json.encodeToJsonElement(
                SendMessageRequest(Message(messageId = "m", role = Role.ROLE_USER, parts = listOf(TextPart("hi"))))
            ),
            A2AMethod.SubscribeToTask to subscribeParams,
        ).forEach { (method, params) ->
            val response = postRaw(method, params, headers = emptyMap(), streaming = true)

            assertEquals(HttpStatusCode.OK, response.status, method.value)
            assertEquals(ContentType.Application.Json, response.contentType, method.value)
            assertEquals(
                versionNotSupportedError,
                json.decodeFromString<JSONRPCErrorResponse>(response.body),
                method.value
            )
        }
    }

    @Test
    fun testVersionHeaderNameIsCaseInsensitive() = runTest {
        listOf("a2a-version", "A2A-VERSION").forEach { name ->
            val response = postRaw(A2AMethod.GetTask, getTaskParams, headers = mapOf(name to "1.0"))

            assertEquals(StubRequestHandler.task, decodeTask(response), name)
        }
    }

    @Test
    fun testRequestedVersionAndExtensionsReachRequestHandler() = runTest {
        var seen: ServerCallContext? = null

        postRaw(
            A2AMethod.GetTask,
            getTaskParams,
            headers = mapOf(
                "a2a-version" to "1.0.2",
                "a2a-extensions" to "urn:a, urn:b,,urn:c",
            ),
            handler = recordingHandler { seen = it }
        )

        assertEquals("1.0", seen?.requestedVersion)
        assertEquals(setOf("urn:a", "urn:b", "urn:c"), seen?.requestedExtensions)
        // The headers themselves are looked up case-insensitively, too
        assertEquals(listOf("1.0.2"), seen?.headers?.get(A2AHeaders.A2A_VERSION))
        assertEquals(emptyMap(), seen?.state)
    }

    private val userKey = StateKey<String>("user")

    /** Puts the `X-User` request header, read from the [ApplicationCall], into the context state. */
    private val putUserIntoState: suspend ApplicationCall.(ServerCallContext) -> ServerCallContext = { ctx ->
        ctx.copy(state = ctx.state + (userKey to request.headers["X-User"]!!))
    }

    private fun recordingHandler(onCall: (ServerCallContext) -> Unit) = object : RequestHandler by StubRequestHandler {
        override suspend fun onGetTask(request: GetTaskRequest, ctx: ServerCallContext): Task {
            onCall(ctx)
            return StubRequestHandler.task
        }
    }

    @Test
    fun testCallContextCustomizerReceivesApplicationCall() = runTest {
        var seen: ServerCallContext? = null

        val response = postRaw(
            A2AMethod.GetTask,
            getTaskParams,
            headers = mapOf(A2AHeaders.A2A_VERSION to "1.0", "X-User" to "alice"),
            handler = recordingHandler { seen = it },
            customizeCallContext = putUserIntoState,
        )

        assertEquals(StubRequestHandler.task, decodeTask(response))
        assertEquals(mapOf<StateKey<*>, Any>(userKey to "alice"), seen?.state)
        // The default context the customizer starts from is the one built from the headers
        assertEquals("1.0", seen?.requestedVersion)
        assertEquals(listOf("alice"), seen?.headers?.get("x-user"))
    }

    @Test
    fun testStandaloneTransportPassesCallContextCustomizerToRoute() = runTest {
        var seen: ServerCallContext? = null
        val port = ServerSocket(0).use { it.localPort }
        val transport = HttpJSONRPCServerTransport(recordingHandler { seen = it })

        transport.start(CIO, port, "/a2a", customizeCallContext = putUserIntoState)

        try {
            HttpClient(ClientCIO).use { httpClient ->
                val response = httpClient.post("http://localhost:$port/a2a") {
                    contentType(ContentType.Application.Json)
                    header(A2AHeaders.A2A_VERSION, A2AVersions.VERSION_1_0)
                    header("X-User", "bob")
                    setBody(rawRequest(A2AMethod.GetTask, getTaskParams, "standalone"))
                }

                assertEquals(HttpStatusCode.OK, response.status)
            }
        } finally {
            transport.stop()
        }

        assertEquals(mapOf<StateKey<*>, Any>(userKey to "bob"), seen?.state)
    }
}
