package ai.koog.a2a.client

import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentInterface
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
import ai.koog.a2a.model.Request
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
import ai.koog.a2a.transport.ClientCallContext
import ai.koog.a2a.transport.ClientTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class A2AClientTest {
    private val task = Task(id = "task-1", status = TaskStatus(TaskState.TASK_STATE_WORKING))

    private fun card(
        interfaces: List<AgentInterface>,
        capabilities: AgentCapabilities = AgentCapabilities(
            streaming = true,
            pushNotifications = true,
            extendedAgentCard = true,
        ),
    ) = AgentCard(
        name = "Test Agent",
        description = "A test agent",
        supportedInterfaces = interfaces,
        version = "1.0.0",
        defaultInputModes = listOf("text/plain"),
        defaultOutputModes = listOf("text/plain"),
        skills = emptyList(),
        capabilities = capabilities,
    )

    private fun jsonRpcInterface(tenant: String?) = AgentInterface(
        url = "https://example.com/a2a",
        protocolBinding = TransportProtocol.JSONRPC,
        protocolVersion = "1.0",
        tenant = tenant,
    )

    private fun client(card: AgentCard, transport: RecordingTransport = RecordingTransport()) =
        A2AClient(card, transport)

    @Test
    fun testTenantFilledForEveryRequestType() = runTest {
        val transport = RecordingTransport()
        val client = client(card(listOf(jsonRpcInterface("acme"))), transport)

        val message = Message(messageId = "m-1", role = Role.ROLE_USER, parts = listOf(TextPart("hi")))
        val pushConfig = TaskPushNotificationConfig(url = "https://example.com/hook", taskId = "task-1")

        client.sendMessage(SendMessageRequest(message))
        client.sendMessageStreaming(SendMessageRequest(message))
        client.getTask(GetTaskRequest("task-1"))
        client.listTasks(ListTasksRequest())
        client.cancelTask(CancelTaskRequest("task-1"))
        client.subscribeToTask(SubscribeToTaskRequest("task-1"))
        client.createTaskPushNotificationConfig(pushConfig)
        client.getTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest("task-1", "cfg-1"))
        client.listTaskPushNotificationConfigs(ListTaskPushNotificationConfigsRequest("task-1"))
        client.deleteTaskPushNotificationConfig(DeleteTaskPushNotificationConfigRequest("task-1", "cfg-1"))
        client.getExtendedAgentCard(GetExtendedAgentCardRequest())

        assertEquals(
            listOf<Request>(
                SendMessageRequest(message, tenant = "acme"),
                SendMessageRequest(message, tenant = "acme"),
                GetTaskRequest("task-1", tenant = "acme"),
                ListTasksRequest(tenant = "acme"),
                CancelTaskRequest("task-1", tenant = "acme"),
                SubscribeToTaskRequest("task-1", tenant = "acme"),
                pushConfig.copy(tenant = "acme"),
                GetTaskPushNotificationConfigRequest("task-1", "cfg-1", tenant = "acme"),
                ListTaskPushNotificationConfigsRequest("task-1", tenant = "acme"),
                DeleteTaskPushNotificationConfigRequest("task-1", "cfg-1", tenant = "acme"),
                GetExtendedAgentCardRequest(tenant = "acme"),
            ),
            transport.requests
        )
    }

    @Test
    fun testRequestTenantIsNotOverridden() = runTest {
        val transport = RecordingTransport()
        val client = client(card(listOf(jsonRpcInterface("acme"))), transport)

        client.getTask(GetTaskRequest("task-1", tenant = "other"))

        assertEquals(listOf<Request>(GetTaskRequest("task-1", tenant = "other")), transport.requests)
    }

    @Test
    fun testTenantFollowsReplacedCard() = runTest {
        val transport = RecordingTransport(extendedCard = card(listOf(jsonRpcInterface("extended"))))
        val client = client(card(listOf(jsonRpcInterface("initial"))), transport)

        client.getTask(GetTaskRequest("task-1"))
        client.getExtendedAgentCard(GetExtendedAgentCardRequest())
        client.getTask(GetTaskRequest("task-1"))

        assertEquals(
            listOf<Request>(
                GetTaskRequest("task-1", tenant = "initial"),
                GetExtendedAgentCardRequest(tenant = "initial"),
                GetTaskRequest("task-1", tenant = "extended"),
            ),
            transport.requests
        )
    }

    private inner class RecordingTransport(
        private val extendedCard: AgentCard = card(emptyList()),
    ) : ClientTransport {
        val requests = mutableListOf<Request>()

        override suspend fun getExtendedAgentCard(
            request: GetExtendedAgentCardRequest,
            ctx: ClientCallContext
        ): AgentCard {
            requests += request
            return extendedCard
        }

        override suspend fun sendMessage(request: SendMessageRequest, ctx: ClientCallContext): ResponseEvent {
            requests += request
            return task
        }

        override fun sendMessageStreaming(request: SendMessageRequest, ctx: ClientCallContext): Flow<Event> {
            requests += request
            return emptyFlow()
        }

        override suspend fun getTask(request: GetTaskRequest, ctx: ClientCallContext): Task {
            requests += request
            return task
        }

        override suspend fun listTasks(request: ListTasksRequest, ctx: ClientCallContext): ListTasksResponse {
            requests += request
            return ListTasksResponse(tasks = emptyList(), nextPageToken = "", pageSize = 0, totalSize = 0)
        }

        override suspend fun cancelTask(request: CancelTaskRequest, ctx: ClientCallContext): Task {
            requests += request
            return task
        }

        override fun subscribeToTask(request: SubscribeToTaskRequest, ctx: ClientCallContext): Flow<Event> {
            requests += request
            return emptyFlow()
        }

        override suspend fun createTaskPushNotificationConfig(
            request: TaskPushNotificationConfig,
            ctx: ClientCallContext
        ): TaskPushNotificationConfig {
            requests += request
            return request
        }

        override suspend fun getTaskPushNotificationConfig(
            request: GetTaskPushNotificationConfigRequest,
            ctx: ClientCallContext
        ): TaskPushNotificationConfig {
            requests += request
            return TaskPushNotificationConfig(url = "https://example.com/hook")
        }

        override suspend fun listTaskPushNotificationConfigs(
            request: ListTaskPushNotificationConfigsRequest,
            ctx: ClientCallContext
        ): ListTaskPushNotificationConfigsResponse {
            requests += request
            return ListTaskPushNotificationConfigsResponse()
        }

        override suspend fun deleteTaskPushNotificationConfig(
            request: DeleteTaskPushNotificationConfigRequest,
            ctx: ClientCallContext
        ) {
            requests += request
        }

        override fun close() {}
    }
}
