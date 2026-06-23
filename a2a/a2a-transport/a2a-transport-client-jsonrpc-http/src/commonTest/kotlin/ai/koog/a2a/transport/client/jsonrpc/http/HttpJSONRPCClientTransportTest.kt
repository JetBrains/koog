package ai.koog.a2a.transport.client.jsonrpc.http

import ai.koog.a2a.exceptions.A2AErrorCodes
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.ErrorData
import ai.koog.a2a.exceptions.ErrorInfo
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentInterface
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class HttpJSONRPCClientTransportTest {

    private val json = JSONRPCJson

    private suspend inline fun <reified TRequest, reified TResponse> testAPIMethod(
        method: A2AMethod,
        request: TRequest,
        expectedResponse: TResponse,
        noinline invoke: suspend ClientTransport.(TRequest) -> TResponse,
    ) {
        val mockEngine = MockEngine { receivedRequest ->
            assertEquals(HttpMethod.Post, receivedRequest.method)
            assertEquals(ContentType.Application.Json, receivedRequest.body.contentType)

            val requestBodyText = (receivedRequest.body as TextContent).text
            val jsonRpcRequest = json.decodeFromString<JSONRPCRequest>(requestBodyText)

            assertEquals(method.value, jsonRpcRequest.method)
            assertEquals(request, json.decodeFromJsonElement<TRequest>(jsonRpcRequest.params))

            val jsonRpcResponse = JSONRPCSuccessResponse(
                id = jsonRpcRequest.id,
                result = json.encodeToJsonElement<TResponse>(expectedResponse),
                jsonrpc = JSONRPC_VERSION,
            )

            respond(
                content = json.encodeToString(jsonRpcResponse),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val httpClient = HttpClient(mockEngine)
        val transport = HttpJSONRPCClientTransport("https://api.example.com/a2a", httpClient)

        val actualResponse = transport.invoke(request)

        assertEquals(expectedResponse, actualResponse)

        transport.close()
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

    @Ignore
    @Test
    fun testSendMessageStreaming() = runTest {
        // FIXME Can't test it, MockEngine doesn't support SSE capability
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

    @Ignore
    @Test
    fun testSubscribeToTask() = runTest {
        // FIXME Can't test it, MockEngine doesn't support SSE capability
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
        val testMessage = Message(
            messageId = Uuid.random().toString(),
            role = Role.ROLE_USER,
            parts = listOf(TextPart("Hello, agent!")),
            taskId = "invalid-task-id"
        )

        val request = SendMessageRequest(
            message = testMessage
        )

        val expectedDetails = listOf<ErrorData>(
            ErrorInfo(reason = "INVALID_PARAMETERS", metadata = mapOf("field" to "message"))
        )

        val mockEngine = MockEngine { receivedRequest ->
            assertEquals(HttpMethod.Post, receivedRequest.method)
            assertEquals(ContentType.Application.Json, receivedRequest.body.contentType)

            val requestBodyText = (receivedRequest.body as TextContent).text
            val jsonRpcRequest = json.decodeFromString<JSONRPCRequest>(requestBodyText)

            assertEquals(A2AMethod.SendMessage.value, jsonRpcRequest.method)
            assertEquals(request, json.decodeFromJsonElement<SendMessageRequest>(jsonRpcRequest.params))

            val jsonRpcErrorResponse = JSONRPCErrorResponse(
                id = jsonRpcRequest.id,
                error = JSONRPCError(
                    code = A2AErrorCodes.INVALID_PARAMS,
                    message = "Invalid method parameters",
                    data = json.encodeToJsonElement(expectedDetails)
                ),
                jsonrpc = JSONRPC_VERSION,
            )

            respond(
                content = json.encodeToString(jsonRpcErrorResponse),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val httpClient = HttpClient(mockEngine)
        val transport = HttpJSONRPCClientTransport("https://api.example.com/a2a", httpClient)

        try {
            transport.sendMessage(request)
            fail("Expected A2AInvalidParamsException to be thrown")
        } catch (e: A2AInvalidParamsException) {
            assertEquals("Invalid method parameters", e.message)
            assertEquals(A2AErrorCodes.INVALID_PARAMS, e.errorCode)
            assertEquals(expectedDetails, e.details)
        }

        transport.close()
    }
}
