package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.CommunicationEvent
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskPushNotificationConfigParams
import ai.koog.a2a.model.TaskQueryParams
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.messages.ContextMessageStorage
import ai.koog.a2a.server.messages.InMemoryMessageStorage
import ai.koog.a2a.server.messages.MessageStorage
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.server.session.SessionManager
import ai.koog.a2a.server.tasks.ContextTaskStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.Response
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A2A server responsible for handling requests from A2A clients.
 */
public open class A2AServer(
    protected val agentExecutor: AgentExecutor,
    protected val agentCard: AgentCard,
    protected val agentCardExtended: AgentCard? = null,
    protected val taskStorage: TaskStorage = InMemoryTaskStorage(),
    protected val messageStorage: MessageStorage = InMemoryMessageStorage(),
    protected val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    protected val clock: Clock = Clock.System,
) : RequestHandler {
    protected val sessionManager: SessionManager = SessionManager(coroutineScope)

    override suspend fun onGetAuthenticatedExtendedAgentCard(
        request: Request<Nothing?>,
        ctx: ServerCallContext
    ): Response<AgentCard> {
        // Default server implementation does not provide authorization, return extended card directly if present
        return Response(
            data = agentCardExtended ?: agentCard,
            id = request.id
        )
    }

    override suspend fun onSendMessage(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Response<CommunicationEvent> {
        val messageConfiguration = request.data.configuration
        // Reusing streaming logic here, because it's essentially the same, only we need some particular event from the stream
        val eventStream = onSendMessageStreaming(request, ctx)

        return if (messageConfiguration?.blocking == true) {
            // If blocking is requested, attempt to wait for the last event, until the current turn of the agent execution is finished.
            val lastEventResponse = eventStream.last()

            when (val eventData = lastEventResponse.data) {
                is Message -> Response(data = eventData, id = lastEventResponse.id)
                is TaskEvent ->
                    taskStorage
                        .get(
                            eventData.taskId,
                            historyLength = messageConfiguration.historyLength,
                            includeArtifacts = true
                        )
                        ?.let { Response(data = it, id = lastEventResponse.id) }
                        ?: throw A2ATaskNotFoundException("Task '${eventData.taskId}' not found after the agent execution")
            }
        } else {
            // Else read the first event from the stream, check that it's a proper communication event and return it.
            val firstEventResponse = eventStream.first()

            when (val eventData = firstEventResponse.data) {
                is Message -> Response(data = eventData, id = firstEventResponse.id)
                is Task -> Response(data = eventData, id = firstEventResponse.id)
                else -> throw A2AInternalErrorException("Got unexpected event type from the agent '${eventData::class.simpleName}'")
            }
        }
    }

    override fun onSendMessageStreaming(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Flow<Response<Event>> = channelFlow {
        val message = request.data.message
        val taskId = message.taskId

        // Check if message links to a task.
        val eventProcessor = if (taskId != null) {
            // Check if the task is still in progress, no message can be sent.
            if (sessionManager.processorForTask(taskId) != null) {
                throw A2AUnsupportedOperationException("Task '$taskId' is still running, can't send messages to the task that has not yielded control")
            }

            // Check if the specified task exists and message context id matches the task context id.
            val task = taskStorage.get(taskId) ?: throw A2ATaskNotFoundException("Task '$taskId' not found")
            if (message.contextId != task.contextId) {
                throw A2AInvalidParamsException("Message context id '${message.contextId}' doesn't match task context id '${task.contextId}'")
            }

            // Create new event processor for the task.
            SessionEventProcessor(
                contextId = task.contextId,
                taskStorage = taskStorage,
                coroutineScope = coroutineScope,
                currentTask = task
            )
        } else {
            // Create new event processor without task specified.
            @OptIn(ExperimentalUuidApi::class)
            SessionEventProcessor(
                contextId = message.contextId ?: Uuid.random().toString(),
                taskStorage = taskStorage,
                // Use specified context id or generate a new random one.
                coroutineScope = coroutineScope,
            )
        }.also {
            sessionManager.addProcessor(it)
        }

        // Create request context based on the request information.
        val requestContext = RequestContext(
            contextId = eventProcessor.contextId,
            callContext = ctx,
            params = request.data,
            taskStorage = ContextTaskStorage(eventProcessor.contextId, taskStorage),
            messageStorage = ContextMessageStorage(eventProcessor.contextId, messageStorage),
        )

        // Subscribe to events stream and start emitting them.
        val collectionJob = launch {
            eventProcessor.events
                .collect { event ->
                    send(Response(data = event, id = request.id))
                }
        }

        // Execute the agent.
        agentExecutor.execute(requestContext, eventProcessor)

        // Close event processor session and collecting job
        eventProcessor.close()
        collectionJob.cancel()
    }

    override suspend fun onGetTask(
        request: Request<TaskQueryParams>,
        ctx: ServerCallContext
    ): Response<Task> {
        val taskParams = request.data

        return Response(
            data = taskStorage.get(taskParams.id, historyLength = taskParams.historyLength)
                ?: throw A2ATaskNotFoundException("Task '${taskParams.id}' not found"),
            id = request.id,
        )
    }

    override suspend fun onCancelTask(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<Task> {
        val taskParams = request.data
        val eventProcessor = sessionManager.processorForTask(taskParams.id)

        // Task is not running, check if it exists in the storage.
        if (eventProcessor == null) {
            val task = taskStorage.get(taskParams.id, historyLength = 0, includeArtifacts = true)
                ?: throw A2ATaskNotFoundException("Task '${taskParams.id}' not found")

            // Task exists but not running - check if it is already canceled
            if (task.status.state == TaskState.Canceled) {
                return Response(data = task, id = request.id)
            }

            // If the task is not canceled and in the terminal state, throw.
            if (task.status.state.terminal) {
                throw A2AUnsupportedOperationException("Task '${taskParams.id}' is already in terminal state ${task.status.state}")
            }

            // Proceed to mark the task as canceled.
            taskStorage.update(
                TaskStatusUpdateEvent(
                    taskId = task.id,
                    contextId = task.contextId,
                    status = TaskStatus(
                        state = TaskState.Canceled,
                        timestamp = clock.now()
                    ),
                    final = true
                )
            )
        } else {
            // Create request context based on the request information.
            val requestContext = RequestContext(
                contextId = taskParams.id,
                callContext = ctx,
                params = request.data,
                taskStorage = ContextTaskStorage(eventProcessor.contextId, taskStorage),
                messageStorage = ContextMessageStorage(eventProcessor.contextId, messageStorage),
            )

            // Attempt to cancel the agent execution and wait until it's finished.
            agentExecutor.cancel(requestContext, eventProcessor)

            // If `cancel` finished without exceptions, assume the cancellation was successful and close event processor session too.
            eventProcessor.close()
        }

        // Return the final task state.
        return Response(
            data = taskStorage.get(taskParams.id, historyLength = 0, includeArtifacts = true)
                ?: throw A2ATaskNotFoundException("Task '${taskParams.id}' not found"),
            id = request.id,
        )
    }

    override fun onResubscribeTask(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Flow<Response<Event>> = flow {
        val taskParams = request.data
        val eventProcessor = sessionManager.processorForTask(taskParams.id)
            ?: throw A2AUnsupportedOperationException("Task '${taskParams.id}' is not currently running or does not exist")

        emitAll(
            eventProcessor.events
                .map { event -> Response(data = event, id = request.id) }
        )
    }

    override suspend fun onSetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfig>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig> {
        TODO("Not yet implemented")
    }

    override suspend fun onGetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig> {
        TODO("Not yet implemented")
    }

    override suspend fun onListTaskPushNotificationConfig(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<List<TaskPushNotificationConfig>> {
        TODO("Not yet implemented")
    }

    override suspend fun onDeleteTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<Nothing?> {
        TODO("Not yet implemented")
    }
}
