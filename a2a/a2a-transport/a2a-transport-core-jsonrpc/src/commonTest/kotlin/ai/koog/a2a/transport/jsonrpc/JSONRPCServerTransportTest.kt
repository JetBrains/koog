package ai.koog.a2a.transport.jsonrpc

import ai.koog.a2a.consts.A2AHeaders
import ai.koog.a2a.exceptions.A2AErrorCodes
import ai.koog.a2a.exceptions.A2AErrorReasons
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
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
import ai.koog.a2a.model.ListTasksRequest
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.transport.A2AMethod
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.StateKey
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCError
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPC_VERSION
import ai.koog.a2a.transport.jsonrpc.model.RequestId
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JSONRPCServerTransportTest {
    private val json = JSONRPCJson

    private val requestId = RequestId.StringId("test-1")

    private val v1Ctx = ServerCallContext(mapOf(A2AHeaders.A2A_VERSION to listOf("1.0")))

    private val getTaskParams = json.encodeToJsonElement(GetTaskRequest(id = "task-1"))
    private val subscribeParams = json.encodeToJsonElement(SubscribeToTaskRequest(id = "task-1"))

    /** What the transport passed to `respond` and, if the response was streamed, the collected stream. */
    private class Outcome(val responses: List<JSONRPCResponse>, val streamed: List<JSONRPCResponse>?)

    private fun rawRequest(method: String, params: JsonElement = JsonNull, id: RequestId = requestId): String =
        json.encodeToString(JSONRPCRequest(id = id, method = method, params = params, jsonrpc = JSONRPC_VERSION))

    private suspend fun JSONRPCServerTransport.handle(
        requestRaw: String,
        ctx: ServerCallContext = v1Ctx,
        customizeCallContext: suspend (ServerCallContext) -> ServerCallContext = { it },
    ): Outcome {
        val responses = mutableListOf<JSONRPCResponse>()
        var streamed: List<JSONRPCResponse>? = null

        handleRequest(
            requestRaw = requestRaw,
            ctx = ctx,
            respond = { responses += it },
            respondStreaming = { streamed = it.toList() },
            customizeCallContext = customizeCallContext,
        )

        return Outcome(responses, streamed)
    }

    private suspend fun handle(
        method: A2AMethod,
        params: JsonElement = JsonNull,
        ctx: ServerCallContext = v1Ctx,
        handler: RequestHandler = MockRequestHandler,
        customizeCallContext: suspend (ServerCallContext) -> ServerCallContext = { it },
    ): Outcome {
        val transport = JSONRPCServerTransport(handler)
        return transport.handle(rawRequest(method.value, params), ctx, customizeCallContext)
    }

    private fun Outcome.singleError(): JSONRPCErrorResponse {
        assertNull(streamed)
        return assertIs<JSONRPCErrorResponse>(responses.single())
    }

    private fun Outcome.singleTask(): Task {
        assertNull(streamed)
        val response = assertIs<JSONRPCSuccessResponse>(responses.single())
        return json.decodeFromJsonElement<Task>(response.result)
    }

    private fun errorResponse(
        id: RequestId?,
        code: Int,
        message: String,
        reason: String,
    ) = JSONRPCErrorResponse(
        id = id,
        error = JSONRPCError(
            code = code,
            message = message,
            data = json.encodeToJsonElement<List<ErrorData>>(listOf(ErrorInfo(reason = reason))),
        ),
        jsonrpc = JSONRPC_VERSION,
    )

    private fun versionNotSupportedError(requested: String, supported: String = "1.0") = errorResponse(
        id = requestId,
        code = A2AErrorCodes.VERSION_NOT_SUPPORTED,
        message = "A2A version '$requested' is not supported; supported: $supported",
        reason = A2AErrorReasons.VERSION_NOT_SUPPORTED,
    )

    private fun handlerRecording(onCall: (ServerCallContext) -> Unit) = object : RequestHandler by MockRequestHandler {
        override suspend fun onGetTask(request: GetTaskRequest, ctx: ServerCallContext): Task {
            onCall(ctx)
            return MockRequestHandler.taskGet
        }
    }

    private suspend inline fun <reified TRequest, reified TResponse> testServerMethod(
        method: A2AMethod,
        id: String,
        request: TRequest,
        expectedResponse: TResponse,
    ) {
        val requestId = RequestId.StringId(id)
        val outcome = JSONRPCServerTransport(MockRequestHandler)
            .handle(rawRequest(method.value, json.encodeToJsonElement(request), requestId))

        assertNull(outcome.streamed)

        val response = assertIs<JSONRPCSuccessResponse>(outcome.responses.single())
        assertEquals(requestId, response.id)
        assertEquals(expectedResponse, json.decodeFromJsonElement<TResponse>(response.result))
    }

    private suspend inline fun <reified TRequest, reified TResponse> testServerMethodStreaming(
        method: A2AMethod,
        id: String,
        request: TRequest,
        expectedResponses: List<TResponse>,
    ) {
        val requestId = RequestId.StringId(id)
        val outcome = JSONRPCServerTransport(MockRequestHandler)
            .handle(rawRequest(method.value, json.encodeToJsonElement(request), requestId))

        assertEquals(emptyList(), outcome.responses)

        val streamed = assertNotNull(outcome.streamed).map { assertIs<JSONRPCSuccessResponse>(it) }
        assertEquals(List(expectedResponses.size) { requestId }, streamed.map { it.id })
        assertEquals(expectedResponses, streamed.map { json.decodeFromJsonElement<TResponse>(it.result) })
    }

    @Test
    fun testGetExtendedAgentCard() = runTest {
        testServerMethod(
            method = A2AMethod.GetExtendedAgentCard,
            id = "test-1",
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
            id = "test-2",
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
            id = "test-2",
            request = request,
            expectedResponses = MockRequestHandler.updateEvents,
        )
    }

    @Test
    fun testGetTask() = runTest {
        testServerMethod(
            method = A2AMethod.GetTask,
            id = "test-3",
            request = GetTaskRequest(id = "task-1"),
            expectedResponse = MockRequestHandler.taskGet,
        )
    }

    @Test
    fun testListTasks() = runTest {
        testServerMethod(
            method = A2AMethod.ListTasks,
            id = "test-list-tasks",
            request = ListTasksRequest(contextId = "test-context-1", status = TaskState.TASK_STATE_WORKING),
            expectedResponse = MockRequestHandler.listTasksResponse,
        )
    }

    @Test
    fun testCancelTask() = runTest {
        testServerMethod(
            method = A2AMethod.CancelTask,
            id = "test-4",
            request = CancelTaskRequest(id = "task-1"),
            expectedResponse = MockRequestHandler.taskCancel,
        )
    }

    @Test
    fun testSubscribeToTask() = runTest {
        testServerMethodStreaming(
            method = A2AMethod.SubscribeToTask,
            id = "test-7",
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
            id = "test-5",
            request = config,
            expectedResponse = config,
        )
    }

    @Test
    fun testGetTaskPushNotificationConfig() = runTest {
        testServerMethod(
            method = A2AMethod.GetTaskPushNotificationConfig,
            id = "test-6",
            request = GetTaskPushNotificationConfigRequest(taskId = "task-1", id = "notification-config-1"),
            expectedResponse = MockRequestHandler.taskPushNotificationConfig,
        )
    }

    @Test
    fun testListTaskPushNotificationConfigs() = runTest {
        testServerMethod(
            method = A2AMethod.ListTaskPushNotificationConfigs,
            id = "test-7",
            request = ListTaskPushNotificationConfigsRequest(taskId = "task-1"),
            expectedResponse = MockRequestHandler.listConfigsResponse,
        )
    }

    @Test
    fun testDeleteTaskPushNotificationConfig() = runTest {
        testServerMethod<DeleteTaskPushNotificationConfigRequest, Unit>(
            method = A2AMethod.DeleteTaskPushNotificationConfig,
            id = "test-8",
            request = DeleteTaskPushNotificationConfigRequest(taskId = "task-1", id = "notification-config-1"),
            expectedResponse = Unit,
        )
    }

    @Test
    fun testMethodNotFound() = runTest {
        val outcome = JSONRPCServerTransport(MockRequestHandler).handle(rawRequest("unknown.method"))

        val error = outcome.singleError()
        assertEquals(requestId, error.id)
        assertEquals(A2AErrorCodes.METHOD_NOT_FOUND, error.error.code)
    }

    @Test
    fun testInvalidJsonRequest() = runTest {
        val outcome = JSONRPCServerTransport(MockRequestHandler).handle("invalid json")

        val error = outcome.singleError()
        // JSON-RPC 2.0 requires an explicit null id when the request id cannot be determined
        assertEquals(JsonNull, json.encodeToJsonElement(error).jsonObject["id"])
        assertNull(error.id)
        assertEquals(A2AErrorCodes.PARSE_ERROR, error.error.code)
    }

    @Test
    fun testRequestWithoutParams() = runTest {
        val outcome = JSONRPCServerTransport(MockRequestHandler).handle(
            //language=JSON
            """{"jsonrpc":"2.0","id":"test-no-params","method":"${A2AMethod.GetExtendedAgentCard.value}"}"""
        )

        val response = assertIs<JSONRPCSuccessResponse>(outcome.responses.single())
        assertEquals(RequestId.StringId("test-no-params"), response.id)
        assertEquals(MockRequestHandler.agentCard, json.decodeFromJsonElement<AgentCard>(response.result))
    }

    @Test
    fun testA2AErrorIncludesErrorInfo() = runTest {
        val handler = object : RequestHandler by MockRequestHandler {
            override suspend fun onGetTask(request: GetTaskRequest, ctx: ServerCallContext): Task =
                throw A2ATaskNotFoundException("Task not found: ${request.id}")
        }

        val outcome = handle(A2AMethod.GetTask, getTaskParams, handler = handler)

        assertEquals(
            errorResponse(requestId, A2AErrorCodes.TASK_NOT_FOUND, "Task not found: task-1", A2AErrorReasons.TASK_NOT_FOUND),
            outcome.singleError()
        )
    }

    @Test
    fun testStreamingErrorBeforeFirstEventIsPlainResponse() = runTest {
        val handler = object : RequestHandler by MockRequestHandler {
            override fun onSubscribeToTask(request: SubscribeToTaskRequest, ctx: ServerCallContext): Flow<Event> =
                flow { throw A2AUnsupportedOperationException("Task is in a terminal state: ${request.id}") }
        }

        val outcome = handle(A2AMethod.SubscribeToTask, subscribeParams, handler = handler)

        assertEquals(
            errorResponse(
                requestId,
                A2AErrorCodes.UNSUPPORTED_OPERATION,
                "Task is in a terminal state: task-1",
                A2AErrorReasons.UNSUPPORTED_OPERATION
            ),
            outcome.singleError()
        )
    }

    @Test
    fun testStreamingErrorAfterFirstEventIsStreamEvent() = runTest {
        val handler = object : RequestHandler by MockRequestHandler {
            override fun onSubscribeToTask(request: SubscribeToTaskRequest, ctx: ServerCallContext): Flow<Event> =
                flow {
                    emit(MockRequestHandler.taskGet)
                    throw A2AInternalErrorException("Agent failed")
                }
        }

        val outcome = handle(A2AMethod.SubscribeToTask, subscribeParams, handler = handler)

        assertEquals(emptyList(), outcome.responses)

        val (first, second) = assertNotNull(outcome.streamed).also { assertEquals(2, it.size) }

        assertEquals(
            MockRequestHandler.taskGet,
            json.decodeFromJsonElement<Event>(assertIs<JSONRPCSuccessResponse>(first).result)
        )
        assertEquals(A2AErrorCodes.INTERNAL_ERROR, assertIs<JSONRPCErrorResponse>(second).error.code)
    }

    @Test
    fun testMissingOrEmptyVersionHeaderIsVersionNotSupported() = runTest {
        listOf(ServerCallContext(), ServerCallContext(mapOf(A2AHeaders.A2A_VERSION to listOf("")))).forEach { ctx ->
            val outcome = handle(A2AMethod.GetTask, getTaskParams, ctx = ctx)

            assertEquals(versionNotSupportedError("0.3"), outcome.singleError())
        }
    }

    @Test
    fun testVersionErrorOnStreamingMethodsIsPlainResponse() = runTest {
        listOf(
            A2AMethod.SendMessageStreaming to json.encodeToJsonElement(
                SendMessageRequest(Message(messageId = "m", role = Role.ROLE_USER, parts = listOf(TextPart("hi"))))
            ),
            A2AMethod.SubscribeToTask to subscribeParams,
        ).forEach { (method, params) ->
            val outcome = handle(method, params, ctx = ServerCallContext())

            assertEquals(versionNotSupportedError("0.3"), outcome.singleError(), method.value)
        }
    }

    @Test
    fun testVersionIsCheckedForEveryMethod() = runTest {
        A2AMethod.entries.forEach { method ->
            val outcome = handle(method, ctx = ServerCallContext(mapOf(A2AHeaders.A2A_VERSION to listOf("0.3"))))

            assertEquals(A2AErrorCodes.VERSION_NOT_SUPPORTED, outcome.singleError().error.code, method.value)
        }
    }

    @Test
    fun testUnsupportedVersionIsRejected() = runTest {
        listOf("0.3", "2.0", "99.0", "1.1").forEach { version ->
            val outcome = handle(
                A2AMethod.GetTask,
                getTaskParams,
                ctx = ServerCallContext(mapOf(A2AHeaders.A2A_VERSION to listOf(version)))
            )

            assertEquals(versionNotSupportedError(version), outcome.singleError(), version)
        }
    }

    @Test
    fun testPatchVersionIsIgnored() = runTest {
        val outcome = handle(
            A2AMethod.GetTask,
            getTaskParams,
            ctx = ServerCallContext(mapOf(A2AHeaders.A2A_VERSION to listOf("1.0.5")))
        )

        assertEquals(MockRequestHandler.taskGet, outcome.singleTask())
    }

    @Test
    fun testCustomSupportedVersions() = runTest {
        val transport = JSONRPCServerTransport(MockRequestHandler, supportedVersions = setOf("1.1", "0.3"))
        suspend fun get(ctx: ServerCallContext) =
            transport.handle(rawRequest(A2AMethod.GetTask.value, getTaskParams), ctx)

        // Missing header stands for 0.3, which is supported here
        assertEquals(MockRequestHandler.taskGet, get(ServerCallContext()).singleTask())
        assertEquals(
            MockRequestHandler.taskGet,
            get(ServerCallContext(mapOf(A2AHeaders.A2A_VERSION to listOf("1.1.2")))).singleTask()
        )

        // 1.0 is not in the custom set
        assertEquals(
            versionNotSupportedError("1.0", supported = "1.1, 0.3"),
            get(v1Ctx).singleError()
        )
    }

    @Test
    fun testUnsupportedVersionDoesNotReachRequestHandler() = runTest {
        var called = false

        handle(
            A2AMethod.GetTask,
            getTaskParams,
            ctx = ServerCallContext(mapOf(A2AHeaders.A2A_VERSION to listOf("0.3"))),
            handler = handlerRecording { called = true },
        )

        assertEquals(false, called)
    }

    @Test
    fun testCallContextReachesRequestHandlerUnchangedByDefault() = runTest {
        var seen: ServerCallContext? = null
        val ctx = ServerCallContext(
            headers = mapOf(A2AHeaders.A2A_VERSION to listOf("1.0.2"), A2AHeaders.A2A_EXTENSIONS to listOf("urn:a")),
        )

        handle(A2AMethod.GetTask, getTaskParams, ctx = ctx, handler = handlerRecording { seen = it })

        val handlerCtx = assertNotNull(seen)
        assertEquals(
            listOf(ctx.headers, ctx.state, ctx.requestedVersion, ctx.requestedExtensions, null),
            with(handlerCtx) { listOf(headers, state, requestedVersion, requestedExtensions, tenant) },
        )
    }

    @Test
    fun testCallContextTenantIsSetFromTheRequestOfEveryMethod() = runTest {
        val tenants = mutableMapOf<A2AMethod, String?>()
        fun ServerCallContext.seenBy(method: A2AMethod) = also { tenants[method] = tenant }

        val handler = object : RequestHandler by MockRequestHandler {
            override suspend fun onGetExtendedAgentCard(request: GetExtendedAgentCardRequest, ctx: ServerCallContext) =
                MockRequestHandler.onGetExtendedAgentCard(request, ctx.seenBy(A2AMethod.GetExtendedAgentCard))

            override suspend fun onSendMessage(request: SendMessageRequest, ctx: ServerCallContext) =
                MockRequestHandler.onSendMessage(request, ctx.seenBy(A2AMethod.SendMessage))

            override fun onSendMessageStreaming(request: SendMessageRequest, ctx: ServerCallContext) =
                MockRequestHandler.onSendMessageStreaming(request, ctx.seenBy(A2AMethod.SendMessageStreaming))

            override suspend fun onGetTask(request: GetTaskRequest, ctx: ServerCallContext) =
                MockRequestHandler.onGetTask(request, ctx.seenBy(A2AMethod.GetTask))

            override suspend fun onListTasks(request: ListTasksRequest, ctx: ServerCallContext) =
                MockRequestHandler.onListTasks(request, ctx.seenBy(A2AMethod.ListTasks))

            override suspend fun onCancelTask(request: CancelTaskRequest, ctx: ServerCallContext) =
                MockRequestHandler.onCancelTask(request, ctx.seenBy(A2AMethod.CancelTask))

            override fun onSubscribeToTask(request: SubscribeToTaskRequest, ctx: ServerCallContext) =
                MockRequestHandler.onSubscribeToTask(request, ctx.seenBy(A2AMethod.SubscribeToTask))

            override suspend fun onCreateTaskPushNotificationConfig(
                request: TaskPushNotificationConfig,
                ctx: ServerCallContext,
            ) = MockRequestHandler.onCreateTaskPushNotificationConfig(
                request,
                ctx.seenBy(A2AMethod.CreateTaskPushNotificationConfig),
            )

            override suspend fun onGetTaskPushNotificationConfig(
                request: GetTaskPushNotificationConfigRequest,
                ctx: ServerCallContext,
            ) = MockRequestHandler.onGetTaskPushNotificationConfig(
                request,
                ctx.seenBy(A2AMethod.GetTaskPushNotificationConfig),
            )

            override suspend fun onListTaskPushNotificationConfigs(
                request: ListTaskPushNotificationConfigsRequest,
                ctx: ServerCallContext,
            ) = MockRequestHandler.onListTaskPushNotificationConfigs(
                request,
                ctx.seenBy(A2AMethod.ListTaskPushNotificationConfigs),
            )

            override suspend fun onDeleteTaskPushNotificationConfig(
                request: DeleteTaskPushNotificationConfigRequest,
                ctx: ServerCallContext,
            ) = MockRequestHandler.onDeleteTaskPushNotificationConfig(
                request,
                ctx.seenBy(A2AMethod.DeleteTaskPushNotificationConfig),
            )
        }
        val message = Message(messageId = "msg-1", role = Role.ROLE_USER, parts = listOf(TextPart("Hi")))
        val requests = mapOf(
            A2AMethod.GetExtendedAgentCard to json.encodeToJsonElement(GetExtendedAgentCardRequest(tenant = "t")),
            A2AMethod.SendMessage to json.encodeToJsonElement(SendMessageRequest(message = message, tenant = "t")),
            A2AMethod.SendMessageStreaming to
                json.encodeToJsonElement(SendMessageRequest(message = message, tenant = "t")),
            A2AMethod.GetTask to json.encodeToJsonElement(GetTaskRequest(id = "task-1", tenant = "t")),
            A2AMethod.ListTasks to json.encodeToJsonElement(ListTasksRequest(tenant = "t")),
            A2AMethod.CancelTask to json.encodeToJsonElement(CancelTaskRequest(id = "task-1", tenant = "t")),
            A2AMethod.SubscribeToTask to json.encodeToJsonElement(SubscribeToTaskRequest(id = "task-1", tenant = "t")),
            A2AMethod.CreateTaskPushNotificationConfig to json.encodeToJsonElement(
                TaskPushNotificationConfig(url = "https://example.com", taskId = "task-1", tenant = "t")
            ),
            A2AMethod.GetTaskPushNotificationConfig to json.encodeToJsonElement(
                GetTaskPushNotificationConfigRequest(taskId = "task-1", id = "config-1", tenant = "t")
            ),
            A2AMethod.ListTaskPushNotificationConfigs to json.encodeToJsonElement(
                ListTaskPushNotificationConfigsRequest(taskId = "task-1", tenant = "t")
            ),
            A2AMethod.DeleteTaskPushNotificationConfig to json.encodeToJsonElement(
                DeleteTaskPushNotificationConfigRequest(taskId = "task-1", id = "config-1", tenant = "t")
            ),
        )

        requests.forEach { (method, params) -> handle(method, params, handler = handler) }

        assertEquals(A2AMethod.entries.associateWith<A2AMethod, String?> { "t" }, tenants)
    }

    private val userKey = StateKey<String>("user")

    @Test
    fun testCustomizedCallContextReachesRequestHandler() = runTest {
        var seen: ServerCallContext? = null
        var customizerInput: ServerCallContext? = null

        val outcome = handle(
            A2AMethod.GetTask,
            getTaskParams,
            handler = handlerRecording { seen = it },
            customizeCallContext = { ctx ->
                customizerInput = ctx
                ctx.copy(state = ctx.state + (userKey to "alice"))
            },
        )

        assertEquals(MockRequestHandler.taskGet, outcome.singleTask())
        assertEquals(v1Ctx, customizerInput)
        assertEquals(mapOf<StateKey<*>, Any>(userKey to "alice"), seen?.state)
        assertEquals("1.0", seen?.requestedVersion)
    }

    @Test
    fun testVersionIsCheckedOnCustomizedCallContext() = runTest {
        // No A2A-Version header: only the customized context carries a supported version
        val outcome = handle(
            A2AMethod.GetTask,
            getTaskParams,
            ctx = ServerCallContext(),
            customizeCallContext = { ctx -> ctx.copy(headers = mapOf(A2AHeaders.A2A_VERSION to listOf("1.0"))) },
        )

        assertEquals(MockRequestHandler.taskGet, outcome.singleTask())
    }

    @Test
    fun testA2AExceptionInCallContextCustomizerIsJsonRpcError() = runTest {
        listOf(
            A2AMethod.GetTask to getTaskParams,
            A2AMethod.SubscribeToTask to subscribeParams,
        ).forEach { (method, params) ->
            var handlerCalled = false
            val handler = object : RequestHandler by handlerRecording({ handlerCalled = true }) {
                override fun onSubscribeToTask(request: SubscribeToTaskRequest, ctx: ServerCallContext): Flow<Event> {
                    handlerCalled = true
                    return MockRequestHandler.updateEvents.asFlow()
                }
            }

            val outcome = handle(
                method,
                params,
                handler = handler,
                customizeCallContext = { throw A2AUnsupportedOperationException("Tenant is not allowed") },
            )

            assertEquals(
                errorResponse(
                    requestId,
                    A2AErrorCodes.UNSUPPORTED_OPERATION,
                    "Tenant is not allowed",
                    A2AErrorReasons.UNSUPPORTED_OPERATION
                ),
                outcome.singleError(),
                method.value
            )
            assertEquals(false, handlerCalled, method.value)
        }
    }

    @Test
    fun testNonA2AExceptionInCallContextCustomizerIsInternalError() = runTest {
        val outcome = handle(
            A2AMethod.GetTask,
            getTaskParams,
            customizeCallContext = { error("Principal lookup failed") },
        )

        val error = outcome.singleError()
        assertEquals(requestId, error.id)
        assertEquals(A2AErrorCodes.INTERNAL_ERROR, error.error.code)
    }

    @Test
    fun testCallContextCustomizerIsNotCalledForUnparsableRequest() = runTest {
        var customizerCalled = false

        val outcome = JSONRPCServerTransport(MockRequestHandler).handle("invalid json") { ctx ->
            customizerCalled = true
            ctx
        }

        assertEquals(A2AErrorCodes.PARSE_ERROR, outcome.singleError().error.code)
        assertEquals(false, customizerCalled)
    }

    @Test
    fun testSnakeCaseParamsAreHonored() = runTest {
        var seen: GetTaskRequest? = null
        val handler = object : RequestHandler by MockRequestHandler {
            override suspend fun onGetTask(request: GetTaskRequest, ctx: ServerCallContext): Task {
                seen = request
                return MockRequestHandler.taskGet
            }
        }

        //language=JSON
        val params = json.parseToJsonElement("""{"id": "task-1", "history_length": 2}""")
        val outcome = handle(A2AMethod.GetTask, params, handler = handler)

        assertEquals(MockRequestHandler.taskGet, outcome.singleTask())
        assertEquals(GetTaskRequest(id = "task-1", historyLength = 2), seen)
    }

    @Test
    fun testParamSentInTwoSpellingsIsInvalidParams() = runTest {
        //language=JSON
        val params = json.parseToJsonElement("""{"id": "task-1", "historyLength": 1, "history_length": 2}""")
        val outcome = handle(A2AMethod.GetTask, params)

        assertEquals(A2AErrorCodes.INVALID_PARAMS, outcome.singleError().error.code)
    }
}
