package ai.koog.a2a.transport.jsonrpc

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/** [RequestHandler] that answers every method with fixed fixtures. */
internal object MockRequestHandler : RequestHandler {
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
