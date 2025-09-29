package ai.koog.a2a.transport.server.jsonrpc.http

import ai.koog.a2a.exceptions.A2AErrorCodes
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.CommunicationEvent
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.PushNotificationConfig
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskPushNotificationConfigParams
import ai.koog.a2a.model.TaskQueryParams
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.model.UpdateEvent
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.RequestId
import ai.koog.a2a.transport.Response
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.jsonrpc.A2AMethod
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCJson
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
            url = "https://api.example.com/a2a",
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

        val communicationEvent = Message(
            messageId = "message-1",
            role = Role.Agent,
            parts = listOf(TextPart("Response message.")),
            taskId = "task-1"
        )

        val updateEvents = listOf(
            Message(
                messageId = "message-stream-1",
                role = Role.Agent,
                parts = listOf(TextPart("Streaming response part 1")),
                taskId = "task-1"
            ),
            Message(
                messageId = "message-stream-2",
                role = Role.Agent,
                parts = listOf(TextPart("Streaming response part 2")),
                taskId = "task-1"
            )
        )

        val taskGet = Task(
            id = "task-1",
            contextId = "test-context-1",
            status = TaskStatus(
                state = TaskState.Working
            )
        )

        val taskCancel = Task(
            id = "task-1",
            contextId = "test-context-1",
            status = TaskStatus(
                state = TaskState.Canceled
            )
        )

        val taskPushNotificationConfig = TaskPushNotificationConfig(
            taskId = "task-1",
            pushNotificationConfig = PushNotificationConfig(
                id = "notification-config-1",
                url = "https://webhook.example.com",
                token = "webhook-token-123"
            )
        )

        val taskPushNotificationConfigList = listOf(taskPushNotificationConfig)

        override suspend fun onGetAuthenticatedExtendedAgentCard(
            request: Request<Nothing?>,
            ctx: ServerCallContext
        ): Response<AgentCard> {
            return Response(
                id = request.id,
                data = agentCard
            )
        }

        override suspend fun onSendMessage(
            request: Request<MessageSendParams>,
            ctx: ServerCallContext
        ): Response<CommunicationEvent> {
            return Response(
                id = request.id,
                data = communicationEvent
            )
        }

        override fun onSendMessageStreaming(
            request: Request<MessageSendParams>,
            ctx: ServerCallContext
        ): Flow<Response<UpdateEvent>> {
            return updateEvents
                .asFlow()
                .map {
                    Response(
                        id = request.id,
                        data = it
                    )
                }
        }

        override suspend fun onGetTask(
            request: Request<TaskQueryParams>,
            ctx: ServerCallContext
        ): Response<Task> {
            return Response(
                id = request.id,
                data = taskGet
            )
        }

        override suspend fun onCancelTask(
            request: Request<TaskIdParams>,
            ctx: ServerCallContext
        ): Response<Task> {
            return Response(
                id = request.id,
                data = taskCancel
            )
        }

        override fun onResubscribeTask(
            request: Request<TaskIdParams>,
            ctx: ServerCallContext
        ): Flow<Response<UpdateEvent>> {
            return updateEvents
                .asFlow()
                .map {
                    Response(
                        id = request.id,
                        data = it
                    )
                }
        }

        override suspend fun onSetTaskPushNotificationConfig(
            request: Request<TaskPushNotificationConfig>,
            ctx: ServerCallContext
        ): Response<TaskPushNotificationConfig> {
            return Response(
                id = request.id,
                data = request.data
            )
        }

        override suspend fun onGetTaskPushNotificationConfig(
            request: Request<TaskPushNotificationConfigParams>,
            ctx: ServerCallContext
        ): Response<TaskPushNotificationConfig> {
            return Response(
                id = request.id,
                data = taskPushNotificationConfig
            )
        }

        override suspend fun onListTaskPushNotificationConfig(
            request: Request<TaskIdParams>,
            ctx: ServerCallContext
        ): Response<List<TaskPushNotificationConfig>> {
            return Response(
                id = request.id,
                data = taskPushNotificationConfigList
            )
        }

        override suspend fun onDeleteTaskPushNotificationConfig(
            request: Request<TaskPushNotificationConfigParams>,
            ctx: ServerCallContext
        ): Response<Nothing?> {
            return Response(
                id = request.id,
                data = null
            )
        }
    }

    private val json = JSONRPCJson

    private inline fun <reified TRequest, reified TResponse> testServerMethod(
        method: A2AMethod,
        request: Request<TRequest>,
        expectedResponse: Response<TResponse>,
    ) {
        testApplication {
            install(SSE)

            val transport = HttpJSONRPCServerTransport(MockRequestHandler)

            routing {
                transport.transportRoutes(this, "/a2a")
            }

            val jsonRpcRequest = JSONRPCRequest(
                id = request.id,
                method = method.value,
                params = json.encodeToJsonElement(request.data)
            )

            val response = client.post("/a2a") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(jsonRpcRequest))
            }

            assertEquals(HttpStatusCode.OK, response.status)

            val jsonRpcResponse = json.decodeFromString<JSONRPCSuccessResponse>(response.bodyAsText())
            val actualResponse = Response(
                id = jsonRpcResponse.id,
                data = json.decodeFromJsonElement<TResponse>(jsonRpcResponse.result)
            )

            assertEquals(expectedResponse.id, actualResponse.id)
            assertEquals(expectedResponse.data, actualResponse.data)
        }
    }

    private inline fun <reified TRequest, reified TResponse> testServerMethodStreaming(
        method: A2AMethod,
        request: Request<TRequest>,
        expectedResponses: List<Response<TResponse>>,
    ) {
        testApplication {
            install(SSE)

            val client = createClient {
                install(SSEClient)
            }

            val transport = HttpJSONRPCServerTransport(MockRequestHandler)

            routing {
                transport.transportRoutes(this, "/a2a")
            }

            val jsonRpcRequest = JSONRPCRequest(
                id = request.id,
                method = method.value,
                params = json.encodeToJsonElement(request.data)
            )

            val jsonrpcResponses = buildList {
                client.sse(
                    urlString = "/a2a",
                    request = {
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

            val actualResponses = jsonrpcResponses.map {
                Response(
                    id = it.id,
                    data = json.decodeFromJsonElement<TResponse>(it.result)
                )
            }

            assertEquals(expectedResponses.map { it.id }, actualResponses.map { it.id })
            assertEquals(expectedResponses.map { it.data }, actualResponses.map { it.data })
        }
    }

    @Test
    fun testGetAuthenticatedExtendedAgentCard() = runTest {
        val requestId = RequestId.StringId("test-1")

        val request = Request(
            id = requestId,
            data = null,
        )

        val expectedResponse = Response(
            id = requestId,
            data = MockRequestHandler.agentCard,
        )

        testServerMethod(
            method = A2AMethod.GetAuthenticatedExtendedAgentCard,
            request = request,
            expectedResponse = expectedResponse,
        )
    }

    @Test
    fun testSendMessage() = runTest {
        val requestId = RequestId.StringId("test-2")

        val messageSendParams = MessageSendParams(
            message = Message(
                messageId = "msg-1",
                role = Role.User,
                parts = listOf(TextPart("Hello, agent!")),
                taskId = "task-1"
            )
        )

        val request = Request(
            id = requestId,
            data = messageSendParams,
        )

        val expectedResponse = Response(
            id = requestId,
            data = MockRequestHandler.communicationEvent,
        )

        testServerMethod(
            method = A2AMethod.SendMessage,
            request = request,
            expectedResponse = expectedResponse,
        )
    }

    @Test
    fun testSendMessageStreaming() = runTest {
        val requestId = RequestId.StringId("test-2")

        val messageSendParams = MessageSendParams(
            message = Message(
                messageId = "msg-1",
                role = Role.User,
                parts = listOf(TextPart("Hello, agent!")),
                taskId = "task-1"
            )
        )

        val request = Request(
            id = requestId,
            data = messageSendParams,
        )

        val expectedResponses = MockRequestHandler.updateEvents.map {
            Response(
                id = requestId,
                data = it,
            )
        }

        testServerMethodStreaming(
            method = A2AMethod.SendMessageStreaming,
            request = request,
            expectedResponses = expectedResponses,
        )
    }

    @Test
    fun testGetTask() = runTest {
        val requestId = RequestId.StringId("test-3")
        val taskQueryParams = TaskQueryParams(id = "task-1")

        val request = Request(
            id = requestId,
            data = taskQueryParams,
        )

        val expectedResponse = Response(
            id = requestId,
            data = MockRequestHandler.taskGet,
        )

        testServerMethod(
            method = A2AMethod.GetTask,
            request = request,
            expectedResponse = expectedResponse,
        )
    }

    @Test
    fun testCancelTask() = runTest {
        val requestId = RequestId.StringId("test-4")
        val taskIdParams = TaskIdParams(id = "task-1")

        val request = Request(
            id = requestId,
            data = taskIdParams,
        )

        val expectedResponse = Response(
            id = requestId,
            data = MockRequestHandler.taskCancel,
        )

        testServerMethod(
            method = A2AMethod.CancelTask,
            request = request,
            expectedResponse = expectedResponse,
        )
    }

    @Test
    fun testResubscribeTask() = runTest {
        val requestId = RequestId.StringId("test-7")
        val taskIdParams = TaskIdParams(id = "task-1")

        val request = Request(
            id = requestId,
            data = taskIdParams,
        )

        val expectedResponses = MockRequestHandler.updateEvents.map {
            Response(
                id = requestId,
                data = it,
            )
        }

        testServerMethodStreaming(
            method = A2AMethod.ResubscribeTask,
            request = request,
            expectedResponses = expectedResponses,
        )
    }

    @Test
    fun testSetTaskPushNotificationConfig() = runTest {
        val requestId = RequestId.StringId("test-5")

        val pushNotificationConfig = TaskPushNotificationConfig(
            taskId = "task-123",
            pushNotificationConfig = PushNotificationConfig(
                id = "notification-config-1",
                url = "https://webhook.example.com/notifications",
                token = "webhook-token-123"
            )
        )

        val request = Request(
            id = requestId,
            data = pushNotificationConfig,
        )

        val expectedResponse = Response(
            id = requestId,
            data = pushNotificationConfig,
        )

        testServerMethod(
            method = A2AMethod.SetTaskPushNotificationConfig,
            request = request,
            expectedResponse = expectedResponse,
        )
    }

    @Test
    fun testGetTaskPushNotificationConfig() = runTest {
        val requestId = RequestId.StringId("test-6")

        val configParams = TaskPushNotificationConfigParams(
            id = "task-123",
            pushNotificationConfigId = "notification-config-1"
        )

        val request = Request(
            id = requestId,
            data = configParams,
        )

        val expectedResponse = Response(
            id = requestId,
            data = MockRequestHandler.taskPushNotificationConfig,
        )

        testServerMethod(
            method = A2AMethod.GetTaskPushNotificationConfig,
            request = request,
            expectedResponse = expectedResponse,
        )
    }

    @Test
    fun testListTaskPushNotificationConfig() = runTest {
        val requestId = RequestId.StringId("test-7")
        val taskIdParams = TaskIdParams(id = "task-1")

        val request = Request(
            id = requestId,
            data = taskIdParams,
        )

        val expectedResponse = Response(
            id = requestId,
            data = MockRequestHandler.taskPushNotificationConfigList,
        )

        testServerMethod(
            method = A2AMethod.ListTaskPushNotificationConfig,
            request = request,
            expectedResponse = expectedResponse,
        )
    }

    @Test
    fun testDeleteTaskPushNotificationConfig() = runTest {
        val requestId = RequestId.StringId("test-8")

        val configParams = TaskPushNotificationConfigParams(
            id = "task-123",
            pushNotificationConfigId = "notification-config-1"
        )

        val request = Request(
            id = requestId,
            data = configParams,
        )

        val expectedResponse = Response(
            id = requestId,
            data = null,
        )

        testServerMethod(
            method = A2AMethod.DeleteTaskPushNotificationConfig,
            request = request,
            expectedResponse = expectedResponse,
        )
    }

    @Test
    fun testMethodNotFound() = runTest {
        testApplication {
            install(SSE)

            val transport = HttpJSONRPCServerTransport(MockRequestHandler)

            routing {
                transport.transportRoutes(this, "/a2a")
            }

            val requestId = RequestId.StringId("test-9")
            val jsonRpcRequest = JSONRPCRequest(
                id = requestId,
                method = "unknown.method",
                params = JsonNull
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
                transport.transportRoutes(this, "/a2a")
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
