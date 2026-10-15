package ai.koog.a2a.server

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.consts.A2AHeaders
import ai.koog.a2a.exceptions.A2AExtendedAgentCardNotConfiguredException
import ai.koog.a2a.exceptions.A2AExtensionSupportRequiredException
import ai.koog.a2a.exceptions.A2AInvalidAgentResponseException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2APushNotificationNotSupportedException
import ai.koog.a2a.exceptions.A2ATaskNotCancelableException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.AgentCard
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
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.exceptions.TaskOperationException
import ai.koog.a2a.server.messages.ContextMessageStorage
import ai.koog.a2a.server.messages.InMemoryMessageStorage
import ai.koog.a2a.server.messages.MessageStorage
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.session.IdGenerator
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.Session
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.server.session.SessionManager
import ai.koog.a2a.server.session.UuidIdGenerator
import ai.koog.a2a.server.tasks.ContextTaskStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.tasks.isTerminal
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.agents.lock.KeyedMutex
import ai.koog.agents.lock.withLock
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlin.time.Clock

/**
 * Default implementation of A2A server responsible for handling requests from A2A clients according to the
 * [A2A protocol specification](https://a2a-protocol.org/v1.0.1/specification/).
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
 *         request: SendMessageRequest,
 *         ctx: ServerCallContext
 *     ): ResponseEvent {
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
 *         request: GetTaskRequest,
 *         ctx: ServerCallContext
 *     ): Task {
 *         val user = authenticateAndAuthorize(ctx, requiredPermission = "read_task")
 *
 *         // Optionally validate task ownership
 *         val task = taskStorage.get(request.id, historyLength = 0, includeArtifacts = false)
 *         if (task?.metadata?.get("owner_id") != user.id) {
 *             throw A2AUnsupportedOperationException("Access denied to task ${request.id}")
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
 *         context: RequestContext<SendMessageRequest>,
 *         eventProcessor: SessionEventProcessor
 *     ) {
 *         // Retrieve authenticated user from the context
 *         val user = context.callContext.getFromState(AuthStateKeys.USER)
 *
 *         // Use user information for personalized agent behavior
 *         eventProcessor.sendMessage(
 *             Message(
 *                 messageId = Uuid.random().toString(),
 *                 role = Role.ROLE_AGENT,
 *                 contextId = context.contextId,
 *                 parts = listOf(
 *                     TextPart("Hello ${user.id}, how can I help you today?")
 *                 )
 *             )
 *         )
 *     }
 *
 *     override suspend fun cancel(
 *         context: RequestContext<CancelTaskRequest>,
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
 *         context: RequestContext<SendMessageRequest>,
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
 *                     state = TaskState.TASK_STATE_WORKING,
 *                     timestamp = Clock.System.now()
 *                 ),
 *                 // Optional: the server adds the user message to the task history if it is not there already
 *                 history = listOf(userMessage.copy(taskId = context.taskId)),
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
 *                     state = TaskState.TASK_STATE_COMPLETED,
 *                     message = Message(
 *                        messageId = Uuid.random().toString(),
 *                        role = Role.ROLE_AGENT,
 *                        contextId = context.contextId,
 *                        taskId = context.taskId,
 *                        parts = listOf(
 *                            TextPart("Task completed successfully!")
 *                        )
 *                    ),
 *                    timestamp = Clock.System.now()
 *                 ),
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
 *     agentCardPath = A2APaths.AGENT_CARD_WELL_KNOWN_PATH
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
 * @param idGenerator Generator for new task, context and push notification config IDs (defaults to [UuidIdGenerator])
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
    private companion object {
        private val logger = KotlinLogging.logger {}

        private const val MIN_LIST_TASKS_PAGE_SIZE = 1
        private const val MAX_LIST_TASKS_PAGE_SIZE = 100

        /**
         * Non-terminal states in which the task waits for the client, ending a blocking SendMessage.
         */
        private val INTERRUPTED_STATES = setOf(TaskState.TASK_STATE_INPUT_REQUIRED, TaskState.TASK_STATE_AUTH_REQUIRED)
    }

    /**
     * Mutex for locking specific tasks by their IDs. This is the turn serialization layer: a new turn of a task is
     * started only while holding the task's lock and after the previous turn of the task has fully ended, see
     * [SessionManager]. It is independent of the per-task stream lock inside [SessionManager], which only orders
     * the persisting and delivery of events.
     */
    protected val tasksMutex: KeyedMutex<String> = KeyedMutex()

    /**
     * Manages turns ([ai.koog.a2a.server.session.Session]s) and per-task event streams. Override to customize it.
     * The instance must use the same [taskStorage] and [coroutineScope] as this server.
     */
    protected open val sessionManager: SessionManager = SessionManager(
        coroutineScope = coroutineScope,
        taskStorage = taskStorage,
        pushConfigStorage = pushConfigStorage,
        pushSender = pushSender,
        idGenerator = idGenerator,
    )

    /**
     * Returns [agentCardExtended]. Unlike the other operations, this one doesn't run [checkRequiredExtensions]: the
     * client may need the extended card to learn which extensions to request.
     *
     * @throws A2AUnsupportedOperationException if the [agentCard] doesn't declare
     * [extendedAgentCard][ai.koog.a2a.model.AgentCapabilities.extendedAgentCard].
     * @throws A2AExtendedAgentCardNotConfiguredException if it is declared but [agentCardExtended] is `null`.
     */
    override suspend fun onGetExtendedAgentCard(
        request: GetExtendedAgentCardRequest,
        ctx: ServerCallContext
    ): AgentCard {
        if (agentCard.capabilities.extendedAgentCard != true) {
            throw A2AUnsupportedOperationException("Extended agent card is not supported")
        }

        // Default server implementation does not provide authorization, return extended card directly if present
        return agentCardExtended
            ?: throw A2AExtendedAgentCardNotConfiguredException("Extended agent card is supported but not configured on the server")
    }

    /**
     * Common logic for handling incoming messages: validates the request, starts a turn of the agent (one
     * [AgentExecutor.execute] call), and streams the events of that turn.
     *
     * Turns of the same task are serialized: the validation below runs, and the turn starts, only after the previous
     * turn of the task has ended. Errors are thrown before the first event is emitted:
     * - [A2AInvalidParamsException] if the message has no parts or
     *   [historyLength][ai.koog.a2a.model.SendMessageConfiguration.historyLength] is negative;
     * - [A2ATaskNotFoundException] if [Message.taskId] is set but the task doesn't exist, as seen by
     *   [TaskStorage.get] for a [GetTaskRequest] in the caller's [ServerCallContext];
     * - [A2AInvalidParamsException] if [Message.contextId] is set and differs from the referenced task's context id;
     * - [A2AUnsupportedOperationException] if the referenced task is in a terminal state.
     *
     * The context id of the turn is the referenced task's context id, else [Message.contextId], else a generated one.
     * For a referenced task, the agent can only send task events, see [SessionEventProcessor].
     *
     * The flow ends after the agent's [Message], after an event with a terminal task state, or when the turn ends,
     * whichever comes first. Only in the last case is an exception thrown by the agent rethrown (if the task exists,
     * the task is marked failed instead, and the flow ends with that update). An event of the agent rejected as invalid
     * (e.g. a status update before a [Task] for a new task, or a [Message] for an existing task) is reported as
     * [A2AInvalidAgentResponseException] in the same way (for an existing task the task is marked failed too, but
     * the requester gets the error instead of the failed update), also when the agent swallowed the failure and produced
     * nothing else. Once the flow has ended (or its collection
     * was stopped), the agent keeps running in [coroutineScope], and its later failures are not reported to the
     * requester.
     *
     * @param request The SendMessage request.
     * @param ctx The context of the call.
     * @param includeTaskSnapshot For a message to an existing task: whether to emit, before the agent's first event,
     * the task as stored right before the turn's first event (without the user message of the request, which is
     * persisted later, see [SessionManager]). This way the stream always begins with the task: a [Task] event of the
     * agent for an existing task is ignored, see [SessionEventProcessor]. If the turn produces no events, the flow
     * consists of the snapshot only.
     * @return A cold flow of the turn's events; each collection sends the message again.
     */
    @OptIn(InternalA2AApi::class)
    protected open fun onSendMessageCommon(
        request: SendMessageRequest,
        ctx: ServerCallContext,
        includeTaskSnapshot: Boolean = false,
    ): Flow<Event> = flow {
        val message = request.message

        if (message.parts.isEmpty()) {
            throw A2AInvalidParamsException("Empty message parts are not supported")
        }

        validateHistoryLength(request.configuration?.historyLength)

        val taskId = message.taskId ?: idGenerator.generateTaskId(message)

        val session = tasksMutex.withLock(taskId) {
            // If there's a currently running session for the same task, wait for it to finish.
            sessionManager.getSession(taskId)?.join()

            // Check the task the message refers to, if any. Read in full: the agent gets it as stored at turn start.
            val task: Task? = message.taskId?.let { referencedTaskId ->
                val lookup = GetTaskRequest(id = referencedTaskId, tenant = request.tenant)
                val task = taskStorage.get(lookup, ctx)
                    ?: throw A2ATaskNotFoundException("Task '$referencedTaskId' not found")

                if (message.contextId != null && task.contextId != null && message.contextId != task.contextId) {
                    throw A2AInvalidParamsException(
                        "Message contextId '${message.contextId}' doesn't match contextId '${task.contextId}' of task '$referencedTaskId'"
                    )
                }

                if (task.status.state.terminal) {
                    throw A2AUnsupportedOperationException(
                        "Task '$referencedTaskId' is in terminal state ${task.status.state} and can't accept messages"
                    )
                }

                task
            }

            val contextId = task?.contextId
                ?: message.contextId
                ?: idGenerator.generateContextId(message)

            val requestContext = newRequestContext(ctx, request, contextId, taskId, task)

            // The session's event view is registered before the agent starts, so no event of this turn is missed.
            sessionManager.startTurn(requestContext) { eventProcessor ->
                agentExecutor.execute(requestContext, eventProcessor)
            }
        }

        val snapshot = if (includeTaskSnapshot) session.taskSnapshot else null
        var isFirstEvent = true
        var isResponseComplete = false
        var isRejected = false

        // Stream the events of this turn, until the response is complete or the turn ends.
        emitAll(
            session.events.transformWhile { event ->
                if (event.isTerminal() && session.failedWithRejectedEvent()) {
                    // The task was marked failed because the agent's event was rejected: the requester gets the error.
                    isRejected = true
                    return@transformWhile false
                }

                if (isFirstEvent) {
                    isFirstEvent = false

                    if (snapshot != null) {
                        emit(snapshot)
                    }
                }

                emit(event)

                isResponseComplete = event is Message || event.isTerminal()
                !isResponseComplete
            }
        )

        if (isRejected || !isResponseComplete) {
            // The turn has ended, or the agent failed: propagate the exception thrown by the agent execution, if any.
            awaitAgent(session)
        }

        if (!isResponseComplete && !isRejected) {
            if (session.eventProcessor.rejection != null && isFirstEvent) {
                // The agent sent an invalid event, ignored the failure, and produced nothing else.
                throw A2AInvalidAgentResponseException("Agent sent an invalid event")
            }

            if (isFirstEvent && snapshot != null) {
                emit(snapshot)
            }
        }
    }

    private fun <T> newRequestContext(
        ctx: ServerCallContext,
        params: T,
        contextId: String,
        taskId: String,
        task: Task?,
    ): RequestContext<T> = RequestContext(
        callContext = ctx,
        params = params,
        taskStorage = ContextTaskStorage(contextId, taskStorage),
        messageStorage = ContextMessageStorage(contextId, messageStorage),
        contextId = contextId,
        taskId = taskId,
        task = task,
    )

    /**
     * Whether the agent job has already failed because an event of the agent was rejected ([InvalidEventException],
     * [TaskOperationException]).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Session.failedWithRejectedEvent(): Boolean {
        val failure = if (agentJob.isCompleted) agentJob.getCompletionExceptionOrNull() else null
        return failure is InvalidEventException || failure is TaskOperationException
    }

    /**
     * Waits for the agent job of an ended turn, rethrowing the exception the agent failed with. A cancelled agent job
     * (e.g. by a cancel request) is not an error of this request: only the cancellation of the caller itself is
     * propagated. An event of the agent that was rejected ([InvalidEventException], [TaskOperationException]) is
     * reported as [A2AInvalidAgentResponseException].
     */
    private suspend fun awaitAgent(session: Session) {
        try {
            session.agentJob.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
        } catch (e: InvalidEventException) {
            throw A2AInvalidAgentResponseException("Agent sent an invalid event: ${e.message}")
        } catch (e: TaskOperationException) {
            throw A2AInvalidAgentResponseException("Agent sent an event that was rejected by the server")
        }
    }

    /**
     * Handles SendMessage, see [onSendMessageCommon] for the validation and the turn.
     *
     * - If the agent replies with a [Message], returns it, whatever
     *   [returnImmediately][ai.koog.a2a.model.SendMessageConfiguration.returnImmediately] is.
     * - If `returnImmediately` is `true`, returns after the first task event of the turn.
     * - Otherwise (blocking, the default), returns at the first [Task] or [TaskStatusUpdateEvent] whose state is
     *   terminal or interrupted ([TaskState.TASK_STATE_INPUT_REQUIRED], [TaskState.TASK_STATE_AUTH_REQUIRED]), or when
     *   the turn ends, whichever comes first.
     *
     * A task result is read from [taskStorage] at that point, with the history limited to
     * [historyLength][ai.koog.a2a.model.SendMessageConfiguration.historyLength] and all artifacts. The agent keeps
     * running in [coroutineScope] after the response is returned.
     */
    override suspend fun onSendMessage(
        request: SendMessageRequest,
        ctx: ServerCallContext
    ): ResponseEvent {
        checkRequiredExtensions(ctx)

        val messageConfiguration = request.configuration
        val returnImmediately = messageConfiguration?.returnImmediately == true
        var lastTaskEvent: TaskEvent? = null

        // Reusing streaming logic here, stopping at the event the response is based on.
        val responseEvent = onSendMessageCommon(request, ctx).firstOrNull { event ->
            when (event) {
                is Message -> true

                is TaskEvent -> {
                    lastTaskEvent = event
                    returnImmediately || event.isTerminal() || event.isInterrupted()
                }
            }
        }

        if (responseEvent is Message) {
            return responseEvent
        }

        // A task event to respond with, or the turn has ended: respond with the task as stored.
        val taskId = lastTaskEvent?.taskId
            ?: request.message.taskId
            ?: throw IllegalStateException("Can't get response from the agent: event stream is empty")

        return taskStorage
            .get(
                taskId,
                historyLength = messageConfiguration?.historyLength,
                includeArtifacts = true
            )
            ?: throw A2ATaskNotFoundException("Task '$taskId' not found after the agent execution")
    }

    /**
     * Handles SendStreamingMessage, see [onSendMessageCommon] for the validation and the turn.
     *
     * The stream ends after the agent's [Message], at a terminal task state, or when the turn ends (e.g. at an
     * interrupted state), whichever comes first. A stream of a task begins with the [Task]: for a message to an
     * existing task, the task as stored before the turn's first event is emitted first, unless the agent starts the
     * turn with a [Task]. The history of that task is limited to
     * [historyLength][ai.koog.a2a.model.SendMessageConfiguration.historyLength].
     *
     * @throws A2AUnsupportedOperationException if streaming is not supported, before the first event
     */
    override fun onSendMessageStreaming(
        request: SendMessageRequest,
        ctx: ServerCallContext
    ): Flow<Event> = flow {
        checkRequiredExtensions(ctx)
        checkStreamingSupport()
        emitAll(onSendMessageCommon(request, ctx, includeTaskSnapshot = true))
    }

    private fun Event.isInterrupted(): Boolean = when (this) {
        is Task -> status.state in INTERRUPTED_STATES
        is TaskStatusUpdateEvent -> status.state in INTERRUPTED_STATES
        else -> false
    }

    override suspend fun onGetTask(
        request: GetTaskRequest,
        ctx: ServerCallContext
    ): Task {
        checkRequiredExtensions(ctx)
        validateHistoryLength(request.historyLength)

        return taskStorage.get(request, ctx)
            ?: throw A2ATaskNotFoundException("Task '${request.id}' not found")
    }

    override suspend fun onListTasks(
        request: ListTasksRequest,
        ctx: ServerCallContext
    ): ListTasksResponse {
        checkRequiredExtensions(ctx)

        request.pageSize?.let { pageSize ->
            if (pageSize !in MIN_LIST_TASKS_PAGE_SIZE..MAX_LIST_TASKS_PAGE_SIZE) {
                throw A2AInvalidParamsException(
                    "pageSize must be between $MIN_LIST_TASKS_PAGE_SIZE and $MAX_LIST_TASKS_PAGE_SIZE, but was $pageSize"
                )
            }
        }
        validateHistoryLength(request.historyLength)

        return taskStorage.list(request, ctx)
    }

    private fun validateHistoryLength(historyLength: Int?) {
        if (historyLength != null && historyLength < 0) {
            throw A2AInvalidParamsException("historyLength must be non-negative, but was $historyLength")
        }
    }

    /**
     * Cancels a task (CancelTask).
     *
     * - Throws [A2ATaskNotFoundException] if the task doesn't exist (or isn't visible to the caller, see
     *   [TaskStorage.get]), and [A2ATaskNotCancelableException] if it is in a terminal state already.
     * - Otherwise calls [AgentExecutor.cancel] with a processor bound to the task's event stream: the running turn's
     *   processor if a turn is running (the turn can't end until the cancellation is done, so its requester and all
     *   subscribers receive the cancellation events), or a standalone one for a task without a running turn, e.g. one
     *   waiting for input.
     * - If the task is still not in a terminal state after [AgentExecutor.cancel] returns, the server cancels the
     *   running agent job (if any) and forces the state [TaskState.TASK_STATE_CANCELED] with a status update event
     *   (with no status message), which is delivered to every stream of the task and closes them.
     *
     * The task is returned as stored (with artifacts, without history). [CancelTaskRequest] has no history limit.
     *
     * @throws A2ATaskNotCancelableException if the task is terminal, or the executor left it in a terminal state
     * other than [TaskState.TASK_STATE_CANCELED].
     */
    @OptIn(InternalA2AApi::class)
    override suspend fun onCancelTask(
        request: CancelTaskRequest,
        ctx: ServerCallContext
    ): Task {
        checkRequiredExtensions(ctx)

        val taskId = request.id

        // The owner-scoped lookup: a task that is not visible to the caller is reported as not found.
        val initialTask = taskStorage.get(GetTaskRequest(id = taskId, historyLength = 0, tenant = request.tenant), ctx)
            ?: throw A2ATaskNotFoundException("Task '$taskId' not found")

        /*
         Get a processor bound to the task's event stream, so that cancellation events reach every stream of the task. If
         a turn is running, it's the turn's processor, and the turn can't end until we're done. Otherwise, no turn can
         start until we're done, and a turn that is waiting to start is rejected if the task ends up canceled. The task
         lock isn't taken: a follow-up holding it may be waiting for the very turn being cancelled to end.
         */
        return sessionManager.withTaskProcessor(taskId, initialTask.contextId.orEmpty()) { eventProcessor, agentJob ->
            // Re-read the task: a turn may have ended in the meantime.
            val task = taskStorage.get(taskId, historyLength = 0, includeArtifacts = true)
                ?: throw A2ATaskNotFoundException("Task '$taskId' not found")

            if (task.status.state.terminal) {
                throw A2ATaskNotCancelableException("Task '$taskId' is already in terminal state ${task.status.state}")
            }

            val requestContext = newRequestContext(ctx, request, eventProcessor.contextId, eventProcessor.taskId, task)

            // Attempt to cancel the agent execution and wait until it's finished.
            agentExecutor.cancel(requestContext, eventProcessor, agentJob)

            // The executor may have left the task running: the server makes sure it ends up canceled.
            val afterCancel = taskStorage.get(taskId, historyLength = 0, includeArtifacts = false)
                ?: throw A2ATaskNotFoundException("Task '$taskId' not found")

            if (!afterCancel.status.state.terminal) {
                // Nothing may publish for the task after the forced cancellation.
                agentJob?.cancelAndJoin()

                val current = taskStorage.get(taskId, historyLength = 0, includeArtifacts = false)
                if (current != null && !current.status.state.terminal) {
                    eventProcessor.sendServerTaskEvent(
                        TaskStatusUpdateEvent(
                            taskId = taskId,
                            contextId = current.contextId ?: eventProcessor.contextId,
                            status = TaskStatus(
                                state = TaskState.TASK_STATE_CANCELED,
                                timestamp = Clock.System.now(),
                            ),
                        )
                    )
                }
            }

            // Return the final task state.
            taskStorage.get(taskId, historyLength = 0, includeArtifacts = true)
                ?.also {
                    if (it.status.state != TaskState.TASK_STATE_CANCELED) {
                        throw A2ATaskNotCancelableException("Task '$taskId' was not canceled successfully, current state is ${it.status.state}")
                    }
                }
                ?: throw A2ATaskNotFoundException("Task '$taskId' not found")
        }
    }

    /**
     * Streams the task snapshot followed by its live events until the task reaches a terminal state, see
     * [SessionManager.subscribe]. Works whether or not a turn of the task is running, e.g. for a task waiting for input.
     *
     * Errors are thrown before the first event: [A2AUnsupportedOperationException] if streaming is not supported or
     * the task is in a terminal state, [A2ATaskNotFoundException] if the task doesn't exist.
     */
    @OptIn(InternalA2AApi::class)
    override fun onSubscribeToTask(
        request: SubscribeToTaskRequest,
        ctx: ServerCallContext
    ): Flow<Event> = flow {
        checkRequiredExtensions(ctx)
        checkStreamingSupport()

        emitAll(sessionManager.subscribe(request, ctx))
    }

    override suspend fun onCreateTaskPushNotificationConfig(
        request: TaskPushNotificationConfig,
        ctx: ServerCallContext
    ): TaskPushNotificationConfig {
        checkRequiredExtensions(ctx)

        val pushStorage = storageIfPushNotificationSupported()
        val taskId = request.taskId?.takeIf { it.isNotBlank() }
            ?: throw A2AInvalidParamsException("Task id is required to create a push notification config")

        checkTaskExists(taskId, request.tenant, ctx)

        // The id may be set by the client; the server assigns one if it's absent.
        val config = if (request.id.isNullOrBlank()) {
            request.copy(id = idGenerator.generatePushNotificationConfigId(request))
        } else {
            request
        }

        return pushStorage.save(config, ctx)
    }

    override suspend fun onGetTaskPushNotificationConfig(
        request: GetTaskPushNotificationConfigRequest,
        ctx: ServerCallContext
    ): TaskPushNotificationConfig {
        checkRequiredExtensions(ctx)

        val pushStorage = storageIfPushNotificationSupported()
        checkTaskExists(request.taskId, request.tenant, ctx)

        return pushStorage.get(request, ctx)
            ?: throw A2ATaskNotFoundException(
                "Push notification config '${request.id}' not found for task '${request.taskId}'"
            )
    }

    override suspend fun onListTaskPushNotificationConfigs(
        request: ListTaskPushNotificationConfigsRequest,
        ctx: ServerCallContext
    ): ListTaskPushNotificationConfigsResponse {
        checkRequiredExtensions(ctx)

        val pushStorage = storageIfPushNotificationSupported()
        checkTaskExists(request.taskId, request.tenant, ctx)

        return pushStorage.list(request, ctx)
    }

    override suspend fun onDeleteTaskPushNotificationConfig(
        request: DeleteTaskPushNotificationConfigRequest,
        ctx: ServerCallContext
    ) {
        checkRequiredExtensions(ctx)

        val pushStorage = storageIfPushNotificationSupported()
        checkTaskExists(request.taskId, request.tenant, ctx)

        pushStorage.delete(request, ctx)
    }

    /**
     * Checks that the task exists and is visible to the caller, with the same owner-scoped lookup as GetTask, see
     * [TaskStorage.get].
     */
    private suspend fun checkTaskExists(taskId: String, tenant: String?, ctx: ServerCallContext) {
        taskStorage.get(GetTaskRequest(id = taskId, historyLength = 0, tenant = tenant), ctx)
            ?: throw A2ATaskNotFoundException("Task '$taskId' not found")
    }

    /**
     * Checks that the client requested all extensions that [agentCard] declares as required, called first in every
     * [RequestHandler] operation except [onGetExtendedAgentCard]. The extensions requested by the client are
     * [ServerCallContext.requestedExtensions]; requested extensions that the card doesn't declare are ignored.
     *
     * Only the public [agentCard] is consulted, not [agentCardExtended]. Override to customize the check,
     * e.g. to skip some operations.
     *
     * @throws A2AExtensionSupportRequiredException if some required extensions were not requested.
     */
    protected open fun checkRequiredExtensions(context: ServerCallContext) {
        val missing = agentCard.capabilities.extensions.orEmpty()
            .filter { it.required == true }
            .mapNotNull { it.uri }
            .filter { it !in context.requestedExtensions }

        if (missing.isNotEmpty()) {
            throw A2AExtensionSupportRequiredException(
                "Required extensions were not requested in ${A2AHeaders.A2A_EXTENSIONS}: ${missing.joinToString(", ")}"
            )
        }
    }

    /**
     * Checks that the [agentCard] declares [streaming][ai.koog.a2a.model.AgentCapabilities.streaming], called in
     * streaming operations after [checkRequiredExtensions] and before anything is emitted.
     *
     * @throws A2AUnsupportedOperationException if streaming is not declared or is `false`.
     */
    protected open fun checkStreamingSupport() {
        if (agentCard.capabilities.streaming != true) {
            throw A2AUnsupportedOperationException("Streaming is not supported by the server")
        }
    }

    /**
     * Checks that the [agentCard] declares [pushNotifications][ai.koog.a2a.model.AgentCapabilities.pushNotifications]
     * and that [pushConfigStorage] is configured, called in push notification config operations after
     * [checkRequiredExtensions].
     *
     * @return The configured push notification config storage.
     * @throws A2APushNotificationNotSupportedException if push notifications are not declared, or no storage is configured.
     */
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
