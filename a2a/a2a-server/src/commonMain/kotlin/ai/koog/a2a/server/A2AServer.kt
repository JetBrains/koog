package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AAuthenticatedExtendedCardNotConfiguredException
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2APushNotificationNotSupportedException
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
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.Session
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.server.session.SessionManager
import ai.koog.a2a.server.tasks.ContextTaskStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.Response
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    protected val pushConfigStorage: PushNotificationConfigStorage? = null,
    protected val pushSender: PushNotificationSender? = null,
    protected val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    protected val clock: Clock = Clock.System,
) : RequestHandler {
    protected val sessionManager: SessionManager = SessionManager(
        coroutineScope = coroutineScope,
        taskStorage = taskStorage,
        pushConfigStorage = pushConfigStorage,
        pushSender = pushSender,
    )

    override suspend fun onGetAuthenticatedExtendedAgentCard(
        request: Request<Nothing?>,
        ctx: ServerCallContext
    ): Response<AgentCard> {
        if (agentCard.supportsAuthenticatedExtendedCard != true) {
            throw A2AAuthenticatedExtendedCardNotConfiguredException("Extended agent card is not supported")
        }

        // Default server implementation does not provide authorization, return extended card directly if present
        return Response(
            data = agentCardExtended
                ?: throw A2AAuthenticatedExtendedCardNotConfiguredException("Extended agent card is supported but not configured on the server"),
            id = request.id
        )
    }

    /**
     * Common logic for handling incoming messages and starting the agent execution.
     * Does all the setup and validation, creates event stream.
     *
     * @return A stream of events from the agent
     */
    protected fun onSendMessageCommon(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Flow<Response<Event>> = channelFlow {
        val message = request.data.message
        val taskId = message.taskId

        // Check if message links to a task.
        val eventProcessor = if (taskId != null) {
            // Check if the task is still in progress, no message can be sent.
            if (sessionManager.sessionForTask(taskId) != null) {
                throw A2AUnsupportedOperationException("Task '$taskId' is still running, can't send messages to the task that has not yielded control")
            }

            // Check if the specified task exists and message context id matches the task context id.
            val task = taskStorage.get(taskId, historyLength = 0, includeArtifacts = false)
                ?: throw A2ATaskNotFoundException("Task '$taskId' not found")

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
        }

        // Create request context based on the request information.
        val requestContext = RequestContext(
            contextId = eventProcessor.contextId,
            callContext = ctx,
            params = request.data,
            taskStorage = ContextTaskStorage(eventProcessor.contextId, taskStorage),
            messageStorage = ContextMessageStorage(eventProcessor.contextId, messageStorage),
        )

        // Create agent execution session
        val session = Session(coroutineScope, eventProcessor) {
            agentExecutor.execute(requestContext, eventProcessor)
        }

        // Subscribe to events stream and start emitting them.
        launch {
            session.events
                .collect { event ->
                    send(Response(data = event, id = request.id))
                }
        }

        // Add to session manager, it will handle monitoring and closing once the session is completed (successfully or not).
        sessionManager.addSession(session)

        // Start the session to execute the agent and wait for it to finish.
        session.join()
    }

    override suspend fun onSendMessage(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Response<CommunicationEvent> {
        val messageConfiguration = request.data.configuration
        // Reusing streaming logic here, because it's essentially the same, only we need some particular event from the stream
        val eventStream = onSendMessageCommon(request, ctx)

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
    ): Flow<Response<Event>> = flow {
        checkStreamingSupport()
        emitAll(onSendMessageCommon(request, ctx))
    }

    override suspend fun onGetTask(
        request: Request<TaskQueryParams>,
        ctx: ServerCallContext
    ): Response<Task> {
        val taskParams = request.data

        return Response(
            data = taskStorage.get(taskParams.id, historyLength = taskParams.historyLength, includeArtifacts = false)
                ?: throw A2ATaskNotFoundException("Task '${taskParams.id}' not found"),
            id = request.id,
        )
    }

    override suspend fun onCancelTask(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<Task> {
        val taskParams = request.data
        val session = sessionManager.sessionForTask(taskParams.id)

        // Task is not running, check if it exists in the storage.
        if (session == null) {
            val task = taskStorage.get(taskParams.id, historyLength = 0, includeArtifacts = true)
                ?: throw A2ATaskNotFoundException("Task '${taskParams.id}' not found")

            // Task exists but not running - check if it is already canceled.
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
                taskStorage = ContextTaskStorage(session.contextId, taskStorage),
                messageStorage = ContextMessageStorage(session.contextId, messageStorage),
            )

            // Attempt to cancel the agent execution and wait until it's finished.
            agentExecutor.cancel(requestContext, session)

            // If cancel finished without exception, assume the cancellation was successful and close the session explicitly.
            session.close()
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
        checkStreamingSupport()

        val taskParams = request.data
        val session = sessionManager.sessionForTask(taskParams.id)
            ?: throw A2AUnsupportedOperationException("Task '${taskParams.id}' is not currently running or does not exist")

        emitAll(
            session.events
                .map { event -> Response(data = event, id = request.id) }
        )
    }

    override suspend fun onSetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfig>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig> {
        val pushStorage = storageIfPushNotificationSupported()
        val taskPushConfig = request.data

        pushStorage.save(taskPushConfig.taskId, taskPushConfig.pushNotificationConfig)

        return Response(data = taskPushConfig, id = request.id)
    }

    override suspend fun onGetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig> {
        val pushStorage = storageIfPushNotificationSupported()
        val pushConfigParams = request.data

        val pushConfig = pushStorage.get(pushConfigParams.id, pushConfigParams.pushNotificationConfigId)
            ?: throw NoSuchElementException("Can't find push notification config with id '${pushConfigParams.pushNotificationConfigId}' for task '${pushConfigParams.id}'")

        return Response(
            data = TaskPushNotificationConfig(
                taskId = pushConfigParams.id,
                pushNotificationConfig = pushConfig
            ),
            id = request.id
        )
    }

    override suspend fun onListTaskPushNotificationConfig(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<List<TaskPushNotificationConfig>> {
        val pushStorage = storageIfPushNotificationSupported()
        val taskParams = request.data

        return Response(
            data = pushStorage
                .getAll(taskParams.id)
                .map { TaskPushNotificationConfig(taskId = taskParams.id, pushNotificationConfig = it) },
            id = request.id
        )
    }

    override suspend fun onDeleteTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<Nothing?> {
        val pushStorage = storageIfPushNotificationSupported()
        val taskPushConfigParams = request.data

        pushStorage.delete(taskPushConfigParams.id, taskPushConfigParams.pushNotificationConfigId)

        return Response(data = null, id = request.id)
    }

    protected fun checkStreamingSupport() {
        if (agentCard.capabilities.streaming != true) {
            throw A2AUnsupportedOperationException("Streaming is not supported by the server")
        }
    }

    protected fun storageIfPushNotificationSupported(): PushNotificationConfigStorage {
        if (agentCard.capabilities.pushNotifications != true) {
            throw A2APushNotificationNotSupportedException("Push notifications are not supported by the server")
        }

        if (pushConfigStorage == null) {
            throw A2APushNotificationNotSupportedException("Push notifications are supported, but not configured on the server")
        }

        return pushConfigStorage
    }

    public fun cancel(cause: CancellationException? = null) {
        coroutineScope.cancel(cause)
    }
}
