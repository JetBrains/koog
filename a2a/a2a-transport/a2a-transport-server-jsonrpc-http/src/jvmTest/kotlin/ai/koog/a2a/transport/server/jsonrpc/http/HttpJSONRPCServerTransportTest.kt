package ai.koog.a2a.transport.server.jsonrpc.http

import ai.koog.a2a.consts.A2AHeaders
import ai.koog.a2a.consts.A2AVersions
import ai.koog.a2a.exceptions.A2AErrorCodes
import ai.koog.a2a.exceptions.A2AErrorReasons
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.exceptions.ErrorData
import ai.koog.a2a.exceptions.ErrorInfo
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentInterface
import ai.koog.a2a.model.AgentSkill
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
import ai.koog.a2a.model.SecurityRequirement
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.StringList
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.jsonrpc.A2AMethod
import ai.koog.a2a.transport.jsonrpc.JSONRPCServerTransport
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCError
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPC_VERSION
import ai.koog.a2a.transport.jsonrpc.model.RequestId
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
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
import io.ktor.server.sse.SSE
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import io.ktor.client.plugins.sse.SSE as SSEClient

class HttpJSONRPCServerTransportTest {
    private object MockRequestHandler : RequestHandler {
        val agentCard = AgentCard(
            name = "Test Agent",
            description = "A test agent",
            supportedInterfaces = listOf(
                AgentInterface(
                    url = "https://api.example.com/a2a",
                    protocolBinding = TransportProtocol.JSONRPC,
                    protocolVersion = "1.0",
                )
            ),
            version = "1.0.0",
            capabilities = AgentCapabilities(),
            defaultInputModes = listOf("text/plain"),
            defaultOutputModes = listOf("text/plain"),
            securityRequirements = listOf(
                SecurityRequirement(mapOf("oauth" to StringList(listOf("read")))),
                SecurityRequirement(mapOf("api-key" to StringList(listOf("mtls")))),
            ),
            skills = listOf(
                AgentSkill(
                    id = "test-skill",
                    name = "Test Skill",
                    description = "A test skill",
                    tags = listOf("test")
                )
            )
        )

        val responseEvent: ResponseEvent = Message(
            messageId = "message-1",
            role = Role.ROLE_AGENT,
            parts = listOf(TextPart("Response message.")),
            taskId = "task-1"
        )

        val updateEvents: List<Event> = listOf(
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

        val taskGet = Task(
            id = "task-1",
            contextId = "test-context-1",
            status = TaskStatus(
                state = TaskState.TASK_STATE_WORKING
            )
        )

        val taskCancel = Task(
            id = "task-1",
            contextId = "test-context-1",
            status = TaskStatus(
                state = TaskState.TASK_STATE_CANCELED
            )
        )

        val listTasksResponse = ListTasksResponse(
            tasks = listOf(taskGet),
            nextPageToken = "",
            pageSize = 50,
            totalSize = 1,
        )

        val taskPushNotificationConfig = TaskPushNotificationConfig(
            taskId = "task-1",
            id = "notification-config-1",
            url = "https://webhook.example.com",
            token = "webhook-token-123"
        )

        val listConfigsResponse = ListTaskPushNotificationConfigsResponse(
            configs = listOf(taskPushNotificationConfig),
            nextPageToken = "",
        )

        override suspend fun onGetExtendedAgentCard(
            request: GetExtendedAgentCardRequest,
            ctx: ServerCallContext
        ): AgentCard = agentCard

        override suspend fun onSendMessage(
            request: SendMessageRequest,
            ctx: ServerCallContext
        ): ResponseEvent = responseEvent

        override fun onSendMessageStreaming(
            request: SendMessageRequest,
            ctx: ServerCallContext
        ): Flow<Event> = updateEvents.asFlow()

        override suspend fun onGetTask(
            request: GetTaskRequest,
            ctx: ServerCallContext
        ): Task = taskGet

        override suspend fun onListTasks(
            request: ListTasksRequest,
            ctx: ServerCallContext
        ): ListTasksResponse = listTasksResponse

        override suspend fun onCancelTask(
            request: CancelTaskRequest,
            ctx: ServerCallContext
        ): Task = taskCancel

        override fun onSubscribeToTask(
            request: SubscribeToTaskRequest,
            ctx: ServerCallContext
        ): Flow<Event> = updateEvents.asFlow()

        override suspend fun onCreateTaskPushNotificationConfig(
            request: TaskPushNotificationConfig,
            ctx: ServerCallContext
        ): TaskPushNotificationConfig = request

        override suspend fun onGetTaskPushNotificationConfig(
            request: GetTaskPushNotificationConfigRequest,
            ctx: ServerCallContext
        ): TaskPushNotificationConfig = taskPushNotificationConfig

        override suspend fun onListTaskPushNotificationConfigs(
            request: ListTaskPushNotificationConfigsRequest,
            ctx: ServerCallContext
        ): ListTaskPushNotificationConfigsResponse = listConfigsResponse

        override suspend fun onDeleteTaskPushNotificationConfig(
            request: DeleteTaskPushNotificationConfigRequest,
            ctx: ServerCallContext
        ) {}
    }

