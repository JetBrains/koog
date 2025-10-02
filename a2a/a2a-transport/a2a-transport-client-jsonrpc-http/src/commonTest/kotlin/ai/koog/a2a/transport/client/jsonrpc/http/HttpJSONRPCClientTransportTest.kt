package ai.koog.a2a.transport.client.jsonrpc.http

import ai.koog.a2a.exceptions.A2AErrorCodes
import ai.koog.a2a.exceptions.A2AInvalidParamsException
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
import ai.koog.a2a.transport.ClientTransport
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.RequestId
import ai.koog.a2a.transport.Response
import ai.koog.a2a.transport.jsonrpc.A2AMethod
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCError
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCJson
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPC_VERSION
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
        request: Request<TRequest>,
        expectedResponse: Response<TResponse>,
        noinline invoke: suspend ClientTransport.(Request<TRequest>) -> Response<TResponse>,
    ) {
        val mockEngine = MockEngine { receivedRequest ->
            assertEquals(HttpMethod.Post, receivedRequest.method)
            assertEquals(ContentType.Application.Json, receivedRequest.body.contentType)

            val requestBodyText = (receivedRequest.body as TextContent).text
            val jsonRpcRequest = json.decodeFromString<JSONRPCRequest>(requestBodyText)

            assertEquals(method.value, jsonRpcRequest.method)
            assertEquals(request.id, jsonRpcRequest.id)
            assertEquals(request.data, json.decodeFromJsonElement(jsonRpcRequest.params))

            val jsonRpcResponse = JSONRPCSuccessResponse(
                id = expectedResponse.id,
                result = json.encodeToJsonElement(expectedResponse.data),
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

        assertEquals(expectedResponse.id, actualResponse.id)
        assertEquals(expectedResponse.data, actualResponse.data)

        transport.close()
    }

    @Test
    fun testGetAuthenticatedExtendedAgentCard() = runTest {
        val id = RequestId.StringId("test-1")

        val request = Request(
            id = id,
            data = null,
        )

        val expectedResponse = Response(
            id = id,
            data = AgentCard(
                name = "Test Agent",
                description = "A test agent",
                url = "https://api.example.com/a2a",
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
        )

        testAPIMethod(
            method = A2AMethod.GetAuthenticatedExtendedAgentCard,
            request = request,
            expectedResponse = expectedResponse,
            invoke = { getAuthenticatedExtendedAgentCard(it) }
        )
    }

    @Test
    fun testSendMessage() = runTest {
        val id = RequestId.StringId("test-2")

        val testMessage = Message(
            messageId = Uuid.random().toString(),
            role = Role.User,
            parts = listOf(TextPart("Hello, agent!")),
            taskId = "task-123"
        )

        val messageSendParams = MessageSendParams(
            message = testMessage
        )

        val request = Request(
            id = id,
            data = messageSendParams,
        )

        val expectedResponse: Response<CommunicationEvent> = Response(
            id = id,
            data = Message(
                messageId = "msg-456",
                role = Role.Agent,
                parts = listOf(TextPart("Hello, user! How can I help you?")),
                taskId = "task-123"
            )
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
        val id = RequestId.StringId("test-3")

        val taskQueryParams = TaskQueryParams(
            id = "task-123",
            historyLength = 10
        )

        val request = Request(
            id = id,
            data = taskQueryParams,
        )

        val expectedResponse = Response(
            id = id,
            data = Task(
                id = "task-123",
                contextId = "context-456",
                status = TaskStatus(
                    state = TaskState.Working,
                    message = Message(
                        messageId = Uuid.random().toString(),
                        role = Role.Agent,
                        parts = listOf(TextPart("Working on your request..."))
                    )
                ),
                history = listOf(
                    Message(
                        messageId = Uuid.random().toString(),
                        role = Role.User,
                        parts = listOf(TextPart("Hello, agent!")),
                        taskId = "task-123"
                    )
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
    fun testCancelTask() = runTest {
        val id = RequestId.StringId("test-4")

        val taskIdParams = TaskIdParams(id = "task-123")

        val request = Request(
            id = id,
            data = taskIdParams,
        )

        val expectedResponse = Response(
            id = id,
            data = Task(
                id = "task-123",
                contextId = "context-456",
                status = TaskStatus(
                    state = TaskState.Canceled,
                    message = Message(
                        messageId = Uuid.random().toString(),
                        role = Role.Agent,
                        parts = listOf(TextPart("Task has been canceled."))
                    )
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
    fun testResubscribeTask() = runTest {
        // FIXME Can't test it, MockEngine doesn't support SSE capability
    }

    @Test
    fun testSetTaskPushNotificationConfig() = runTest {
        val id = RequestId.StringId("test-5")

        val pushNotificationConfig = TaskPushNotificationConfig(
            taskId = "task-123",
            pushNotificationConfig = PushNotificationConfig(
                id = "notification-config-1",
                url = "https://webhook.example.com/notifications",
                token = "webhook-token-123"
            )
        )

        val request = Request(
            id = id,
            data = pushNotificationConfig,
        )

        val expectedResponse = Response(
            id = id,
            data = pushNotificationConfig
        )

        testAPIMethod(
            method = A2AMethod.SetTaskPushNotificationConfig,
            request = request,
            expectedResponse = expectedResponse,
            invoke = { setTaskPushNotificationConfig(it) }
        )
    }

    @Test
    fun testGetTaskPushNotificationConfig() = runTest {
        val id = RequestId.StringId("test-6")

        val configParams = TaskPushNotificationConfigParams(
            id = "task-123",
            pushNotificationConfigId = "notification-config-1"
        )

        val request = Request(
            id = id,
            data = configParams,
        )

        val expectedResponse = Response(
            id = id,
            data = TaskPushNotificationConfig(
                taskId = "task-123",
                pushNotificationConfig = PushNotificationConfig(
                    id = "notification-config-1",
                    url = "https://webhook.example.com/notifications",
                    token = "webhook-token-123"
                )
            )
        )

        testAPIMethod(
            method = A2AMethod.GetTaskPushNotificationConfig,
            request = request,
            expectedResponse = expectedResponse,
            invoke = { getTaskPushNotificationConfig(it) }
        )
    }

    @Test
    fun testListTaskPushNotificationConfig() = runTest {
        val id = RequestId.StringId("test-7")

        val taskIdParams = TaskIdParams(id = "task-123")

        val request = Request(
            id = id,
            data = taskIdParams,
        )

        val expectedResponse = Response(
            id = id,
            data = listOf(
                TaskPushNotificationConfig(
                    taskId = "task-123",
                    pushNotificationConfig = PushNotificationConfig(
                        id = "notification-config-1",
                        url = "https://webhook.example.com/notifications",
                        token = "webhook-token-123"
                    )
                ),
                TaskPushNotificationConfig(
                    taskId = "task-123",
                    pushNotificationConfig = PushNotificationConfig(
                        id = "notification-config-2",
                        url = "https://webhook2.example.com/notifications",
                        token = "webhook-token-456"
                    )
                )
            )
        )

        testAPIMethod(
            method = A2AMethod.ListTaskPushNotificationConfig,
            request = request,
            expectedResponse = expectedResponse,
            invoke = { listTaskPushNotificationConfig(it) }
        )
    }

    @Test
    fun testDeleteTaskPushNotificationConfig() = runTest {
        val id = RequestId.StringId("test-8")

        val configParams = TaskPushNotificationConfigParams(
            id = "task-123",
            pushNotificationConfigId = "notification-config-1"
        )

        val request = Request(
            id = id,
            data = configParams,
        )

        val expectedResponse = Response(
            id = id,
            data = null
        )

        testAPIMethod(
            method = A2AMethod.DeleteTaskPushNotificationConfig,
            request = request,
            expectedResponse = expectedResponse,
            invoke = { deleteTaskPushNotificationConfig(it) }
        )
    }

    @Test
    fun testSendMessageError() = runTest {
        val id = RequestId.StringId("test-error-1")

        val testMessage = Message(
            messageId = Uuid.random().toString(),
            role = Role.User,
            parts = listOf(TextPart("Hello, agent!")),
            taskId = "invalid-task-id"
        )

        val messageSendParams = MessageSendParams(
            message = testMessage
        )

        val request = Request(
            id = id,
            data = messageSendParams,
        )

        val mockEngine = MockEngine { receivedRequest ->
            assertEquals(HttpMethod.Post, receivedRequest.method)
            assertEquals(ContentType.Application.Json, receivedRequest.body.contentType)

            val requestBodyText = (receivedRequest.body as TextContent).text
            val jsonRpcRequest = json.decodeFromString<JSONRPCRequest>(requestBodyText)

            assertEquals(A2AMethod.SendMessage.value, jsonRpcRequest.method)
            assertEquals(request.id, jsonRpcRequest.id)
            assertEquals(request.data, json.decodeFromJsonElement(jsonRpcRequest.params))

            val jsonRpcErrorResponse = JSONRPCErrorResponse(
                id = id,
                error = JSONRPCError(
                    code = A2AErrorCodes.INVALID_PARAMS,
                    message = "Invalid method parameters",
                    data = json.encodeToJsonElement("The message parameters are invalid")
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
            assertEquals(-32602, e.errorCode)
        }

        transport.close()
    }
}
