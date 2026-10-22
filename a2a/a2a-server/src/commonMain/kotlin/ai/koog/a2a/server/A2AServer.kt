package ai.koog.a2a.server

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.consts.A2AHeaders
import ai.koog.a2a.exceptions.A2AExtendedAgentCardNotConfiguredException
import ai.koog.a2a.exceptions.A2AExtensionSupportRequiredException
import ai.koog.a2a.exceptions.A2AInternalErrorException
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
import ai.koog.a2a.server.exceptions.TurnNotActiveException
import ai.koog.a2a.server.messages.ContextMessageStorage
import ai.koog.a2a.server.messages.InMemoryMessageStorage
import ai.koog.a2a.server.messages.MessageStorage
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.tasks.ContextTaskStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.tasks.isTerminal
import ai.koog.a2a.server.turn.IdGenerator
import ai.koog.a2a.server.turn.LifecycleState
import ai.koog.a2a.server.turn.RequestContext
import ai.koog.a2a.server.turn.ServerShutdownException
import ai.koog.a2a.server.turn.Turn
import ai.koog.a2a.server.turn.TurnEventProcessor
import ai.koog.a2a.server.turn.TurnManager
import ai.koog.a2a.server.turn.TurnOutcome
import ai.koog.a2a.server.turn.UuidIdGenerator
import ai.koog.a2a.server.turn.genericFailureMessage
import ai.koog.a2a.transport.A2AMethod
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlin.time.Clock
import kotlin.time.Duration

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
 * - Delegates event processing and persisting to [TurnEventProcessor]
 * - Delegates turn management to [TurnManager]
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
 *         eventProcessor: TurnEventProcessor
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
 *         eventProcessor: TurnEventProcessor,
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
 *         eventProcessor: TurnEventProcessor
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
 * @param pushConfigStorage Optional storage for push notification configurations (defaults to `null`). Push
 * notifications are enabled only if both it and [pushSender] are set.
 * @param pushSender Optional push notification sender implementation (defaults to `null`), see [pushConfigStorage].
 * @param idGenerator Generator for new task, context and push notification config IDs (defaults to [UuidIdGenerator])
 * @param coroutineScope Scope for managing all turns, agent jobs, event processing, etc. They run under a child
 * supervisor job of it, so it needn't be a supervisor: a failing agent doesn't cancel it (see [TurnManager]).
 * [shutdown] and [cancel] cancel only that child, not the scope. Cancelling the scope from outside stops them all and
 * shuts the server down as [cancel] does.
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
        private const val MIN_LIST_TASKS_PAGE_SIZE = 1
        private const val MAX_LIST_TASKS_PAGE_SIZE = 100

        /**
         * Non-terminal states in which the task waits for the client, ending a blocking SendMessage.
         */
        private val INTERRUPTED_STATES = setOf(TaskState.TASK_STATE_INPUT_REQUIRED, TaskState.TASK_STATE_AUTH_REQUIRED)

        /** The operations rejected while the server is shutting down: those that start turns or subscriptions. */
        private val REJECTED_WHILE_DRAINING =
            setOf(A2AMethod.SendMessage, A2AMethod.SendMessageStreaming, A2AMethod.SubscribeToTask)
    }

    /**
     * The server's internal turn layer, exposed to subclasses via opt-in.
     */
    @InternalA2AApi
    protected val turnManager: TurnManager = TurnManager(
        coroutineScope = coroutineScope,
        taskStorage = taskStorage,
        pushConfigStorage = pushConfigStorage,
        pushSender = pushSender,
        idGenerator = idGenerator,
        failureMessage = { taskId, contextId, error -> failureMessage(taskId, contextId, error) },
    )

    /**
     * Returns [agentCardExtended]. Unlike the other operations, by default it doesn't require the extensions the
     * [agentCard] declares as required, see [checkAccepting]: the client may need the extended card to learn which
     * extensions to request.
     *
     * @throws A2AUnsupportedOperationException if the [agentCard] doesn't declare
     * [extendedAgentCard][ai.koog.a2a.model.AgentCapabilities.extendedAgentCard].
     * @throws A2AExtendedAgentCardNotConfiguredException if it is declared but [agentCardExtended] is `null`.
     */
    override suspend fun onGetExtendedAgentCard(
        request: GetExtendedAgentCardRequest,
        ctx: ServerCallContext
    ): AgentCard {
        checkAccepting(A2AMethod.GetExtendedAgentCard, ctx)

        if (agentCard.capabilities.extendedAgentCard != true) {
            throw A2AUnsupportedOperationException("Extended agent card is not supported")
        }

        // No authorization in the default implementation: return the card if configured.
        return agentCardExtended
            ?: throw A2AExtendedAgentCardNotConfiguredException("Extended agent card is supported but not configured on the server")
    }

    /**
     * Common logic for handling incoming messages: validates the request, starts a turn of the agent (one
     * [AgentExecutor.execute] call), and streams the events of that turn.
     *
     * The turn starts only after the previous turn of the same task has ended, and the referenced task is validated
     * then; the message and the inline push notification config are validated before waiting. Errors are thrown
     * before the first event is emitted:
     * - [A2AInvalidParamsException] if the message has no parts, `historyLength` is negative, or [Message.contextId]
     *   differs from the referenced task's context id;
     * - [A2ATaskNotFoundException] if [Message.taskId] is set but the task isn't visible to the caller, see [TaskStorage.get];
     * - [A2AUnsupportedOperationException] if the referenced task is in a terminal state.
     *
     * The turn's context id is the referenced task's, else [Message.contextId], else a generated one. For a
     * referenced task, the agent can only send task events, see [TurnEventProcessor].
     *
     * The inline [taskPushNotificationConfig][ai.koog.a2a.model.SendMessageConfiguration.taskPushNotificationConfig]
     * is saved for the turn's task when the turn starts, before the agent does, so the turn's first task event is
     * already pushed to it:
     * - its `taskId` and `tenant` are set to the turn's task and the request's tenant;
     * - without an id, the task id is used, so re-sending the same config with every message replaces it;
     * - its `url` must not be blank, and it is checked with [validatePushNotificationConfig] (before the referenced
     *   task is); if either fails, nothing is saved, no turn starts, and the request fails with
     *   [A2AInvalidParamsException] (or the validation's exception);
     * - it is silently ignored if the [agentCard] doesn't declare
     *   [pushNotifications][ai.koog.a2a.model.AgentCapabilities.pushNotifications], or [pushConfigStorage] or
     *   [pushSender] is `null`.
     *
     * If the turn doesn't start (e.g. the referenced task is terminal), the inline config isn't saved. If a turn
     * that would create a new task ends without a stored task (e.g. the agent replied with a [Message], or never ran
     * because the server was shut down meanwhile), the inline config saved for it is deleted.
     *
     * The flow ends after the agent's [Message], after an event with a terminal task state (e.g. the CANCELED of a
     * concurrent CancelTask, even if the agent then fails), or when the turn ends. If the server is shut down before
     * (see [shutdown]), the flow fails with [A2AInternalErrorException] "Server is shut down" instead, and the agent is
     * cancelled. Only when the turn ends is the agent's exception rethrown:
     * - an [ai.koog.a2a.exceptions.A2AException] thrown before the agent's first event of the turn is rethrown and
     *   leaves the task unchanged; the task snapshot isn't emitted;
     * - any other exception is rethrown if the task doesn't exist; otherwise the task is marked failed and the flow
     *   ends with that update.
     *
     * An event rejected as invalid (e.g. a status update before a [Task] for a new task, or a [Message] for an existing
     * task) is reported the same way as [A2AInvalidAgentResponseException], also when the agent swallowed the failure
     * and produced nothing else; for an existing task the requester gets the error instead of the failed update. A turn
     * of a new task that ends without any event and without a failure is also reported as
     * [A2AInvalidAgentResponseException]. After the flow ends or its collection stops, the agent keeps running in
     * [coroutineScope] and its later failures are not reported to the requester.
     *
     * @param request The SendMessage request.
     * @param ctx The context of the call.
     * @param includeTaskSnapshot For a message to an existing task: whether to emit the task as stored right before
     * the turn's first event (without the request's user message, which is persisted later) ahead of the agent's first
     * event, so the stream always begins with the task. If the turn produces no events and the agent doesn't fail, the
     * flow is the snapshot only.
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
        val inlinePushConfig = resolveInlinePushConfig(request, taskId, ctx)

        /*
         Waits for the previous turn of the task, then validates a referenced task, saves the inline config, registers
         the event view, and starts the agent, so no event is missed. The agent gets the referenced task as read then.
         */
        val turn = turnManager.startTurn(request, ctx, taskId, inlinePushConfig) { task, eventProcessor ->
            val requestContext = newRequestContext(ctx, request, eventProcessor.contextId, eventProcessor.taskId, task)
            agentExecutor.execute(requestContext, eventProcessor)
        }

        // The view must not outlive this flow: an uncollected view blocks every delivery to the task.
        try {
            failOnShutdown { emitTurnEvents(turn, includeTaskSnapshot, isNewTask = message.taskId == null) }
        } finally {
            turn.cancelEvents()
        }
    }

    /**
     * Runs [block], which collects a turn view or a subscription, replacing the [ServerShutdownException] it fails with
     * when the server shuts down with the shutdown error. A collector cancelled itself (e.g. the client went away) stays
     * cancelled.
     */
    private suspend inline fun failOnShutdown(block: () -> Unit) {
        try {
            block()
        } catch (e: ServerShutdownException) {
            currentCoroutineContext().ensureActive()
            throw e.toA2AException()
        }
    }

    /** Emits the events of [turn] for [onSendMessageCommon]. */
    @OptIn(InternalA2AApi::class)
    private suspend fun FlowCollector<Event>.emitTurnEvents(turn: Turn, includeTaskSnapshot: Boolean, isNewTask: Boolean) {
        val snapshot = if (includeTaskSnapshot) turn.taskSnapshot else null
        var isFirstEvent = true
        var isResponseComplete = false

        emitAll(
            turn.events.transformWhile { event ->
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

        if (isResponseComplete) return

        // The turn has ended (or the task became terminal without the update reaching this view): respond by its outcome.
        when (val outcome = turn.outcome.await()) {
            is TurnOutcome.Failed -> throw outcome.cause

            is TurnOutcome.RequestError -> throw outcome.cause

            is TurnOutcome.Rejected -> throw when (outcome.cause) {
                is InvalidEventException -> A2AInvalidAgentResponseException("Agent sent an invalid event: ${outcome.cause.message}")
                else -> A2AInvalidAgentResponseException("Agent sent an event that was rejected by the server")
            }

            is TurnOutcome.Completed -> if (isFirstEvent) {
                when {
                    // The agent sent an invalid event, ignored the failure, and produced nothing else.
                    outcome.rejection != null -> throw A2AInvalidAgentResponseException("Agent sent an invalid event")

                    // Neither a Message nor a Task: there is nothing to respond with.
                    isNewTask -> throw A2AInvalidAgentResponseException("Agent ended the turn without sending any event")

                    snapshot != null -> emit(snapshot)
                }
            }
        }
    }

    /**
     * Resolves and validates the inline push notification config of [request] for [taskId], see [onSendMessageCommon].
     * [TurnManager.startTurn] saves it once the turn starts.
     *
     * @return the config to save, or `null` if there is none or it is ignored.
     */
    private suspend fun resolveInlinePushConfig(
        request: SendMessageRequest,
        taskId: String,
        ctx: ServerCallContext,
    ): TaskPushNotificationConfig? {
        val inline = request.configuration?.taskPushNotificationConfig ?: return null

        if (agentCard.capabilities.pushNotifications != true || pushConfigStorage == null || pushSender == null) {
            return null
        }

        checkPushUrl(inline)

        val id = inline.id?.takeIf { it.isNotBlank() } ?: taskId
        val config = inline.copy(taskId = taskId, id = id, tenant = request.tenant)

        validatePushNotificationConfig(config, ctx)

        return config
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
     * Handles SendMessage, see [onSendMessageCommon] for the validation and the turn.
     *
     * - If the agent replies with a [Message], returns it, whatever
     *   [returnImmediately][ai.koog.a2a.model.SendMessageConfiguration.returnImmediately] is.
     * - If `returnImmediately` is `true`, returns after the first task event of the turn.
     * - Otherwise (blocking, the default), returns at the first [Task] or [TaskStatusUpdateEvent] whose state is
     *   terminal or interrupted ([TaskState.TASK_STATE_INPUT_REQUIRED], [TaskState.TASK_STATE_AUTH_REQUIRED]), or when
     *   the turn ends, whichever comes first. If the server is shut down before, fails with
     *   [A2AInternalErrorException] "Server is shut down".
     *
     * A task result is read from [taskStorage] at that point like GetTask (with [ctx] and the request's tenant), with
     * the history limited to [historyLength][ai.koog.a2a.model.SendMessageConfiguration.historyLength] and all
     * artifacts. The agent keeps running in [coroutineScope] after the response is returned.
     *
     * An inline push notification config is saved for the turn's task, see [onSendMessageCommon].
     */
    override suspend fun onSendMessage(
        request: SendMessageRequest,
        ctx: ServerCallContext
    ): ResponseEvent {
        checkAccepting(A2AMethod.SendMessage, ctx)

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
        val taskId = checkNotNull(lastTaskEvent?.taskId ?: request.message.taskId) {
            "Unreachable: a turn of a new task ended without any event, which onSendMessageCommon reports as an error"
        }

        // Read for the caller, like GetTask.
        val lookup = GetTaskRequest(id = taskId, historyLength = messageConfiguration?.historyLength, tenant = request.tenant)

        return taskStorage.get(lookup, ctx)
            ?: throw A2ATaskNotFoundException("Task '$taskId' not found after the agent execution")
    }

    /**
     * Handles SendStreamingMessage, see [onSendMessageCommon] for the validation and the turn.
     *
     * The stream ends after the agent's [Message], at a terminal task state, or when the turn ends (e.g. at an
     * interrupted state), whichever comes first. If the server is shut down before, its last event is the error
     * [A2AInternalErrorException] "Server is shut down". A stream of a task begins with the [Task]: for a message to an
     * existing task, the task as stored before the turn's first event is emitted first. The history of that task is
     * limited to
     * [historyLength][ai.koog.a2a.model.SendMessageConfiguration.historyLength].
     *
     * An inline push notification config is saved for the turn's task, see [onSendMessageCommon].
     *
     * @throws A2AUnsupportedOperationException if streaming is not supported, before the first event
     */
    override fun onSendMessageStreaming(
        request: SendMessageRequest,
        ctx: ServerCallContext
    ): Flow<Event> = flow {
        checkAccepting(A2AMethod.SendMessageStreaming, ctx)
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
        checkAccepting(A2AMethod.GetTask, ctx)
        validateHistoryLength(request.historyLength)

        return taskStorage.get(request, ctx)
            ?: throw A2ATaskNotFoundException("Task '${request.id}' not found")
    }

    override suspend fun onListTasks(
        request: ListTasksRequest,
        ctx: ServerCallContext
    ): ListTasksResponse {
        checkAccepting(A2AMethod.ListTasks, ctx)

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
     *   (with no status message). It is pushed (if push notifications are configured) and delivered to every stream
     *   of the task, closing them. It doesn't add the user message of a cancelled follow-up turn to the history.
     *
     * The task is returned as stored (with artifacts, without history). [CancelTaskRequest] has no history limit.
     *
     * @throws A2ATaskNotCancelableException if the task is terminal, or ends up in a terminal state other than
     * [TaskState.TASK_STATE_CANCELED] (set by the executor, or by the running agent racing the cancellation).
     */
    @OptIn(InternalA2AApi::class)
    override suspend fun onCancelTask(
        request: CancelTaskRequest,
        ctx: ServerCallContext
    ): Task {
        checkAccepting(A2AMethod.CancelTask, ctx)

        val taskId = request.id

        /*
         Get a processor bound to the task's event stream, so that cancellation events reach every stream of the task. If
         a turn is running, it's the turn's processor, and the turn can't end until we're done. Otherwise, no turn can
         start until we're done, and a turn that is waiting to start is rejected if the task ends up canceled. Unlike
         startTurn, this doesn't wait for the previous turn to end: it may be the very turn being cancelled.
         */
        return turnManager.withTaskProcessor(
            // The owner-scoped lookup: a task that is not visible to the caller is reported as not found.
            GetTaskRequest(id = taskId, historyLength = 0, tenant = request.tenant),
            ctx,
        ) { task, eventProcessor, agentJob, publish ->
            if (task.status.state.terminal) {
                throw A2ATaskNotCancelableException("Task '$taskId' is already in terminal state ${task.status.state}")
            }

            val requestContext = newRequestContext(ctx, request, eventProcessor.contextId, eventProcessor.taskId, task)

            // A running agent may end the task after it was read, closing the turn's processor: report that state below.
            val processorClosed = try {
                agentExecutor.cancel(requestContext, eventProcessor, agentJob)
                null
            } catch (e: TurnNotActiveException) {
                e
            }

            var current = taskStorage.get(taskId, historyLength = 0, includeArtifacts = true)

            if (processorClosed != null && (current == null || !current.status.state.terminal)) throw processorClosed
            if (current == null) throw A2ATaskNotFoundException("Task '$taskId' not found")

            // The executor may have left the task running: force it to canceled.
            if (!current.status.state.terminal) {
                // Without an agent, nothing else publishes under the gate, so the task read above is current.
                if (agentJob != null) {
                    // Nothing may publish for the task after the forced cancellation.
                    agentJob.cancelAndJoin()
                    // The agent may have ended the task before it stopped.
                    current = taskStorage.get(taskId, historyLength = 0, includeArtifacts = true)
                        ?: throw A2ATaskNotFoundException("Task '$taskId' not found")
                }

                if (!current.status.state.terminal) {
                    // Straight to the task stream: a running follow-up's user message isn't persisted by the server.
                    publish(
                        TaskStatusUpdateEvent(
                            taskId = taskId,
                            contextId = current.contextId ?: eventProcessor.contextId,
                            status = TaskStatus(
                                state = TaskState.TASK_STATE_CANCELED,
                                timestamp = Clock.System.now(),
                            ),
                        )
                    )

                    // Read back rather than built: the result is the task as stored.
                    current = taskStorage.get(taskId, historyLength = 0, includeArtifacts = true)
                        ?: throw A2ATaskNotFoundException("Task '$taskId' not found")
                }
            }

            if (current.status.state != TaskState.TASK_STATE_CANCELED) {
                throw A2ATaskNotCancelableException("Task '$taskId' was not canceled successfully, current state is ${current.status.state}")
            }

            current
        }
    }

    /**
     * Streams the task snapshot followed by its live events until the task reaches a terminal state, see
     * [TurnManager.subscribe]. Works whether or not a turn of the task is running, e.g. for a task waiting for input.
     * If the server is shut down before (see [shutdown]), the stream's last event is the error
     * [A2AInternalErrorException] "Server is shut down"; during a graceful shutdown the stream stays open until then.
     *
     * Errors are thrown before the first event: [A2AUnsupportedOperationException] if streaming is not supported or
     * the task is in a terminal state, [A2ATaskNotFoundException] if the task doesn't exist.
     */
    @OptIn(InternalA2AApi::class)
    override fun onSubscribeToTask(
        request: SubscribeToTaskRequest,
        ctx: ServerCallContext
    ): Flow<Event> = flow {
        checkAccepting(A2AMethod.SubscribeToTask, ctx)
        checkStreamingSupport()

        failOnShutdown { emitAll(turnManager.subscribe(request, ctx)) }
    }

    /**
     * Saves a push notification config for an existing task (CreateTaskPushNotificationConfig). Without an id, the
     * id is generated with [IdGenerator.generatePushNotificationConfigId]. The config's `url` must not be blank, and
     * the config is checked with [validatePushNotificationConfig] before it is saved.
     *
     * @throws A2APushNotificationNotSupportedException if push notifications are not supported, see
     * [storageIfPushNotificationSupported].
     * @throws A2AInvalidParamsException if the task id is missing or the url is blank.
     * @throws A2ATaskNotFoundException if the task doesn't exist or isn't visible to the caller.
     */
    override suspend fun onCreateTaskPushNotificationConfig(
        request: TaskPushNotificationConfig,
        ctx: ServerCallContext
    ): TaskPushNotificationConfig {
        checkAccepting(A2AMethod.CreateTaskPushNotificationConfig, ctx)

        val pushStorage = storageIfPushNotificationSupported()
        val taskId = request.taskId?.takeIf { it.isNotBlank() }
            ?: throw A2AInvalidParamsException("Task id is required to create a push notification config")
        checkPushUrl(request)

        checkTaskExists(taskId, request.tenant, ctx)

        // The id may be set by the client; the server assigns one if it's absent.
        val config = if (request.id.isNullOrBlank()) {
            request.copy(id = idGenerator.generatePushNotificationConfigId(request))
        } else {
            request
        }

        validatePushNotificationConfig(config, ctx)

        return pushStorage.save(config, ctx)
    }

    override suspend fun onGetTaskPushNotificationConfig(
        request: GetTaskPushNotificationConfigRequest,
        ctx: ServerCallContext
    ): TaskPushNotificationConfig {
        checkAccepting(A2AMethod.GetTaskPushNotificationConfig, ctx)

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
        checkAccepting(A2AMethod.ListTaskPushNotificationConfigs, ctx)

        val pushStorage = storageIfPushNotificationSupported()
        checkTaskExists(request.taskId, request.tenant, ctx)

        return pushStorage.list(request, ctx)
    }

    override suspend fun onDeleteTaskPushNotificationConfig(
        request: DeleteTaskPushNotificationConfigRequest,
        ctx: ServerCallContext
    ) {
        checkAccepting(A2AMethod.DeleteTaskPushNotificationConfig, ctx)

        val pushStorage = storageIfPushNotificationSupported()
        checkTaskExists(request.taskId, request.tenant, ctx)

        pushStorage.delete(request, ctx)
    }

    /**
     * Builds the status message of a task the server marks as
     * [TASK_STATE_FAILED][ai.koog.a2a.model.TaskState.TASK_STATE_FAILED] because its turn failed: the agent threw, or
     * one of its events was rejected as invalid. Not called when the agent returned or its job was cancelled (e.g. by
     * CancelTask), or for an [ai.koog.a2a.exceptions.A2AException] thrown before the turn published any event, which
     * leaves the task unchanged.
     *
     * Called after the agent's job has completed and before the turn ends, holding no lock, so it may suspend (e.g.
     * for I/O). Keep it short: until it returns, the turn hasn't ended, so the task's queued follow-ups and anyone
     * waiting for the turn wait too. It is cancellable, e.g. by [cancel]. A CancelTask may run meanwhile, and the
     * message is unused if the task is then terminal or doesn't exist.
     *
     * The server sets the returned message's task id, context id, and role to the task's and
     * [ROLE_AGENT][ai.koog.a2a.model.Role.ROLE_AGENT], keeping its message id, parts, and metadata. If this throws (the
     * error is logged) or is cancelled, the default message is used.
     *
     * By default, returns a generic agent message, "Task failed due to an internal error", with an id from
     * [IdGenerator.generateMessageId]. Override to customize it, e.g. to map known exceptions to user-facing text;
     * clients see the message, so don't put exception details in it unless they are meant for them.
     *
     * @param taskId The id of the failed task.
     * @param contextId The context id of the turn.
     * @param error The exception the agent's job failed with.
     * @return The status message of the failed task.
     */
    protected open suspend fun failureMessage(taskId: String, contextId: String, error: Throwable): Message =
        idGenerator.genericFailureMessage(taskId, contextId)

    /**
     * Checks a push notification config before it is saved, both from CreateTaskPushNotificationConfig and inline in
     * SendMessage. [config] has its task id and id set. Does nothing by default.
     *
     * An inline config is checked before the referenced task is, so this may be called for a SendMessage that then
     * fails (e.g. the task is unknown, terminal, or in another context); the config isn't saved then.
     *
     * Override to reject configs, e.g. webhook URLs outside an allow-list to prevent SSRF.
     *
     * @param config The config to be saved.
     * @param ctx The context of the call.
     * @throws A2AInvalidParamsException (or another A2A exception) to reject the config; the request fails.
     */
    protected open suspend fun validatePushNotificationConfig(
        config: TaskPushNotificationConfig,
        ctx: ServerCallContext,
    ) {
        // No validation by default.
    }

    /** The `url` is required by the protocol. */
    private fun checkPushUrl(config: TaskPushNotificationConfig) {
        if (config.url.isBlank()) {
            throw A2AInvalidParamsException("Push notification config url is required")
        }
    }

    /** Checks that the task is visible to the caller, with the same owner-scoped lookup as GetTask. */
    private suspend fun checkTaskExists(taskId: String, tenant: String?, ctx: ServerCallContext) {
        taskStorage.get(GetTaskRequest(id = taskId, historyLength = 0, tenant = tenant), ctx)
            ?: throw A2ATaskNotFoundException("Task '$taskId' not found")
    }

    /**
     * Checks that the server accepts the operation [method], called first in every [RequestHandler] operation, before
     * any validation and holding no lock. In order:
     * - **Shutdown.** Once the server is shut down (see [shutdown]), every operation fails with
     *   [A2AInternalErrorException] "Server is shut down". While the server is shutting down, SendMessage,
     *   SendStreamingMessage, and SubscribeToTask fail with [A2AInternalErrorException] "Server is shutting down"; the
     *   other operations are accepted.
     * - **Required extensions.** Except for [A2AMethod.GetExtendedAgentCard], the client must request every extension
     *   that [agentCard] (not [agentCardExtended]) declares as required. The extensions requested by the client are
     *   [ServerCallContext.requestedExtensions]; requested extensions that the card doesn't declare are ignored.
     *
     * Override to add checks or to relax the extension requirement for some operations. Call `super` to keep the
     * shutdown check and the extension check. An override that doesn't still can't start a turn after shutdown:
     * [TurnManager.startTurn] checks that too, after the request's validation.
     *
     * @param method The operation being called.
     * @param ctx The context of the call.
     * @throws A2AInternalErrorException if the server is shut down, or shutting down and [method] isn't accepted then.
     * @throws A2AExtensionSupportRequiredException if some required extensions were not requested.
     */
    @OptIn(InternalA2AApi::class)
    protected open fun checkAccepting(method: A2AMethod, ctx: ServerCallContext) {
        when (val state = turnManager.lifecycleState) {
            LifecycleState.RUNNING -> Unit
            LifecycleState.DRAINING -> if (method in REJECTED_WHILE_DRAINING) state.checkAccepting()
            LifecycleState.SHUT_DOWN -> state.checkAccepting()
        }

        if (method == A2AMethod.GetExtendedAgentCard) return

        val missing = agentCard.capabilities.extensions.orEmpty()
            .filter { it.required == true }
            .mapNotNull { it.uri }
            .filter { it !in ctx.requestedExtensions }

        if (missing.isNotEmpty()) {
            throw A2AExtensionSupportRequiredException(
                "Required extensions were not requested in ${A2AHeaders.A2A_EXTENSIONS}: ${missing.joinToString(", ")}"
            )
        }
    }

    /**
     * Checks that the [agentCard] declares [streaming][ai.koog.a2a.model.AgentCapabilities.streaming], called in
     * streaming operations after [checkAccepting] and before anything is emitted.
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
     * and that both [pushConfigStorage] and [pushSender] are configured, called in push notification config operations
     * after [checkAccepting]. Without a sender, saved configs would never be notified.
     *
     * @return The configured push notification config storage.
     * @throws A2APushNotificationNotSupportedException if push notifications are not declared, or the storage or the
     * sender is not configured.
     */
    protected open fun storageIfPushNotificationSupported(): PushNotificationConfigStorage {
        if (agentCard.capabilities.pushNotifications != true) {
            throw A2APushNotificationNotSupportedException("Push notifications are not supported by the server")
        }

        if (pushConfigStorage == null || pushSender == null) {
            throw A2APushNotificationNotSupportedException("Push notifications are supported, but not configured on the server")
        }

        return pushConfigStorage
    }

    /**
     * Shuts the server down gracefully, waiting at most [gracePeriod] for the work in flight:
     * 1. From now on, SendMessage, SendStreamingMessage, and SubscribeToTask fail with [A2AInternalErrorException]
     *    "Server is shutting down", including a message that waits for the running turn of its task; the other
     *    operations are accepted, see [checkAccepting].
     * 2. Waits until every running turn ([AgentExecutor.execute] call) has ended, its requester getting its response as
     *    usual, then until every pending push notification has been sent. Open SubscribeToTask streams stay open and
     *    receive the events meanwhile; they aren't waited for.
     * 3. Then, or once [gracePeriod] has passed, whichever comes first, shuts down as [cancel] does: the turns still
     *    running are cut, the remaining push notifications are dropped, and the open streams end.
     *
     * Returns once the server is shut down. Idempotent: concurrent and repeated calls wait for the same shutdown, and
     * the earliest deadline wins, so a call with a shorter [gracePeriod] shuts down sooner, while a longer one never
     * delays it. [cancel] (or cancelling [coroutineScope]) shuts down at once, and the waiting calls return. If the
     * calling coroutine is cancelled while waiting, the server shuts down at once, as by [cancel], before the
     * [CancellationException] is rethrown.
     *
     * An agent that ignores cancellation may keep running after that; what the shutdown itself may still wait for is
     * described in [cancel].
     *
     * @param gracePeriod How long to wait for the running turns and push notifications. [Duration.ZERO] (the default),
     * or a negative one, shuts down at once, as [cancel] does.
     */
    @OptIn(InternalA2AApi::class)
    public open suspend fun shutdown(gracePeriod: Duration = Duration.ZERO) {
        turnManager.shutdownGracefully(gracePeriod)
    }

    /**
     * Shuts the server down immediately, without waiting, i.e. [shutdown] without a grace period:
     * - every later request fails with [A2AInternalErrorException] "Server is shut down", see [checkAccepting];
     * - every open stream (SendStreamingMessage, SubscribeToTask) that hasn't delivered a terminal state of its task
     *   yet ends with that error as its last event, and every waiting blocking SendMessage fails with it;
     * - the running agents are cancelled and their tasks keep their last persisted state; pending push notifications
     *   are dropped.
     *
     * The streams are ended before the agents are cancelled, so a stalled client can't delay the shutdown. What the
     * server's jobs may still wait for is non-cancellable storage I/O and a running [AgentExecutor.cancel], see
     * [TurnManager].
     *
     * Cancels only the server's own jobs, not [coroutineScope]. Idempotent; a waiting [shutdown] returns. See
     * [TurnManager.shutdown].
     *
     * @param cause Optional cause of the cancellation of the server's jobs.
     */
    @OptIn(InternalA2AApi::class)
    public open fun cancel(cause: CancellationException? = null) {
        turnManager.shutdown(cause)
    }
}