    private val json = JSONRPCJson

    private inline fun <reified TRequest, reified TResponse> testServerMethod(
        method: A2AMethod,
        requestId: RequestId,
        request: TRequest,
        expectedResponse: TResponse,
    ) {
        testApplication {
            install(SSE)

            val transport = JSONRPCServerTransport(MockRequestHandler)

            routing {
                a2aJsonRpcTransportRoute("/a2a", transport)
            }

            val jsonRpcRequest = JSONRPCRequest(
                id = requestId,
                method = method.value,
                params = json.encodeToJsonElement(request),
                jsonrpc = JSONRPC_VERSION,
            )

            val response = client.post("/a2a") {
                contentType(ContentType.Application.Json)
                header(A2AHeaders.A2A_VERSION, A2AVersions.VERSION_1_0)
                setBody(json.encodeToString(jsonRpcRequest))
            }

            assertEquals(HttpStatusCode.OK, response.status)

            val jsonRpcResponse = json.decodeFromString<JSONRPCSuccessResponse>(response.bodyAsText())

            assertEquals(requestId, jsonRpcResponse.id)
            assertEquals(expectedResponse, json.decodeFromJsonElement<TResponse>(jsonRpcResponse.result))
        }
    }

    private inline fun <reified TRequest, reified TResponse> testServerMethodStreaming(
        method: A2AMethod,
        requestId: RequestId,
        request: TRequest,
        expectedResponses: List<TResponse>,
    ) {
        testApplication {
            install(SSE)

            val client = createClient {
                install(SSEClient)
            }

            val transport = HttpJSONRPCServerTransport(MockRequestHandler)

            routing {
                a2aJsonRpcTransportRoute("/a2a", transport)
            }

            val jsonRpcRequest = JSONRPCRequest(
                id = requestId,
                method = method.value,
                params = json.encodeToJsonElement(request),
                jsonrpc = JSONRPC_VERSION,
            )

            val jsonrpcResponses = buildList {
                client.sse(
                    urlString = "/a2a",
                    request = {
                        this.method = HttpMethod.Post

                        contentType(ContentType.Application.Json)
                        header(A2AHeaders.A2A_VERSION, A2AVersions.VERSION_1_0)
                        setBody(json.encodeToString(jsonRpcRequest))
                    },
                ) {
                    assertEquals(HttpStatusCode.OK, call.response.status)

                    incoming
                        .map { event -> JSONRPCJson.decodeFromString<JSONRPCSuccessResponse>(event.data!!) }
                        .collect { add(it) }
                }
            }

            assertEquals(expectedResponses.size, jsonrpcResponses.size)
            assertEquals(List(jsonrpcResponses.size) { requestId }, jsonrpcResponses.map { it.id })
            assertEquals(
                expectedResponses,
                jsonrpcResponses.map { json.decodeFromJsonElement<TResponse>(it.result) }
            )
        }
    }

