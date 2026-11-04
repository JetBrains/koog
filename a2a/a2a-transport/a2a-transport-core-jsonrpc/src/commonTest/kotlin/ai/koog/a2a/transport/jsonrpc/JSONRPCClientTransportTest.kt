package ai.koog.a2a.transport.jsonrpc

import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2AMethodNotFoundException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.ErrorInfo
import ai.koog.a2a.exceptions.GenericErrorData
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
import ai.koog.a2a.transport.A2AMethod
import ai.koog.a2a.transport.ClientCallContext
import ai.koog.a2a.transport.ClientTransport
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPC_VERSION
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JSONRPCClientTransportTest {
    /**
     * Records the last [JSONRPCRequest] and answers it with a success response carrying [result]
     * (one response per entry of [streamResults] for streaming requests).
     */
    private class TestJSONRPCClientTransport(
        private val result: JsonElement? = null,
        private val streamResults: List<JsonElement> = emptyList(),
    ) : JSONRPCClientTransport() {
        var received: JSONRPCRequest? = null

        private fun success(request: JSONRPCRequest, result: JsonElement?) =
            JSONRPCSuccessResponse(id = request.id, result = checkNotNull(result), jsonrpc = JSONRPC_VERSION)

        override suspend fun request(request: JSONRPCRequest, ctx: ClientCallContext): JSONRPCResponse {
            received = request
            return success(request, result)
        }

        override fun requestStreaming(request: JSONRPCRequest, ctx: ClientCallContext): Flow<JSONRPCResponse> {
            received = request
            return streamResults.map { success(request, it) }.asFlow()
        }

        override fun close() = Unit

        fun decodeResponse(responseJson: String): Task =
            toResponse(JSONRPCJson.decodeFromString<JSONRPCResponse>(responseJson), Task.serializer())
    }

    private val transport = TestJSONRPCClientTransport()

    private inline fun <reified T : A2AException> assertA2AError(responseJson: String): T =
        assertFailsWith<T> { transport.decodeResponse(responseJson) }

    @Test
    fun testErrorWithoutData() {
        //language=JSON
        val responseJson = """
            {"jsonrpc": "2.0", "id": "1", "error": {"code": -32601, "message": "Method not found"}}
        """.trimIndent()

        val exception = assertA2AError<A2AMethodNotFoundException>(responseJson)

        assertEquals("Method not found", exception.message)
        assertEquals(emptyList(), exception.details)
    }

    @Test
    fun testErrorWithStringData() {
        //language=JSON
        val responseJson = """
            {"jsonrpc": "2.0", "id": "1", "error": {"code": -32602, "message": "Invalid params", "data": "Field required"}}
        """.trimIndent()

        val exception = assertA2AError<A2AInvalidParamsException>(responseJson)

        assertEquals("Invalid params", exception.message)
        assertEquals(emptyList(), exception.details)
    }

    @Test
    fun testErrorWithSingleObjectData() {
        //language=JSON
        val responseJson = """
            {
              "jsonrpc": "2.0",
              "id": "1",
              "error": {
                "code": -32001,
                "message": "Task not found",
                "data": {
                  "@type": "type.googleapis.com/google.rpc.ErrorInfo",
                  "reason": "TASK_NOT_FOUND",
                  "domain": "a2a-protocol.org"
                }
              }
            }
        """.trimIndent()

        val exception = assertA2AError<A2ATaskNotFoundException>(responseJson)

        assertEquals(listOf(ErrorInfo(reason = "TASK_NOT_FOUND")), exception.details)
    }

    @Test
    fun testErrorWithArrayDataSkipsInvalidEntries() {
        //language=JSON
        val responseJson = """
            {
              "jsonrpc": "2.0",
              "id": "1",
              "error": {
                "code": -32001,
                "message": "Task not found",
                "data": [
                  {
                    "@type": "type.googleapis.com/google.rpc.ErrorInfo",
                    "reason": "TASK_NOT_FOUND",
                    "domain": "a2a-protocol.org",
                    "metadata": {"taskId": "task-1"}
                  },
                  "not an object",
                  {"reason": "NO_TYPE"},
                  {"@type": 42},
                  {"@type": "type.example.com/Custom", "value": 1}
                ]
              }
            }
        """.trimIndent()

        val exception = assertA2AError<A2ATaskNotFoundException>(responseJson)

        val expectedDetails = listOf(
            ErrorInfo(reason = "TASK_NOT_FOUND", metadata = mapOf("taskId" to "task-1")),
            GenericErrorData(
                raw = JSONRPCJson.parseToJsonElement("""{"@type": "type.example.com/Custom", "value": 1}""").jsonObject,
                type = "type.example.com/Custom",
            ),
        )
        assertEquals(expectedDetails, exception.details)
    }

    @Test
    fun testErrorWithUndecodableKnownTypeKeepsRawEntry() {
        //language=JSON
        val responseJson = """
            {
              "jsonrpc": "2.0",
              "id": "1",
              "error": {
                "code": -32001,
                "message": "Task not found",
                "data": [
                  {
                    "@type": "type.googleapis.com/google.rpc.ErrorInfo",
                    "reason": "TASK_NOT_FOUND",
                    "domain": "a2a-protocol.org",
                    "metadata": {"attempt": 1}
                  }
                ]
              }
            }
        """.trimIndent()

        val exception = assertA2AError<A2ATaskNotFoundException>(responseJson)

        val rawEntry = JSONRPCJson.parseToJsonElement(responseJson)
            .jsonObject.getValue("error")
            .jsonObject.getValue("data")
            .jsonArray.single() as JsonObject
        assertEquals(listOf(GenericErrorData(raw = rawEntry, type = ErrorInfo.TYPE)), exception.details)
    }

    @Test
    fun testSnakeCaseResultIsDecoded() {
        //language=JSON
        val responseJson = """
            {
              "jsonrpc": "2.0",
              "id": "1",
              "result": {
                "id": "task-1",
                "context_id": "ctx-1",
                "status": {"state": "TASK_STATE_COMPLETED"}
              }
            }
        """.trimIndent()

        val expected = Task(
            id = "task-1",
            contextId = "ctx-1",
            status = TaskStatus(state = TaskState.TASK_STATE_COMPLETED),
        )
        assertEquals(expected, transport.decodeResponse(responseJson))
    }

    private val json = JSONRPCJson

    private fun messageResponse(id: String, text: String) = Message(
        messageId = id,
        role = Role.ROLE_AGENT,
        parts = listOf(TextPart(text)),
        taskId = "task-123"
    )

    private inline fun <reified TRequest, reified TResponse> testAPIMethod(
        method: A2AMethod,
        request: TRequest,
        expectedResponse: TResponse,
        noinline invoke: suspend ClientTransport.(TRequest) -> TResponse,
    ) = runTest {
        val transport = TestJSONRPCClientTransport(result = json.encodeToJsonElement<TResponse>(expectedResponse))

        val actualResponse = transport.invoke(request)

        assertEquals(method.value, transport.received?.method)
        assertEquals(request, json.decodeFromJsonElement<TRequest>(transport.received!!.params))
        assertEquals(expectedResponse, actualResponse)
    }

    private inline fun <reified TRequest> testAPIMethodStreaming(
        method: A2AMethod,
        request: TRequest,
        expectedEvents: List<Event>,
        noinline invoke: ClientTransport.(TRequest) -> Flow<Event>,
    ) = runTest {
        val transport = TestJSONRPCClientTransport(
            streamResults = expectedEvents.map { json.encodeToJsonElement<Event>(it) }
        )

        val actualEvents = transport.invoke(request).toList()

        assertEquals(method.value, transport.received?.method)
        assertEquals(request, json.decodeFromJsonElement<TRequest>(transport.received!!.params))
        assertEquals(expectedEvents, actualEvents)
    }

    @Test
    fun testGetExtendedAgentCard() = testAPIMethod(
        method = A2AMethod.GetExtendedAgentCard,
        request = GetExtendedAgentCardRequest(),
        expectedResponse = AgentCard(
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
            skills = listOf(
                AgentSkill(
                    id = "test-skill",
                    name = "Test Skill",
                    description = "A test skill",
                    tags = listOf("test")
                )
            )
        ),
        invoke = { getExtendedAgentCard(it) }
    )

    private val sendMessageRequest = SendMessageRequest(
        message = Message(
            messageId = "msg-1",
            role = Role.ROLE_USER,
            parts = listOf(TextPart("Hello, agent!")),
            taskId = "task-123"
        )
    )

    @Test
    fun testSendMessage() = testAPIMethod<SendMessageRequest, ResponseEvent>(
        method = A2AMethod.SendMessage,
        request = sendMessageRequest,
        expectedResponse = messageResponse("msg-456", "Hello, user! How can I help you?"),
        invoke = { sendMessage(it) }
    )

    @Test
    fun testSendMessageStreaming() = testAPIMethodStreaming(
        method = A2AMethod.SendMessageStreaming,
        request = sendMessageRequest,
        expectedEvents = listOf(
            messageResponse("msg-stream-1", "Streaming response part 1"),
            messageResponse("msg-stream-2", "Streaming response part 2"),
        ),
        invoke = { sendMessageStreaming(it) }
    )

    @Test
    fun testGetTask() = testAPIMethod(
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
            history = listOf(
                Message(
                    messageId = "history-message",
                    role = Role.ROLE_USER,
                    parts = listOf(TextPart("Hello, agent!")),
                    taskId = "task-123"
                )
            )
        ),
        invoke = { getTask(it) }
    )

    @Test
    fun testListTasks() = testAPIMethod(
        method = A2AMethod.ListTasks,
        request = ListTasksRequest(
            contextId = "context-456",
            status = TaskState.TASK_STATE_WORKING,
            pageSize = 10,
        ),
        expectedResponse = ListTasksResponse(
            tasks = listOf(
                Task(
                    id = "task-123",
                    contextId = "context-456",
                    status = TaskStatus(state = TaskState.TASK_STATE_WORKING)
                ),
                Task(
                    id = "task-789",
                    contextId = "context-456",
                    status = TaskStatus(state = TaskState.TASK_STATE_WORKING)
                )
            ),
            nextPageToken = "next-page-token",
            pageSize = 10,
            totalSize = 2,
        ),
        invoke = { listTasks(it) }
    )

    @Test
    fun testCancelTask() = testAPIMethod(
        method = A2AMethod.CancelTask,
        request = CancelTaskRequest(id = "task-123"),
        expectedResponse = Task(
            id = "task-123",
            contextId = "context-456",
            status = TaskStatus(state = TaskState.TASK_STATE_CANCELED)
        ),
        invoke = { cancelTask(it) }
    )

    @Test
    fun testSubscribeToTask() = testAPIMethodStreaming(
        method = A2AMethod.SubscribeToTask,
        request = SubscribeToTaskRequest(id = "task-123"),
        expectedEvents = listOf(
            Task(
                id = "task-123",
                contextId = "context-456",
                status = TaskStatus(state = TaskState.TASK_STATE_WORKING)
            ),
            messageResponse("msg-stream-1", "Still working..."),
        ),
        invoke = { subscribeToTask(it) }
    )

    private val pushNotificationConfig = TaskPushNotificationConfig(
        taskId = "task-123",
        id = "notification-config-1",
        url = "https://webhook.example.com/notifications",
        token = "webhook-token-123"
    )

    @Test
    fun testCreateTaskPushNotificationConfig() = testAPIMethod(
        method = A2AMethod.CreateTaskPushNotificationConfig,
        request = pushNotificationConfig,
        expectedResponse = pushNotificationConfig,
        invoke = { createTaskPushNotificationConfig(it) }
    )

    @Test
    fun testGetTaskPushNotificationConfig() = testAPIMethod(
        method = A2AMethod.GetTaskPushNotificationConfig,
        request = GetTaskPushNotificationConfigRequest(taskId = "task-123", id = "notification-config-1"),
        expectedResponse = pushNotificationConfig,
        invoke = { getTaskPushNotificationConfig(it) }
    )

    @Test
    fun testListTaskPushNotificationConfigs() = testAPIMethod(
        method = A2AMethod.ListTaskPushNotificationConfigs,
        request = ListTaskPushNotificationConfigsRequest(taskId = "task-123"),
        expectedResponse = ListTaskPushNotificationConfigsResponse(
            configs = listOf(
                pushNotificationConfig,
                TaskPushNotificationConfig(
                    taskId = "task-123",
                    id = "notification-config-2",
                    url = "https://webhook2.example.com/notifications",
                    token = "webhook-token-456"
                )
            ),
            nextPageToken = "",
        ),
        invoke = { listTaskPushNotificationConfigs(it) }
    )

    @Test
    fun testDeleteTaskPushNotificationConfig() = testAPIMethod(
        method = A2AMethod.DeleteTaskPushNotificationConfig,
        request = DeleteTaskPushNotificationConfigRequest(taskId = "task-123", id = "notification-config-1"),
        expectedResponse = Unit,
        invoke = { deleteTaskPushNotificationConfig(it) }
    )
}
