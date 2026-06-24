package ai.koog.a2a.transport.server.jsonrpc.http

import ai.koog.a2a.exceptions.A2AErrorCodes
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
import ai.koog.a2a.model.SendMessageRequest
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
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPC_VERSION
import ai.koog.a2a.transport.jsonrpc.model.RequestId
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import io.ktor.client.plugins.sse.sse
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
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
                    protocolVersion = "1.0.1",
                )
            ),
            version = "1.0.0",
            capabilities = AgentCapabilities(),
            defaultInputModes = listOf("text/plain"),
            defaultOutputModes = listOf("text/plain"),
            security = listOf(
                mapOf("oauth" to listOf("read")),
                mapOf("api-key" to listOf("mtls")),
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
            method = A2AMethod.GetAuthenticatedExtendedAgentCard,
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
            method = A2AMethod.ListTaskPushNotificationConfig,
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
                setBody("invalid json")
            }

            assertEquals(HttpStatusCode.OK, response.status)

            val jsonRpcResponse = json.decodeFromString<JSONRPCErrorResponse>(response.bodyAsText())
            assertNull(jsonRpcResponse.id)
            assertEquals(A2AErrorCodes.PARSE_ERROR, jsonRpcResponse.error.code)
        }
    }
}