    @Test
    fun testGetExtendedAgentCard() = runTest {
        testServerMethod(
            method = A2AMethod.GetExtendedAgentCard,
            requestId = RequestId.StringId("test-1"),
            request = GetExtendedAgentCardRequest(),
            expectedResponse = MockRequestHandler.agentCard,
        )
    }

    @Test
    fun testSendMessage() = runTest {
        val request = SendMessageRequest(
            message = Message(
                messageId = "msg-1",
                role = Role.ROLE_USER,
                parts = listOf(TextPart("Hello, agent!")),
                taskId = "task-1"
            )
        )

        testServerMethod(
            method = A2AMethod.SendMessage,
            requestId = RequestId.StringId("test-2"),
            request = request,
            expectedResponse = MockRequestHandler.responseEvent,
        )
    }

    @Test
    fun testSendMessageStreaming() = runTest {
        val request = SendMessageRequest(
            message = Message(
                messageId = "msg-1",
                role = Role.ROLE_USER,
                parts = listOf(TextPart("Hello, agent!")),
                taskId = "task-1"
            )
        )

        testServerMethodStreaming(
            method = A2AMethod.SendMessageStreaming,
            requestId = RequestId.StringId("test-2"),
            request = request,
            expectedResponses = MockRequestHandler.updateEvents,
        )
    }

    @Test
    fun testGetTask() = runTest {
        testServerMethod(
            method = A2AMethod.GetTask,
            requestId = RequestId.StringId("test-3"),
            request = GetTaskRequest(id = "task-1"),
            expectedResponse = MockRequestHandler.taskGet,
        )
    }

    @Test
    fun testListTasks() = runTest {
        testServerMethod(
            method = A2AMethod.ListTasks,
            requestId = RequestId.StringId("test-list-tasks"),
            request = ListTasksRequest(contextId = "test-context-1", status = TaskState.TASK_STATE_WORKING),
            expectedResponse = MockRequestHandler.listTasksResponse,
        )
    }

    @Test
    fun testCancelTask() = runTest {
        testServerMethod(
            method = A2AMethod.CancelTask,
            requestId = RequestId.StringId("test-4"),
            request = CancelTaskRequest(id = "task-1"),
            expectedResponse = MockRequestHandler.taskCancel,
        )
    }

    @Test
    fun testSubscribeToTask() = runTest {
        testServerMethodStreaming(
            method = A2AMethod.SubscribeToTask,
            requestId = RequestId.StringId("test-7"),
            request = SubscribeToTaskRequest(id = "task-1"),
            expectedResponses = MockRequestHandler.updateEvents,
        )
    }

    @Test
    fun testCreateTaskPushNotificationConfig() = runTest {
        val config = TaskPushNotificationConfig(
            taskId = "task-123",
            id = "notification-config-1",
            url = "https://webhook.example.com/notifications",
            token = "webhook-token-123"
        )

        testServerMethod(
            method = A2AMethod.CreateTaskPushNotificationConfig,
            requestId = RequestId.StringId("test-5"),
            request = config,
            expectedResponse = config,
        )
    }

    @Test
    fun testGetTaskPushNotificationConfig() = runTest {
        testServerMethod(
            method = A2AMethod.GetTaskPushNotificationConfig,
            requestId = RequestId.StringId("test-6"),
            request = GetTaskPushNotificationConfigRequest(
                taskId = "task-1",
                id = "notification-config-1"
            ),
            expectedResponse = MockRequestHandler.taskPushNotificationConfig,
        )
    }

    @Test
    fun testListTaskPushNotificationConfigs() = runTest {
        testServerMethod(
            method = A2AMethod.ListTaskPushNotificationConfigs,
            requestId = RequestId.StringId("test-7"),
            request = ListTaskPushNotificationConfigsRequest(taskId = "task-1"),
            expectedResponse = MockRequestHandler.listConfigsResponse,
        )
    }

    @Test
    fun testDeleteTaskPushNotificationConfig() = runTest {
        testServerMethod<DeleteTaskPushNotificationConfigRequest, Unit>(
            method = A2AMethod.DeleteTaskPushNotificationConfig,
            requestId = RequestId.StringId("test-8"),
            request = DeleteTaskPushNotificationConfigRequest(
                taskId = "task-1",
                id = "notification-config-1"
            ),
            expectedResponse = Unit,
        )
    }

