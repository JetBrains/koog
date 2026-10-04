package ai.koog.a2a.transport.client.jsonrpc.http

import ai.koog.a2a.exceptions.A2AErrorCodes
import ai.koog.a2a.exceptions.A2AErrorReasons
import ai.koog.a2a.exceptions.A2AInvalidParamsException
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
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.transport.ClientTransport
import ai.koog.a2a.transport.jsonrpc.A2AMethod
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
    fun testGetExtendedAgentCard() = runTest {
        val request = GetExtendedAgentCardRequest()

        val expectedResponse = AgentCard(
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
            skills = listOf(
                AgentSkill(
                    id = "test-skill",
                    name = "Test Skill",
                    description = "A test skill",
                    tags = listOf("test")
                )
            )
        )

        testAPIMethod(
            method = A2AMethod.GetAuthenticatedExtendedAgentCard,
            request = request,
            expectedResponse = expectedResponse,
            invoke = { getExtendedAgentCard(it) }
        )
    }

    @Test
    fun testSendMessage() = runTest {
        val testMessage = Message(
            messageId = Uuid.random().toString(),
            role = Role.ROLE_USER,
            parts = listOf(TextPart("Hello, agent!")),
            taskId = "task-123"
        )

        val request = SendMessageRequest(
            message = testMessage
        )

        val expectedResponse: ResponseEvent = Message(
            messageId = "msg-456",
            role = Role.ROLE_AGENT,
            parts = listOf(TextPart("Hello, user! How can I help you?")),
            taskId = "task-123"
        )

        testAPIMethod(
            method = A2AMethod.SendMessage,
            request = request,
            expectedResponse = expectedResponse,
            invoke = { sendMessage(it) }
        )
    }

    @Test
    fun testSendMessageStreaming() = runTest {
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
    fun testGetTask() = runTest {
        val request = GetTaskRequest(
            id = "task-123",
            historyLength = 10
        )

        val expectedResponse = Task(
            id = "task-123",
            contextId = "context-456",
            status = TaskStatus(
                state = TaskState.TASK_STATE_WORKING,
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.ROLE_AGENT,
                    parts = listOf(TextPart("Working on your request..."))
                )
            ),
            history = listOf(
                Message(
                    messageId = Uuid.random().toString(),
                    role = Role.ROLE_USER,
                    parts = listOf(TextPart("Hello, agent!")),
                    taskId = "task-123"
                )
            )
        )

        testAPIMethod(
            method = A2AMethod.GetTask,
            request = request,
            expectedResponse = expectedResponse,
            invoke = { getTask(it) }
        )
    }

    @Test
    fun testListTasks() = runTest {
        val request = ListTasksRequest(
            contextId = "context-456",
            status = TaskState.TASK_STATE_WORKING,
            pageSize = 10,
        )

        val expectedResponse = ListTasksResponse(
            tasks = listOf(
                Task(
                    id = "task-123",
                    contextId = "context-456",
                    status = TaskStatus(
                        state = TaskState.TASK_STATE_WORKING,
                        message = Message(
                            messageId = Uuid.random().toString(),
                            role = Role.ROLE_AGENT,
                            parts = listOf(TextPart("Working on your request..."))
                        )
                    )
                ),
                Task(
                    id = "task-789",
                    contextId = "context-456",
                    status = TaskStatus(
                        state = TaskState.TASK_STATE_WORKING,
                        message = Message(
                            messageId = Uuid.random().toString(),
                            role = Role.ROLE_AGENT,
                            parts = listOf(TextPart("Still working..."))
                        )
                    )
                )
            ),
            nextPageToken = "next-page-token",
            pageSize = 10,
            totalSize = 2,
        )

        testAPIMethod(
            method = A2AMethod.ListTasks,
            request = request,
            expectedResponse = expectedResponse,
            invoke = { listTasks(it) }
        )
    }

    @Test
    fun testCancelTask() = runTest {
        val request = CancelTaskRequest(id = "task-123")

        val expectedResponse = Task(
            id = "task-123",
            contextId = "context-456",
            status = TaskStatus(
                state = TaskState.TASK_STATE_CANCELED,
                message = Message(
                    messageId = Uuid.random().toString(),
                    role = Role.ROLE_AGENT,
                    parts = listOf(TextPart("Task has been canceled."))
                )
            )
        )

        testAPIMethod(
            method = A2AMethod.CancelTask,
            request = request,
            expectedResponse = expectedResponse,
            invoke = { cancelTask(it) }
        )
    }

    @Test
    fun testSubscribeToTask() = runTest {
        val expectedEvents = listOf<Event>(
            Task(
                id = "task-123",
                contextId = "context-456",
                status = TaskStatus(state = TaskState.TASK_STATE_WORKING)
            ),
            Message(
                messageId = "msg-stream-1",
                role = Role.ROLE_AGENT,
                parts = listOf(TextPart("Still working...")),
                taskId = "task-123"
            )
        )

        testAPIMethodStreaming(
            method = A2AMethod.SubscribeToTask,
            request = SubscribeToTaskRequest(id = "task-123"),
            expectedEvents = expectedEvents,
            invoke = { subscribeToTask(it) }
        )
    }

    @Test
    fun testCreateTaskPushNotificationConfig() = runTest {
        val request = TaskPushNotificationConfig(
            taskId = "task-123",
            id = "notification-config-1",
            url = "https://webhook.example.com/notifications",
            token = "webhook-token-123"
        )

        testAPIMethod(
            method = A2AMethod.CreateTaskPushNotificationConfig,
            request = request,
            expectedResponse = request,
            invoke = { createTaskPushNotificationConfig(it) }
        )
    }

    @Test
    fun testGetTaskPushNotificationConfig() = runTest {
        val request = GetTaskPushNotificationConfigRequest(
            taskId = "task-123",
            id = "notification-config-1"
        )

        val expectedResponse = TaskPushNotificationConfig(
            taskId = "task-123",
            id = "notification-config-1",
            url = "https://webhook.example.com/notifications",
            token = "webhook-token-123"
        )

        testAPIMethod(
            method = A2AMethod.GetTaskPushNotificationConfig,
            request = request,
            expectedResponse = expectedResponse,
            invoke = { getTaskPushNotificationConfig(it) }
        )
    }

    @Test
    fun testListTaskPushNotificationConfigs() = runTest {
        val request = ListTaskPushNotificationConfigsRequest(taskId = "task-123")

        val expectedResponse = ListTaskPushNotificationConfigsResponse(
            configs = listOf(
                TaskPushNotificationConfig(
                    taskId = "task-123",
                    id = "notification-config-1",
                    url = "https://webhook.example.com/notifications",
                    token = "webhook-token-123"
                ),
                TaskPushNotificationConfig(
                    taskId = "task-123",
                    id = "notification-config-2",
                    url = "https://webhook2.example.com/notifications",
                    token = "webhook-token-456"
                )
            ),
            nextPageToken = "",
        )

        testAPIMethod(
            method = A2AMethod.ListTaskPushNotificationConfig,
            request = request,
            expectedResponse = expectedResponse,
            invoke = { listTaskPushNotificationConfigs(it) }
        )
    }

    @Test
    fun testDeleteTaskPushNotificationConfig() = runTest {
        val request = DeleteTaskPushNotificationConfigRequest(
            taskId = "task-123",
            id = "notification-config-1"
        )

        testAPIMethod(
            method = A2AMethod.DeleteTaskPushNotificationConfig,
            request = request,
            expectedResponse = Unit,
            invoke = { deleteTaskPushNotificationConfig(it) }
        )
    }

    @Test
    fun testSendMessageError() = runTest {
        val request = SendMessageRequest(
            message = Message(
                messageId = Uuid.random().toString(),
                role = Role.ROLE_USER,
                parts = listOf(TextPart("Hello, agent!")),
                taskId = "invalid-task-id"
            )
        )

        val expectedDetails = listOf<ErrorData>(
            ErrorInfo(reason = "INVALID_PARAMETERS", metadata = mapOf("field" to "message"))
        )

        testApplication {
            val transport = createTransport { jsonRpcRequest ->
                assertEquals(A2AMethod.SendMessage.value, jsonRpcRequest.method)
                respondJsonRpcError(jsonRpcRequest, A2AErrorCodes.INVALID_PARAMS, "Invalid method parameters", expectedDetails)
            }

            val e = assertFailsWith<A2AInvalidParamsException> {
                transport.sendMessage(request)
            }

            assertEquals("Invalid method parameters", e.message)
            assertEquals(A2AErrorCodes.INVALID_PARAMS, e.errorCode)
            assertEquals(expectedDetails, e.details)

            transport.close()
        }
    }

    @Test
    fun testSendMessageStreamingPlainJsonError() = runTest {
        val request = SendMessageRequest(
            message = Message(
                messageId = "msg-1",
                role = Role.ROLE_USER,
                parts = listOf(TextPart("Hello, agent!")),
            )
        )

        val expectedDetails = listOf<ErrorData>(
            ErrorInfo(reason = "INVALID_PARAMETERS", metadata = mapOf("field" to "message"))
        )

        testApplication {
            val transport = createTransport { jsonRpcRequest ->
                assertEquals(A2AMethod.SendMessageStreaming.value, jsonRpcRequest.method)
                respondJsonRpcError(jsonRpcRequest, A2AErrorCodes.INVALID_PARAMS, "Invalid method parameters", expectedDetails)
            }

            val e = assertFailsWith<A2AInvalidParamsException> {
                transport.sendMessageStreaming(request).toList()
            }

            assertEquals("Invalid method parameters", e.message)
            assertEquals(expectedDetails, e.details)

            transport.close()
        }
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
}
