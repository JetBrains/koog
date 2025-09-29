package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AAuthenticatedExtendedCardNotConfiguredException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2APushNotificationNotSupportedException
import ai.koog.a2a.exceptions.A2ATaskNotCancelableException
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
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.messages.ContextMessageStorage
import ai.koog.a2a.server.messages.InMemoryMessageStorage
import ai.koog.a2a.server.messages.MessageStorage
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.session.IdGenerator
import ai.koog.a2a.server.session.LazySession
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.server.session.SessionManager
import ai.koog.a2a.server.session.UuidIdGenerator
import ai.koog.a2a.server.tasks.ContextTaskStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.Response
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.utils.KeyedMutex
import ai.koog.a2a.utils.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Default implementation of A2A server responsible for handling requests from A2A clients according to the
 * [A2A protocol specification](https://a2a-protocol.org/latest/specification/).
 *
 * This class provides a complete implementation of all A2A protocol methods including message sending, task management,
 * and push notifications. However, it **does not** provide any authorization, authentication, or custom validation
 * logic. For production use, you should extend this class and add your own security and business logic.
 *
 * The A2AServer orchestrates the interaction between transport layer, agent executor, and storage components:
 * - Receives requests from [RequestHandler] interface methods
 * - Delegates actual agent logic to [AgentExecutor]
 * - Delegates event processing and persisting to [SessionEventProcessor]
 * - Delegates session management to [SessionManager]
 * - Handles push notifications using [PushNotificationSender]
 *
 * ## Production usage with authorization
 *
 * For production deployments, extend this class to add authorization and custom validation. You can leverage
 * [ServerCallContext.state] to pass user-defined data through the request pipeline to the [AgentExecutor]:
 *
 * ```kotlin
 * // Define your user data and state keys
 * data class AuthenticatedUser(val id: String, val permissions: Set<String>)
 *
 * object AuthStateKeys {
 *     val USER = StateKey<AuthenticatedUser>("authenticated_user")
 * }
 *
 * // Extend A2AServer with authorization
 * class AuthorizedA2AServer(
 *     agentExecutor: AgentExecutor,
 *     agentCard: AgentCard,
 *     agentCardExtended: AgentCard? = null,
 *     taskStorage: TaskStorage = InMemoryTaskStorage(),
 *     messageStorage: MessageStorage = InMemoryMessageStorage(),
 *     private val authService: AuthService,  // Your auth service
 * ) : A2AServer(
 *     agentExecutor = agentExecutor,
 *     agentCard = agentCard,
 *     agentCardExtended = agentCardExtended,
 *     taskStorage = taskStorage,
 *     messageStorage = messageStorage,
 * ) {
 *     // Helper method for common auth pattern
 *     private suspend fun authenticateAndAuthorize(
 *         ctx: ServerCallContext,
 *         requiredPermission: String
 *     ): AuthenticatedUser {
 *         val token = ctx.headers["Authorization"]?.firstOrNull()
 *             ?: throw A2AInvalidParamsException("Missing authorization token")
 *
 *         val user = authService.authenticate(token)
 *             ?: throw A2AInvalidParamsException("Invalid token")
 *
 *         if (!user.permissions.contains(requiredPermission)) {
 *             throw A2AUnsupportedOperationException("Insufficient permissions")
 *         }
 *
 *         return user
 *     }
 *
 *     override suspend fun onSendMessage(
 *         request: Request<MessageSendParams>,
 *         ctx: ServerCallContext
 *     ): Response<CommunicationEvent> {
 *         val user = authenticateAndAuthorize(ctx, requiredPermission = "send_message")
 *
 *         // Pass user data to the agent executor via context state
 *         val enrichedCtx = ctx.copy(
 *             state = ctx.state + (AuthStateKeys.USER to user)
 *         )
 *
 *         // Delegate to parent implementation with enriched context
 *         return super.onSendMessage(request, enrichedCtx)
 *     }
 *
 *     override suspend fun onGetTask(
 *         request: Request<TaskQueryParams>,
 *         ctx: ServerCallContext
 *     ): Response<Task> {
 *         val user = authenticateAndAuthorize(ctx, requiredPermission = "read_task")
 *
 *         // Optionally validate task ownership
 *         val task = taskStorage.get(request.data.id, historyLength = 0, includeArtifacts = false)
 *         if (task?.metadata?.get("owner_id") != user.id) {
 *             throw A2AUnsupportedOperationException("Access denied to task ${request.data.id}")
 *         }
 *
 *         val enrichedCtx = ctx.copy(
 *             state = ctx.state + (AuthStateKeys.USER to user)
 *         )
 *
 *         return super.onGetTask(request, enrichedCtx)
 *     }
 * }
 * ```
 *
 * ## Accessing user data in AgentExecutor
 *
 * The authenticated user data passed through [ServerCallContext.state] can be accessed in your [AgentExecutor]:
 *
 * ```kotlin
 * class MyAgentExecutor : AgentExecutor {
 *     override suspend fun execute(
 *         context: RequestContext<MessageSendParams>,
 *         eventProcessor: SessionEventProcessor
 *     ) {
 *         // Retrieve authenticated user from the context
 *         val user = context.callContext.getFromState(AuthStateKeys.USER)
 *
 *         // Use user information for personalized agent behavior
 *         eventProcessor.sendMessage(
 *             Message(
 *                 role = Role.Agent,
 *                 contextId = context.contextId,
 *                 parts = listOf(
 *                     TextPart("Hello ${user.id}, how can I help you today?")
 *                 )
 *             )
 *         )
 *     }
 *
 *     override suspend fun cancel(
 *         context: RequestContext<TaskIdParams>,
 *         eventProcessor: SessionEventProcessor,
 *         agentJob: Deferred<Unit>?,
 *     ) {
 *         agentJob?.cancelAndJoin()
 *         // Access user data for audit logging
 *         val user = context.callContext.getFromStateOrNull(AuthStateKeys.USER)
 *         log.info("Task ${context.taskId} canceled by user ${user?.id}")
 *     }
 * }
 * ```
 *
 * ## Complete server setup example
 *
 * Here's a complete example of setting up and running an A2A server from scratch:
 *
 * ```kotlin
 * // 1. Create your agent executor with business logic
 * val agentExecutor = object : AgentExecutor {
 *     override suspend fun execute(
 *         context: RequestContext<MessageSendParams>,
 *         eventProcessor: SessionEventProcessor
 *     ) {
 *         val userMessage = context.params.message
 *
 *         // Process the message and create a task
 *         // Send task creation event
 *         eventProcessor.sendTaskEvent(
 *             Task(
 *                 id = context.taskId,
 *                 contextId = context.contextId,
 *                 status = TaskStatus(
 *                     state = TaskState.Working,
 *                     // Mark this message as belonging to the created task
 *                     message = message.copy(taskId = context.taskId)
 *                     timestamp = Clock.System.now()
 *                 ),
 *             )
 *         )
 *
 *         // Simulate some work
 *         delay(1000)
 *
 *         // Mark task as completed
 *         eventProcessor.sendTaskEvent(
 *             TaskStatusUpdateEvent(
 *                 taskId = context.taskId,
 *                 contextId = context.contextId,
 *                 status = TaskStatus(
 *                     state = TaskState.Completed,
 *                     message = Message(
 *                        role = Role.Agent,
 *                        contextId = context.contextId,
 *                        taskId = context.taskId,
 *                        parts = listOf(
 *                            TextPart("Task completed successfully!")
 *                        )
 *                    ),
 *                    timestamp = Clock.System.now()
 *                 ),
 *                 final = true
 *             )
 *         )
 *     }
 * }
 *
 * // 2. Define your agent card describing capabilities
 * val agentCard = AgentCard(...)
 *
 * // 3. Create the A2AServer instance (or your extended version)
 * val a2aServer = A2AServer(...)
 *
 * // 4. Create HTTP JSON-RPC transport
 * val transport = HttpJSONRPCServerTransport(
 *     requestHandler = a2aServer
 * )
 *
 * // 5. Start the server
 * transport.start(
 *     engineFactory = Netty,
 *     port = 8080,
 *     path = "/a2a",
 *     wait = true,
 *     agentCard = agentCard,
 *     agentCardPath = A2AConsts.AGENT_CARD_WELL_KNOWN_PATH
 * )
 * ```
 *
 * ## Integration with existing Ktor application
 *
 * If you have an existing Ktor application, you can integrate the A2A server as a route:
 *
 * ```kotlin
 * val agentCard = AgentCard(...)
 * val a2aServer = A2AServer(...)
 * val transport = HttpJSONRPCServerTransport(a2aServer)
 *
 * embeddedServer(Netty, port = 8080) {
 *     install(SSE)  // Required for streaming support
 *
 *     // To serve AgentCard instance
 *     install(ContentNegotiation) {
 *        json(Json)
 *     }
 *
 *     routing {
 *         // Your existing routes...
 *
 *         // Mount A2A JSON-RPC server transport
 *         transport.transportRoutes(this, "/a2a")
 *
 *         // Serve agent card
 *         get("/a2a/agent-card.json") {
 *             call.respond(agentCard)
 *         }
 *     }
 * }.start(wait = true)
 * ```
 *
 * @param agentExecutor The executor containing the core agent logic
 * @param agentCard The agent card describing this agent's capabilities and metadata
 * @param agentCardExtended Optional extended agent card for authenticated requests
 * @param taskStorage Storage implementation for persisting tasks (defaults to [InMemoryTaskStorage])
 * @param messageStorage Storage implementation for persisting messages (defaults to [InMemoryMessageStorage])
 * @param pushConfigStorage Optional storage for push notification configurations (defaults to `null`)
 * @param pushSender Optional push notification sender implementation (defaults to `null`)
 * @param idGenerator Generator for new task and context IDs (defaults to [UuidIdGenerator])
 * @param coroutineScope Scope for managing all sessions, agent jobs, event processing, etc.
 *
 * @see AgentExecutor for implementing agent business logic
 * @see TaskStorage for persisting tasks
 * @see MessageStorage for persisting messages
 * @see PushNotificationConfigStorage for persisting push notification configurations
 * @see PushNotificationSender for sending push notifications
 * @see ServerCallContext for passing custom state through the request pipeline
 */
public open class A2AServer(
    protected val agentExecutor: AgentExecutor,
    protected val agentCard: AgentCard,
    protected val agentCardExtended: AgentCard? = null,
    protected val taskStorage: TaskStorage = InMemoryTaskStorage(),
    protected val messageStorage: MessageStorage = InMemoryMessageStorage(),
    protected val pushConfigStorage: PushNotificationConfigStorage? = null,
    protected val pushSender: PushNotificationSender? = null,
    protected val idGenerator: IdGenerator = UuidIdGenerator,
    protected val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob()),
) : RequestHandler {
    /**
     * Mutex for locking specific tasks by their IDs.
     */
    protected val tasksMutex: KeyedMutex<String> = KeyedMutex()

    /**
     * Special cancellation key for additional set of task cancellation locks.
     */
    protected fun cancelKey(taskId: String): String = "cancel:$taskId"

    protected open val sessionManager: SessionManager = SessionManager(
        coroutineScope = coroutineScope,
        cancelKey = ::cancelKey,
        tasksMutex = tasksMutex,
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
    protected open fun onSendMessageCommon(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Flow<Response<Event>> = channelFlow {
        val message = request.data.message

        if (message.parts.isEmpty()) {
            throw A2AInvalidParamsException("Empty message parts are not supported")
        }

        val taskId = message.taskId ?: idGenerator.generateTaskId(message)

        val session = tasksMutex.withLock(taskId) {
            // If there's a currently running session for the same task, wait for it to finish.
            sessionManager.getSession(taskId)?.join()

            // Check if message links to a task.
            val task: Task? = message.taskId?.let { taskId ->
                // Check if the specified task exists
                val task = taskStorage.get(taskId, historyLength = 0, includeArtifacts = false)
                    ?: throw A2ATaskNotFoundException("Task '$taskId' not found")

                task
            }

            // Create event processor for the session based on the input data.
            val eventProcessor = SessionEventProcessor(
                contextId = task?.contextId
                    ?: message.contextId
                    ?: idGenerator.generateContextId(message),
                taskId = taskId,
                taskStorage = taskStorage,
            )

            // Create request context based on the request information.
            val requestContext = RequestContext(
                callContext = ctx,
                params = request.data,
                taskStorage = ContextTaskStorage(eventProcessor.contextId, taskStorage),
                messageStorage = ContextMessageStorage(eventProcessor.contextId, messageStorage),
                contextId = eventProcessor.contextId,
                taskId = eventProcessor.taskId,
                task = task,
            )

            LazySession(
                coroutineScope = coroutineScope,
                eventProcessor = eventProcessor,
            ) {
                agentExecutor.execute(requestContext, eventProcessor)
            }.also {
                sessionManager.addSession(it)
            }
        }

        // Subscribe to events stream and start emitting them.
        launch {
            session.events
                .collect { event ->
                    send(Response(data = event, id = request.id))
                }
        }

        // Start the session to execute the agent and wait for it to finish.
        // Using await here to propagate any exceptions thrown by the agent execution.
        session.agentJob.await()
    }

    override suspend fun onSendMessage(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Response<CommunicationEvent> {
        val messageConfiguration = request.data.configuration
        // Reusing streaming logic here, because it's essentially the same, only we need some particular event from the stream
        val eventStream = onSendMessageCommon(request, ctx)

        val event = if (messageConfiguration?.blocking == true) {
            // If blocking is requested, attempt to wait for the last event, until the current turn of the agent execution is finished.
            eventStream.last()
        } else {
            eventStream.first()
        }

        return when (val eventData = event.data) {
            is Message -> Response(data = eventData, id = event.id)
            is TaskEvent ->
                taskStorage
                    .get(
                        eventData.taskId,
                        historyLength = messageConfiguration?.historyLength,
                        includeArtifacts = true
                    )
                    ?.let { Response(data = it, id = event.id) }
                    ?: throw A2ATaskNotFoundException("Task '${eventData.taskId}' not found after the agent execution")
        }
    }

    override fun onSendMessageStreaming(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Flow<Response<Event>> = flow {
        checkStreamingSupport()
        onSendMessageCommon(request, ctx).collect(this)
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
        val taskId = taskParams.id

        /*
         Cancellation uses two lock levels. The first is the standard task lock.
         If it’s already held by another request, ignore it because cancellation takes priority.
         If it’s not held, acquire it to block new requests while the cancellation is in progress.
         */
        val lockAcquired = tasksMutex.tryLock(taskId)

        return try {
            /*
             The second lock is a per-task cancellation lock.
             It’s always taken during cancellation to serialize cancel operations and allow them to proceed even if the
             regular task lock is held. It prevents overlapping cancels and delays session teardown so the event processor
             isn’t closed immediately after the agent job is canceled. This allows the cancel handler to emit additional
             cancellation events through the same processor and session, ensuring that existing subscribers receive all events.
             */
            tasksMutex.withLock(cancelKey(taskId)) {
                val session = sessionManager.getSession(taskParams.id)

                val task = taskStorage.get(taskParams.id, historyLength = 0, includeArtifacts = true)
                    ?: throw A2ATaskNotFoundException("Task '${taskParams.id}' not found")

                // Task is not running, check if it's already in a terminal state.
                if (session == null && task.status.state.terminal) {
                    throw A2ATaskNotCancelableException("Task '${taskParams.id}' is already in terminal state ${task.status.state}")
                }

                val eventProcessor = session?.eventProcessor ?: SessionEventProcessor(
                    contextId = task.contextId,
                    taskId = task.id,
                    taskStorage = taskStorage,
                )

                // Create request context based on the request information.
                val requestContext = RequestContext(
                    callContext = ctx,
                    params = request.data,
                    taskStorage = ContextTaskStorage(eventProcessor.contextId, taskStorage),
                    messageStorage = ContextMessageStorage(eventProcessor.contextId, messageStorage),
                    contextId = eventProcessor.contextId,
                    taskId = eventProcessor.taskId,
                    task = task,
                )

                // Attempt to cancel the agent execution and wait until it's finished.
                agentExecutor.cancel(requestContext, eventProcessor, session?.agentJob)

                // Return the final task state.
                Response(
                    data = taskStorage.get(taskParams.id, historyLength = 0, includeArtifacts = true)
                        ?.also {
                            if (it.status.state != TaskState.Canceled) {
                                throw A2ATaskNotCancelableException("Task '${taskParams.id}' was not canceled successfully, current state is ${it.status.state}")
                            }
                        }
                        ?: throw A2ATaskNotFoundException("Task '${taskParams.id}' not found"),
                    id = request.id,
                )
            }
        } finally {
            if (lockAcquired) {
                tasksMutex.unlock(taskId)
            }
        }
    }

    override fun onResubscribeTask(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Flow<Response<Event>> = flow {
        checkStreamingSupport()

        val taskParams = request.data
        val session = sessionManager.getSession(taskParams.id) ?: return@flow

        session.events
            .map { event -> Response(data = event, id = request.id) }
            .collect(this)
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

    protected open fun checkStreamingSupport() {
        if (agentCard.capabilities.streaming != true) {
            throw A2AUnsupportedOperationException("Streaming is not supported by the server")
        }
    }

    protected open fun storageIfPushNotificationSupported(): PushNotificationConfigStorage {
        if (agentCard.capabilities.pushNotifications != true) {
            throw A2APushNotificationNotSupportedException("Push notifications are not supported by the server")
        }

        if (pushConfigStorage == null) {
            throw A2APushNotificationNotSupportedException("Push notifications are supported, but not configured on the server")
        }

        return pushConfigStorage
    }

    /**
     * Cancels [coroutineScope] associated with this server, essentially cancelling all running jobs and sessions.
     *
     * @param cause Optional cause of the cancellation
     */
    public open fun cancel(cause: CancellationException? = null) {
        coroutineScope.cancel(cause)
    }
}