    @Test
    fun testMethodNotFound() = runTest {
        testApplication {
            install(SSE)

            val transport = HttpJSONRPCServerTransport(MockRequestHandler)

            routing {
                a2aJsonRpcTransportRoute("/a2a", transport)
            }

            val requestId = RequestId.StringId("test-9")
            val jsonRpcRequest = JSONRPCRequest(
                id = requestId,
                method = "unknown.method",
                params = JsonNull,
                jsonrpc = JSONRPC_VERSION,
            )

            val response = client.post("/a2a") {
                contentType(ContentType.Application.Json)
                header(A2AHeaders.A2A_VERSION, A2AVersions.VERSION_1_0)
                setBody(json.encodeToString(jsonRpcRequest))
            }

            assertEquals(HttpStatusCode.OK, response.status)

            val jsonRpcResponse = json.decodeFromString<JSONRPCErrorResponse>(response.bodyAsText())
            assertEquals(requestId, jsonRpcResponse.id)
            assertEquals(A2AErrorCodes.METHOD_NOT_FOUND, jsonRpcResponse.error.code)
        }
    }

    @Test
    fun testInvalidJsonRequest() = runTest {
        testApplication {
            install(SSE)

            val transport = HttpJSONRPCServerTransport(MockRequestHandler)

            routing {
                a2aJsonRpcTransportRoute("/a2a", transport)
            }

            val response = client.post("/a2a") {
                contentType(ContentType.Application.Json)
                header(A2AHeaders.A2A_VERSION, A2AVersions.VERSION_1_0)
                setBody("invalid json")
            }

            assertEquals(HttpStatusCode.OK, response.status)

            val responseBody = response.bodyAsText()

            // JSON-RPC 2.0 requires an explicit null id when the request id cannot be determined
            assertEquals(JsonNull, json.decodeFromString<JsonObject>(responseBody)["id"])

            val jsonRpcResponse = json.decodeFromString<JSONRPCErrorResponse>(responseBody)
            assertNull(jsonRpcResponse.id)
            assertEquals(A2AErrorCodes.PARSE_ERROR, jsonRpcResponse.error.code)
        }
    }

    @Test
    fun testA2AErrorIncludesErrorInfo() = runTest {
        val failingRequestHandler = object : RequestHandler by MockRequestHandler {
            override suspend fun onGetTask(request: GetTaskRequest, ctx: ServerCallContext): Task =
                throw A2ATaskNotFoundException("Task not found: ${request.id}")
        }

        testApplication {
            install(SSE)

            val transport = HttpJSONRPCServerTransport(failingRequestHandler)

            routing {
                a2aJsonRpcTransportRoute("/a2a", transport)
            }

            val requestId = RequestId.StringId("test-error-info")
            val jsonRpcRequest = JSONRPCRequest(
                id = requestId,
                method = A2AMethod.GetTask.value,
                params = json.encodeToJsonElement(GetTaskRequest(id = "task-1")),
                jsonrpc = JSONRPC_VERSION,
            )

            val response = client.post("/a2a") {
                contentType(ContentType.Application.Json)
                header(A2AHeaders.A2A_VERSION, A2AVersions.VERSION_1_0)
                setBody(json.encodeToString(jsonRpcRequest))
            }

            assertEquals(HttpStatusCode.OK, response.status)

            val expectedResponse = JSONRPCErrorResponse(
                id = requestId,
                error = JSONRPCError(
                    code = A2AErrorCodes.TASK_NOT_FOUND,
                    message = "Task not found: task-1",
                    data = json.encodeToJsonElement<List<ErrorData>>(
                        listOf(ErrorInfo(reason = A2AErrorReasons.TASK_NOT_FOUND))
                    ),
                ),
                jsonrpc = JSONRPC_VERSION,
            )

            assertEquals(expectedResponse, json.decodeFromString<JSONRPCErrorResponse>(response.bodyAsText()))
        }
    }

