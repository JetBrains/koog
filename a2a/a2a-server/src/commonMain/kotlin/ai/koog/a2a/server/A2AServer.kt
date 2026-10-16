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
import ai.koog.a2a.server.turn.RequestContext
import ai.koog.a2a.server.turn.Turn
import ai.koog.a2a.server.turn.TurnEventProcessor
import ai.koog.a2a.server.turn.TurnManager
import ai.koog.a2a.server.turn.UuidIdGenerator
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.agents.lock.KeyedMutex
import ai.koog.agents.lock.withLock
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext
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
 * @param coroutineScope Scope for managing all turns, agent jobs, event processing, etc.
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
     * Per-task lock that serializes turns: a new turn starts only while holding it, after the previous turn of the
     * task has ended. Independent of the stream lock inside [TurnManager], which only orders event persisting and delivery.
     */
    protected val tasksMutex: KeyedMutex<String> = KeyedMutex()

    /**
     * Manages [Turn]s and per-task event streams. Must use the same [taskStorage] and [coroutineScope] as this server.
     */
    protected open val turnManager: TurnManager = TurnManager(
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

        // No authorization in the default implementation: return the card if configured.
        return agentCardExtended
            ?: throw A2AExtendedAgentCardNotConfiguredException("Extended agent card is supported but not configured on the server")
    }

    /**
     * Common logic for handling incoming messages: validates the request, starts a turn of the agent (one
     * [AgentExecutor.execute] call), and streams the events of that turn.
     *
     * Validation runs, and the turn starts, only after the previous turn of the same task has ended. Errors are
     * thrown before the first event is emitted:
     * - [A2AInvalidParamsException] if the message has no parts, `historyLength` is negative, or [Message.contextId]
     *   differs from the referenced task's context id;
     * - [A2ATaskNotFoundException] if [Message.taskId] is set but the task isn't visible to the caller, see [TaskStorage.get];
     * - [A2AUnsupportedOperationException] if the referenced task is in a terminal state.
     *
     * The turn's context id is the referenced task's, else [Message.contextId], else a generated one. For a
     * referenced task, the agent can only send task events, see [TurnEventProcessor].
     *
     * The inline [taskPushNotificationConfig][ai.koog.a2a.model.SendMessageConfiguration.taskPushNotificationConfig]
     * is saved for the turn's task after validation and before the turn starts, so the turn's first task event is
     * already pushed to it:
     * - its `taskId` and `tenant` are set to the turn's task and the request's tenant;
     * - without an id, the task id is used, so re-sending the same config with every message replaces it;
     * - its `url` must not be blank, and it is checked with [validatePushNotificationConfig]; if either fails, nothing
     *   is saved, no turn starts, and the request fails with [A2AInvalidParamsException] (or the validation's exception);
     * - it is silently ignored if the [agentCard] doesn't declare
     *   [pushNotifications][ai.koog.a2a.model.AgentCapabilities.pushNotifications], or [pushConfigStorage] or
     *   [pushSender] is `null`.
     *
     * If a turn that would create a new task ends without a stored task (e.g. the agent replied with a [Message]),
     * the inline config saved for it is deleted. If a follow-up's turn doesn't start (e.g. the task became terminal
     * meanwhile), its inline config is deleted unless it replaced an existing config with the same id.
     *
     * The flow ends after the agent's [Message], after an event with a terminal task state, or when the turn ends.
     * Only in the last case is the agent's exception rethrown (if the task exists, it is marked failed instead and the
     * flow ends with that update). An event rejected as invalid (e.g. a status update before a [Task] for a new task,
     * or a [Message] for an existing task) is reported the same way as [A2AInvalidAgentResponseException], also when
     * the agent swallowed the failure and produced nothing else; for an existing task the requester gets the error
     * instead of the failed update. A turn of a new task that ends without any event and without a failure is also
     * reported as [A2AInvalidAgentResponseException]. After the flow ends or its collection stops, the agent keeps running in
     * [coroutineScope] and its later failures are not reported to the requester.
     *
     * @param request The SendMessage request.
     * @param ctx The context of the call.
     * @param includeTaskSnapshot For a message to an existing task: whether to emit the task as stored right before
     * the turn's first event (without the request's user message, which is persisted later) ahead of the agent's first
     * event, so the stream always begins with the task. If the turn produces no events, the flow is the snapshot only.
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

        val turn = tasksMutex.withLock(taskId) {
            // Wait for the running turn of the same task, if any.
            turnManager.getTurn(taskId)?.join()

            // The agent gets the referenced task as stored at turn start, so read it in full.
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

            // Saved before the turn starts, so that the turn's first task event is pushed to it.
            val inlinePushConfig = saveInlinePushConfig(request, taskId, ctx, isFollowUp = task != null)
            // Never for a follow-up: the task exists and may have other configs.
            val cleansUpPushConfigs = inlinePushConfig != null && task == null

            try {
                // The event view is registered before the agent starts, so no event is missed.
                turnManager.startTurn(requestContext) { eventProcessor ->
                    try {
                        agentExecutor.execute(requestContext, eventProcessor)
                    } finally {
                        if (cleansUpPushConfigs) {
                            withContext(NonCancellable) { deletePushConfigsIfTaskAbsent(taskId) }
                        }
                    }
                }
            } catch (e: Throwable) {
                if (cleansUpPushConfigs) {
                    withContext(NonCancellable) { deletePushConfigsIfTaskAbsent(taskId) }
                } else if (inlinePushConfig?.isNew == true) {
                    // The follow-up's turn didn't start (e.g. a concurrent cancel made the task terminal).
                    withContext(NonCancellable) { deleteInlinePushConfig(inlinePushConfig, request.tenant, ctx) }
                }
                throw e
            }
        }

        // The view must not outlive this flow: an uncollected view blocks every delivery to the task.
        try {
            emitTurnEvents(turn, includeTaskSnapshot, isNewTask = message.taskId == null)
        } finally {
            turn.cancelEvents()
        }
    }

    /** Emits the events of [turn] for [onSendMessageCommon]. */
    private suspend fun FlowCollector<Event>.emitTurnEvents(turn: Turn, includeTaskSnapshot: Boolean, isNewTask: Boolean) {
        val snapshot = if (includeTaskSnapshot) turn.taskSnapshot else null
        var isFirstEvent = true
        var isResponseComplete = false
        var isRejected = false

        emitAll(
            turn.events.transformWhile { event ->
                if (event.isTerminal() && turn.failedWithRejectedEvent()) {
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
            awaitAgent(turn)
        }

        if (!isResponseComplete && !isRejected && isFirstEvent) {
            // The turn has ended without any event.
            when {
                // The agent sent an invalid event, ignored the failure, and produced nothing else.
                turn.eventProcessor.rejection != null ->
                    throw A2AInvalidAgentResponseException("Agent sent an invalid event")

                // Neither a Message nor a Task: there is nothing to respond with.
                isNewTask -> throw A2AInvalidAgentResponseException("Agent ended the turn without sending any event")

                snapshot != null -> emit(snapshot)
            }
        }
    }

    /**
     * An inline push notification config saved by [saveInlinePushConfig].
     *
     * @property isNew whether no config with [id] existed for [taskId] before; only known for a follow-up, `false` otherwise.
     */
    private class SavedInlinePushConfig(val taskId: String, val id: String, val isNew: Boolean)

    /**
     * Saves the inline push notification config of [request] for [taskId], see [onSendMessageCommon].
     *
     * @return the saved config, or `null` if none was saved.
     */
    private suspend fun saveInlinePushConfig(
        request: SendMessageRequest,
        taskId: String,
        ctx: ServerCallContext,
        isFollowUp: Boolean,
    ): SavedInlinePushConfig? {
        val inline = request.configuration?.taskPushNotificationConfig ?: return null
        val pushStorage = pushConfigStorage

        if (agentCard.capabilities.pushNotifications != true || pushStorage == null || pushSender == null) {
            return null
        }

        checkPushUrl(inline)

        val id = inline.id?.takeIf { it.isNotBlank() } ?: taskId
        val config = inline.copy(taskId = taskId, id = id, tenant = request.tenant)

        validatePushNotificationConfig(config, ctx)

        // A config this request replaces must not be deleted if the follow-up is rejected.
        val isNew = isFollowUp && pushStorage.getAll(taskId).none { it.id == id }
        pushStorage.save(config, ctx)

        return SavedInlinePushConfig(taskId, id, isNew)
    }

    /** Deletes [config], saved for a follow-up whose turn didn't start. Failures are only logged. */
    private suspend fun deleteInlinePushConfig(config: SavedInlinePushConfig, tenant: String?, ctx: ServerCallContext) {
        val pushStorage = pushConfigStorage ?: return

        try {
            pushStorage.delete(DeleteTaskPushNotificationConfigRequest(config.taskId, config.id, tenant), ctx)
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete push notification config '${config.id}' of task '${config.taskId}'" }
        }
    }

    /** Deletes the push notification configs of [taskId] if the task was never stored. Failures are only logged. */
    private suspend fun deletePushConfigsIfTaskAbsent(taskId: String) {
        val pushStorage = pushConfigStorage ?: return

        try {
            if (taskStorage.get(taskId, historyLength = 0, includeArtifacts = false) == null) {
                pushStorage.deleteAll(taskId)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete push notification configs of task '$taskId', which was not created" }
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

    /** Whether the agent job already failed because its event was rejected ([InvalidEventException], [TaskOperationException]). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Turn.failedWithRejectedEvent(): Boolean {
        val failure = if (agentJob.isCompleted) agentJob.getCompletionExceptionOrNull() else null
        return failure is InvalidEventException || failure is TaskOperationException
    }

    /**
     * Waits for the agent job of an ended turn and rethrows its failure. A cancelled agent job (e.g. by CancelTask) is
     * not an error of this request; only the caller's own cancellation propagates. Rejected agent events
     * ([InvalidEventException], [TaskOperationException]) become [A2AInvalidAgentResponseException].
     */
    private suspend fun awaitAgent(turn: Turn) {
        try {
            turn.agentJob.await()
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
     *
     * An inline push notification config is saved for the turn's task, see [onSendMessageCommon].
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
        return turnManager.withTaskProcessor(taskId, initialTask.contextId.orEmpty()) { eventProcessor, agentJob, publish ->
            // Re-read the task: a turn may have ended in the meantime.
            val task = taskStorage.get(taskId, historyLength = 0, includeArtifacts = true)
                ?: throw A2ATaskNotFoundException("Task '$taskId' not found")

            if (task.status.state.terminal) {
                throw A2ATaskNotCancelableException("Task '$taskId' is already in terminal state ${task.status.state}")
            }

            val requestContext = newRequestContext(ctx, request, eventProcessor.contextId, eventProcessor.taskId, task)

            try {
                agentExecutor.cancel(requestContext, eventProcessor, agentJob)
            } catch (e: TurnNotActiveException) {
                // The agent ended the task since it was read, closing the turn's processor: report that state below.
                val current = taskStorage.get(taskId, historyLength = 0, includeArtifacts = false)
                if (current == null || !current.status.state.terminal) throw e
            }

            // The executor may have left the task running: force it to canceled.
            val afterCancel = taskStorage.get(taskId, historyLength = 0, includeArtifacts = false)
                ?: throw A2ATaskNotFoundException("Task '$taskId' not found")

            if (!afterCancel.status.state.terminal) {
                // Nothing may publish for the task after the forced cancellation.
                agentJob?.cancelAndJoin()

                val current = taskStorage.get(taskId, historyLength = 0, includeArtifacts = false)
                if (current != null && !current.status.state.terminal) {
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
                }
            }

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
     * Streams the task snapshot followed by its live events until the task reaches a terminal state or the server is
     * shut down with [cancel], see [TurnManager.subscribe]. Works whether or not a turn of the task is running, e.g. for a task waiting for input.
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

        emitAll(turnManager.subscribe(request, ctx))
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
        checkRequiredExtensions(ctx)

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
     * Checks a push notification config before it is saved, both from CreateTaskPushNotificationConfig and inline in
     * SendMessage. [config] has its task id and id set. Does nothing by default.
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
     * and that both [pushConfigStorage] and [pushSender] are configured, called in push notification config operations
     * after [checkRequiredExtensions]. Without a sender, saved configs would never be notified.
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
     * Shuts the server down: ends every SubscribeToTask stream normally (see [TurnManager.shutdown]) and cancels
     * [coroutineScope], cancelling all running agent jobs and turns.
     *
     * @param cause Optional cause of the cancellation
     */
    public open fun cancel(cause: CancellationException? = null) {
        turnManager.shutdown()
        coroutineScope.cancel(cause)
    }
}