    @Test
    fun testStreamingErrorBeforeFirstEventIsPlainJson() = runTest {
        val failingRequestHandler = object : RequestHandler by MockRequestHandler {
            override fun onSubscribeToTask(request: SubscribeToTaskRequest, ctx: ServerCallContext): Flow<Event> =
                flow { throw A2AUnsupportedOperationException("Task is in a terminal state: ${request.id}") }
        }

        testApplication {
            install(SSE)

            val transport = HttpJSONRPCServerTransport(failingRequestHandler)

            routing {
                a2aJsonRpcTransportRoute("/a2a", transport)
            }

            val requestId = RequestId.StringId("test-pre-stream-error")
            val jsonRpcRequest = JSONRPCRequest(
                id = requestId,
                method = A2AMethod.SubscribeToTask.value,
                params = json.encodeToJsonElement(SubscribeToTaskRequest(id = "task-1")),
                jsonrpc = JSONRPC_VERSION,
            )

            val response = client.post("/a2a") {
                contentType(ContentType.Application.Json)
                header(A2AHeaders.A2A_VERSION, A2AVersions.VERSION_1_0)
                accept(ContentType.Text.EventStream)
                setBody(json.encodeToString(jsonRpcRequest))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

            val jsonRpcResponse = json.decodeFromString<JSONRPCErrorResponse>(response.bodyAsText())

            assertEquals(requestId, jsonRpcResponse.id)
            assertEquals(A2AErrorCodes.UNSUPPORTED_OPERATION, jsonRpcResponse.error.code)
        }
    }

    @Test
    fun testStreamingErrorAfterFirstEventIsStreamEvent() = runTest {
        val failingRequestHandler = object : RequestHandler by MockRequestHandler {
            override fun onSubscribeToTask(request: SubscribeToTaskRequest, ctx: ServerCallContext): Flow<Event> =
                flow {
                    emit(MockRequestHandler.taskGet)
                    throw A2AInternalErrorException("Agent failed")
                }
        }

        testApplication {
            install(SSE)

            val client = createClient {
                install(SSEClient)
            }

            val transport = HttpJSONRPCServerTransport(failingRequestHandler)

            routing {
                a2aJsonRpcTransportRoute("/a2a", transport)
            }

            val requestId = RequestId.StringId("test-mid-stream-error")
            val jsonRpcRequest = JSONRPCRequest(
                id = requestId,
                method = A2AMethod.SubscribeToTask.value,
                params = json.encodeToJsonElement(SubscribeToTaskRequest(id = "task-1")),
                jsonrpc = JSONRPC_VERSION,
            )

            val jsonRpcResponses = buildList {
                client.sse(
                    urlString = "/a2a",
                    request = {
                        method = HttpMethod.Post

                        contentType(ContentType.Application.Json)
                        header(A2AHeaders.A2A_VERSION, A2AVersions.VERSION_1_0)
                        setBody(json.encodeToString(jsonRpcRequest))
                    },
                ) {
                    incoming
                        .map { event -> json.decodeFromString<JSONRPCResponse>(event.data!!) }
                        .collect { add(it) }
                }
            }

            assertEquals(2, jsonRpcResponses.size)

            val (first, second) = jsonRpcResponses

            assertEquals(
                MockRequestHandler.taskGet,
                json.decodeFromJsonElement<Event>((first as JSONRPCSuccessResponse).result)
            )
            assertEquals(A2AErrorCodes.INTERNAL_ERROR, (second as JSONRPCErrorResponse).error.code)
        }
    }

    @Test
    fun testRequestWithoutParams() = runTest {
        testApplication {
            install(SSE)

            val transport = HttpJSONRPCServerTransport(MockRequestHandler)

            routing {
                a2aJsonRpcTransportRoute("/a2a", transport)
            }

            val response = client.post("/a2a") {
                contentType(ContentType.Application.Json)
                header(A2AHeaders.A2A_VERSION, A2AVersions.VERSION_1_0)
                //language=JSON
                setBody("""{"jsonrpc":"2.0","id":"test-no-params","method":"${A2AMethod.GetExtendedAgentCard.value}"}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)

            val jsonRpcResponse = json.decodeFromString<JSONRPCSuccessResponse>(response.bodyAsText())

            assertEquals(RequestId.StringId("test-no-params"), jsonRpcResponse.id)
            assertEquals(MockRequestHandler.agentCard, json.decodeFromJsonElement<AgentCard>(jsonRpcResponse.result))
        }
    }

    private class RawResponse(val status: HttpStatusCode, val contentType: ContentType?, val body: String)

    /**
     * Posts [method] to a transport with [handler], sending the given [headers] as they are (no `A2A-Version` is
     * added), asking for SSE if [streaming].
     */
    private suspend fun postRaw(
        method: A2AMethod,
        params: kotlinx.serialization.json.JsonElement,
        headers: Map<String, String>,
        streaming: Boolean = false,
        handler: RequestHandler = MockRequestHandler,
        transportFactory: (RequestHandler) -> JSONRPCServerTransport = { JSONRPCServerTransport(it) },
    ): RawResponse {
        var result: RawResponse? = null

        testApplication {
            install(SSE)

            routing {
                a2aJsonRpcTransportRoute("/a2a", transportFactory(handler))
            }

            val response = client.post("/a2a") {
                contentType(ContentType.Application.Json)
                headers.forEach { (name, value) -> header(name, value) }
                if (streaming) accept(ContentType.Text.EventStream)
                setBody(
                    json.encodeToString(
                        JSONRPCRequest(
                            id = RequestId.StringId("version-test"),
                            method = method.value,
                            params = params,
                            jsonrpc = JSONRPC_VERSION,
                        )
                    )
                )
            }

            result = RawResponse(response.status, response.contentType()?.withoutParameters(), response.bodyAsText())
        }

        return result!!
    }

    private fun versionNotSupportedError(requested: String, supported: String = "1.0") = JSONRPCErrorResponse(
        id = RequestId.StringId("version-test"),
        error = JSONRPCError(
            code = A2AErrorCodes.VERSION_NOT_SUPPORTED,
            message = "A2A version '$requested' is not supported; supported: $supported",
            data = json.encodeToJsonElement<List<ErrorData>>(
                listOf(ErrorInfo(reason = A2AErrorReasons.VERSION_NOT_SUPPORTED))
            ),
        ),
        jsonrpc = JSONRPC_VERSION,
    )

    private val getTaskParams = json.encodeToJsonElement(GetTaskRequest(id = "task-1"))
    private val subscribeParams = json.encodeToJsonElement(SubscribeToTaskRequest(id = "task-1"))

    @Test
    fun testMissingVersionHeaderIsVersionNotSupported() = runTest {
        val response = postRaw(A2AMethod.GetTask, getTaskParams, headers = emptyMap())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            versionNotSupportedError("0.3"),
            json.decodeFromString<JSONRPCErrorResponse>(response.body)
        )
    }

    @Test
    fun testEmptyVersionHeaderIsVersionNotSupported() = runTest {
        val response = postRaw(A2AMethod.GetTask, getTaskParams, headers = mapOf(A2AHeaders.A2A_VERSION to ""))

        assertEquals(
            versionNotSupportedError("0.3"),
            json.decodeFromString<JSONRPCErrorResponse>(response.body)
        )
    }

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
                versionNotSupportedError("0.3"),
                json.decodeFromString<JSONRPCErrorResponse>(response.body),
                method.value
            )
        }
    }

    @Test
    fun testVersionIsCheckedForEveryMethod() = runTest {
        A2AMethod.entries.forEach { method ->
            val response = postRaw(method, JsonNull, headers = mapOf(A2AHeaders.A2A_VERSION to "0.3"), streaming = method.streaming)

            assertEquals(
                A2AErrorCodes.VERSION_NOT_SUPPORTED,
                json.decodeFromString<JSONRPCErrorResponse>(response.body).error.code,
                method.value
            )
        }
    }

    @Test
    fun testUnsupportedVersionIsRejected() = runTest {
        listOf("0.3", "2.0", "99.0", "1.1").forEach { version ->
            val response = postRaw(A2AMethod.GetTask, getTaskParams, headers = mapOf(A2AHeaders.A2A_VERSION to version))

            assertEquals(
                versionNotSupportedError(version),
                json.decodeFromString<JSONRPCErrorResponse>(response.body),
                version
            )
        }
    }

    @Test
    fun testPatchVersionIsIgnored() = runTest {
        val response = postRaw(A2AMethod.GetTask, getTaskParams, headers = mapOf(A2AHeaders.A2A_VERSION to "1.0.5"))

        val result = json.decodeFromString<JSONRPCSuccessResponse>(response.body)
        assertEquals(MockRequestHandler.taskGet, json.decodeFromJsonElement<Task>(result.result))
    }

    @Test
    fun testVersionHeaderNameIsCaseInsensitive() = runTest {
        listOf("a2a-version", "A2A-VERSION").forEach { name ->
            val response = postRaw(A2AMethod.GetTask, getTaskParams, headers = mapOf(name to "1.0"))

            val result = json.decodeFromString<JSONRPCSuccessResponse>(response.body)
            assertEquals(MockRequestHandler.taskGet, json.decodeFromJsonElement<Task>(result.result), name)
        }
    }

    @Test
    fun testCustomSupportedVersions() = runTest {
        val factory = { handler: RequestHandler ->
            HttpJSONRPCServerTransport(handler, supportedVersions = setOf("1.1", "0.3"))
        }

        // Missing header stands for 0.3, which is supported here
        val missing = postRaw(A2AMethod.GetTask, getTaskParams, headers = emptyMap(), transportFactory = factory)
        assertEquals(
            MockRequestHandler.taskGet,
            json.decodeFromJsonElement<Task>(json.decodeFromString<JSONRPCSuccessResponse>(missing.body).result)
        )

        val v11 = postRaw(
            A2AMethod.GetTask,
            getTaskParams,
            headers = mapOf(A2AHeaders.A2A_VERSION to "1.1.2"),
            transportFactory = factory
        )
        assertEquals(
            MockRequestHandler.taskGet,
            json.decodeFromJsonElement<Task>(json.decodeFromString<JSONRPCSuccessResponse>(v11.body).result)
        )

        // 1.0 is not in the custom set
        val v10 = postRaw(
            A2AMethod.GetTask,
            getTaskParams,
            headers = mapOf(A2AHeaders.A2A_VERSION to "1.0"),
            transportFactory = factory
        )
        assertEquals(
            versionNotSupportedError("1.0", supported = "1.1, 0.3"),
            json.decodeFromString<JSONRPCErrorResponse>(v10.body)
        )
    }

    @Test
    fun testUnsupportedVersionDoesNotReachRequestHandler() = runTest {
        var called = false
        val handler = object : RequestHandler by MockRequestHandler {
            override suspend fun onGetTask(request: GetTaskRequest, ctx: ServerCallContext): Task {
                called = true
                return MockRequestHandler.taskGet
            }
        }

        postRaw(A2AMethod.GetTask, getTaskParams, headers = mapOf(A2AHeaders.A2A_VERSION to "0.3"), handler = handler)

        assertEquals(false, called)
    }

    @Test
    fun testRequestedVersionAndExtensionsReachRequestHandler() = runTest {
        var seen: ServerCallContext? = null
        val handler = object : RequestHandler by MockRequestHandler {
            override suspend fun onGetTask(request: GetTaskRequest, ctx: ServerCallContext): Task {
                seen = ctx
                return MockRequestHandler.taskGet
            }
        }

        postRaw(
            A2AMethod.GetTask,
            getTaskParams,
            headers = mapOf(
                "a2a-version" to "1.0.2",
                "a2a-extensions" to "urn:a, urn:b,,urn:c",
            ),
            handler = handler
        )

        assertEquals("1.0", seen?.requestedVersion)
        assertEquals(setOf("urn:a", "urn:b", "urn:c"), seen?.requestedExtensions)
        // The headers themselves are looked up case-insensitively, too
        assertEquals(listOf("1.0.2"), seen?.headers?.get(A2AHeaders.A2A_VERSION))
    }
}
