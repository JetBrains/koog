package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2ATaskNotCancelableException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.exceptions.TurnNotActiveException
import ai.koog.a2a.server.notifications.GatedPushNotificationSender
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.notifications.pushConfig
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.turn.TurnEventProcessor
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant

/**
 * CancelTask at the [A2AServer] level: the executor's cancellation, the server-forced CANCELED state, and the effect on
 * subscribers and on the requester of the cancelled turn.
 */
class A2AServerCancelTest {
    private val ctx = ServerCallContext()
    private lateinit var taskStorage: InMemoryTaskStorage
    private lateinit var pushConfigStorage: InMemoryPushNotificationConfigStorage
    private lateinit var pushSender: GatedPushNotificationSender

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
        pushConfigStorage = InMemoryPushNotificationConfigStorage()
        pushSender = GatedPushNotificationSender()
    }

    private fun newServer(
        scripts: Map<String, AgentScript>,
        cancelHandler: CancelHandler,
        scope: CoroutineScope,
        storage: InMemoryTaskStorage = taskStorage,
    ) = A2AServer(
        agentExecutor = ScriptedAgentExecutor(scripts, cancelHandler),
        agentCard = testAgentCard(AgentCapabilities(streaming = true, pushNotifications = true)),
        taskStorage = storage,
        pushConfigStorage = pushConfigStorage,
        pushSender = pushSender,
        idGenerator = FixedIdGenerator(messageId = "generated-message-id"),
        coroutineScope = scope,
    )

    private fun TestScope.createServer(scripts: Map<String, AgentScript>, cancelHandler: CancelHandler) =
        newServer(scripts, cancelHandler, agentScope())

    private fun request(text: String) = SendMessageRequest(
        message = Message(
            messageId = "user-$text",
            role = Role.ROLE_USER,
            parts = listOf(TextPart(text)),
            contextId = CONTEXT_ID,
        ),
    )

    private fun status(state: TaskState, timestamp: Instant = TIMESTAMP, message: String? = null) = TaskStatus(
        state = state,
        message = message?.let {
            Message(
                messageId = "agent-$it",
                role = Role.ROLE_AGENT,
                parts = listOf(TextPart(it)),
                contextId = CONTEXT_ID,
                taskId = TASK_ID,
            )
        },
        timestamp = timestamp,
    )

    private fun statusUpdate(state: TaskState, message: String? = null) = TaskStatusUpdateEvent(
        taskId = TASK_ID,
        contextId = CONTEXT_ID,
        status = status(state, message = message),
    )

    private fun task(state: TaskState) = Task(id = TASK_ID, contextId = CONTEXT_ID, status = status(state))

    /** Creates the task and leaves it waiting for input; the turn ends. */
    private val idleScript = AgentScript { _, processor ->
        processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED))
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED))
    }

    private val doNothingOnCancel = CancelHandler { _, _ -> }

    private val canceledByExecutor = statusUpdate(TaskState.TASK_STATE_CANCELED, "canceled by executor")

    private val cancelingExecutor = CancelHandler { processor, agentJob ->
        agentJob?.cancelAndJoin()
        processor.sendTaskEvent(canceledByExecutor)
    }

    private class RunningTask {
        val started = CompletableDeferred<Unit>()
        val interrupted = CompletableDeferred<Unit>()

        val script = AgentScript { _, processor ->
            processor.sendTaskEvent(Task(id = TASK_ID, contextId = CONTEXT_ID, status = status(TaskState.TASK_STATE_WORKING)))
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                interrupted.complete(Unit)
            }
        }

        private fun status(state: TaskState) = TaskStatus(state = state, timestamp = TIMESTAMP)
    }

    private fun forcedCancellation(event: Event): TaskStatusUpdateEvent {
        assertIs<TaskStatusUpdateEvent>(event)
        // The timestamp is taken by the server's clock.
        assertEquals(statusUpdate(TaskState.TASK_STATE_CANCELED).copy(status = status(TaskState.TASK_STATE_CANCELED, event.status.timestamp ?: TIMESTAMP)), event)
        assertNotNull(event.status.timestamp)
        return event
    }

    /** Runs the `start` script, which leaves the task idle, and subscribes to it. */
    private suspend fun TestScope.idleTaskWithSubscriber(server: A2AServer): Pair<Task, Subscription> {
        server.onSendMessage(request("start"), ctx)
        val stored = assertNotNull(taskStorage.get(TASK_ID))
        return stored to subscribe(server).also { it.firstEvent.await() }
    }

    @Test
    fun testCancelIdleInputRequiredTaskForcesCanceledAndClosesSubscribers() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("start" to idleScript), doNothingOnCancel)
        val (stored, subscription) = idleTaskWithSubscriber(server)

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)
        subscription.job.join()

        val forced = forcedCancellation(subscription.events.last())
        assertEquals(listOf<Event>(stored, forced), subscription.events)
        assertEquals(
            stored.copy(status = forced.status, history = null),
            canceled,
        )
        assertEquals(canceled, taskStorage.get(TASK_ID, historyLength = 0, includeArtifacts = true))
    }

    @Test
    fun testCancelIdleTaskWithExecutorCancellationSendsNoDuplicateCanceled() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("start" to idleScript), cancelingExecutor)
        val (stored, subscription) = idleTaskWithSubscriber(server)

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)
        subscription.job.join()

        assertEquals(listOf<Event>(stored, canceledByExecutor), subscription.events)
        assertEquals(stored.copy(status = canceledByExecutor.status, history = null), canceled)
    }

    @Test
    fun testCancelRunningTaskEndsRequesterWithCanceledTaskWhenExecutorCancels() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningTask()
        val server = createServer(mapOf("run" to running.script), cancelingExecutor)
        val response = async { server.onSendMessage(request("run"), ctx) }
        running.started.await()
        val subscription = subscribe(server)
        subscription.firstEvent.await()

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)
        subscription.job.join()

        assertEquals(canceledByExecutor.status, canceled.status)
        // The requester's blocking call ends with the canceled task, not with an exception.
        assertEquals<Event?>(taskStorage.get(TASK_ID), response.await())
        assertEquals(1, subscription.events.count { it is TaskStatusUpdateEvent && it.status.state == TaskState.TASK_STATE_CANCELED })
    }

    @Test
    fun testCancelRunningTaskForcesCanceledAndInterruptsAgentWhenExecutorDoesNothing() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningTask()
        val server = createServer(mapOf("run" to running.script), doNothingOnCancel)
        val response = async { server.onSendMessage(request("run"), ctx) }
        running.started.await()

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)

        running.interrupted.await()
        assertEquals(TaskState.TASK_STATE_CANCELED, canceled.status.state)
        assertEquals(null, canceled.status.message)
        assertEquals<Event?>(taskStorage.get(TASK_ID), response.await())
        assertEquals(TaskState.TASK_STATE_CANCELED, assertNotNull(taskStorage.get(TASK_ID)).status.state)
    }

    @Test
    fun testForcedCancelOfRunningFollowUpDoesNotPersistItsUserMessage() = runTest(timeout = TEST_TIMEOUT) {
        val followUpStarted = CompletableDeferred<Unit>()
        // A follow-up turn whose agent sends nothing until it is cancelled.
        val waitScript = AgentScript { _, _ ->
            followUpStarted.complete(Unit)
            awaitCancellation()
        }
        val server = createServer(mapOf("start" to idleScript, "wait" to waitScript), doNothingOnCancel)
        server.onSendMessage(request("start"), ctx)
        val idle = assertNotNull(taskStorage.get(TASK_ID))

        val followUpRequest = request("wait").let { it.copy(message = it.message.copy(taskId = TASK_ID)) }
        val response = async { server.onSendMessage(followUpRequest, ctx) }
        followUpStarted.await()

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)

        // The requester of the follow-up still gets the canceled task.
        assertEquals(TaskState.TASK_STATE_CANCELED, assertIs<Task>(response.await()).status.state)
        assertEquals(idle.copy(status = canceled.status), taskStorage.get(TASK_ID))
    }

    @Test
    fun testForcedCanceledOfIdleTaskIsPushed() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("start" to idleScript), doNothingOnCancel)
        server.onSendMessage(request("start"), ctx)
        runCurrent()
        pushConfigStorage.save(pushConfig(TASK_ID, "c1"), ctx)

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)
        runCurrent()

        assertEquals(listOf<Pair<String, Event>>("c1" to statusUpdate(TaskState.TASK_STATE_CANCELED).copy(status = canceled.status)), pushSender.sent)
    }

    @Test
    fun testForcedCanceledOfRunningTaskIsPushed() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningTask()
        val server = createServer(mapOf("run" to running.script), doNothingOnCancel)
        val response = async { server.onSendMessage(request("run"), ctx) }
        running.started.await()
        runCurrent()
        pushConfigStorage.save(pushConfig(TASK_ID, "c1"), ctx)

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)
        response.await()
        runCurrent()

        assertEquals(listOf<Pair<String, Event>>("c1" to statusUpdate(TaskState.TASK_STATE_CANCELED).copy(status = canceled.status)), pushSender.sent)
    }

    /**
     * A running agent that reaches [finalState] on its own once the executor's cancellation has started, i.e. after
     * CancelTask read the task as running. The executor then tries to send CANCELED through the closed processor.
     */
    private suspend fun TestScope.cancelRacingAgentThatReaches(finalState: TaskState): Pair<Result<Task>, Event> {
        val started = CompletableDeferred<Unit>()
        val finishNow = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val script = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
            started.complete(Unit)
            finishNow.await()
            processor.sendTaskEvent(statusUpdate(finalState))
            finished.complete(Unit)
        }
        val lateCancel = CancelHandler { processor, _ ->
            finishNow.complete(Unit)
            finished.await()
            processor.sendTaskEvent(canceledByExecutor)
        }
        val server = createServer(mapOf("run" to script), lateCancel)
        val response = async { server.onSendMessage(request("run"), ctx) }
        started.await()

        val result = runCatching { server.onCancelTask(CancelTaskRequest(TASK_ID), ctx) }

        return result to response.await()
    }

    @Test
    fun testCancelRacingWithTurnReachingCompletedFailsWithNotCancelable() = runTest(timeout = TEST_TIMEOUT) {
        val (result, response) = cancelRacingAgentThatReaches(TaskState.TASK_STATE_COMPLETED)

        assertIs<A2ATaskNotCancelableException>(result.exceptionOrNull())
        assertEquals(TaskState.TASK_STATE_COMPLETED, assertIs<Task>(response).status.state)
        assertEquals(response, taskStorage.get(TASK_ID))
    }

    @Test
    fun testCancelRacingWithTurnReachingCanceledReturnsCanceledTask() = runTest(timeout = TEST_TIMEOUT) {
        val (result, response) = cancelRacingAgentThatReaches(TaskState.TASK_STATE_CANCELED)

        assertEquals(TaskState.TASK_STATE_CANCELED, assertIs<Task>(response).status.state)
        assertEquals(response.copy(history = null), result.getOrThrow())
    }

    @Test
    fun testCancelTerminalOrUnknownTaskFails() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(emptyMap(), cancelingExecutor)
        val completed = task(TaskState.TASK_STATE_COMPLETED)
        taskStorage.update(completed)

        assertFailsWith<A2ATaskNotCancelableException> { server.onCancelTask(CancelTaskRequest(TASK_ID), ctx) }
        assertFailsWith<A2ATaskNotFoundException> { server.onCancelTask(CancelTaskRequest("unknown"), ctx) }
        assertEquals(completed, taskStorage.get(TASK_ID))
    }

    @Test
    fun testFollowUpStartedDuringIdleCancelIsRejectedAndNeverRuns() = runTest(timeout = TEST_TIMEOUT) {
        val cancelEntered = CompletableDeferred<Unit>()
        val releaseCancel = CompletableDeferred<Unit>()
        val gatedCancel = CancelHandler { _, _ ->
            cancelEntered.complete(Unit)
            releaseCancel.await()
        }
        var followUpRan = false
        val server = createServer(mapOf("finish" to AgentScript { _, _ -> followUpRan = true }), gatedCancel)
        // An idle task: no turn is running.
        taskStorage.update(task(TaskState.TASK_STATE_INPUT_REQUIRED))

        // The cancellation of the idle task is in progress when the follow-up arrives: it has passed validation and
        // waits for the cancellation to finish to start its turn.
        val cancel = async { server.onCancelTask(CancelTaskRequest(TASK_ID), ctx) }
        cancelEntered.await()
        val followUpRequest = request("finish").let { it.copy(message = it.message.copy(taskId = TASK_ID)) }
        val followUp = async { runCatching { server.onSendMessage(followUpRequest, ctx) } }
        runCurrent()
        assertEquals(false, followUp.isCompleted)

        releaseCancel.complete(Unit)
        val canceled = cancel.await()

        assertIs<A2AUnsupportedOperationException>(followUp.await().exceptionOrNull())
        assertEquals(false, followUpRan)
        assertEquals(TaskState.TASK_STATE_CANCELED, canceled.status.state)
        assertEquals(canceled, taskStorage.get(TASK_ID, historyLength = 0, includeArtifacts = true))
    }

    @Test
    fun testProcessorLeakedByFailedIdleCancelRejectsEventsAfterCancelReturns() = runTest(timeout = TEST_TIMEOUT) {
        var leaked: TurnEventProcessor? = null
        val leakingCancel = CancelHandler { processor, agentJob ->
            assertEquals(null, agentJob)
            leaked = processor
            // Fails before anything ends the task, so nothing closes the processor on a terminal state.
            throw IllegalStateException("cancel failed")
        }
        val server = createServer(emptyMap(), leakingCancel)
        // An idle task: the executor gets a standalone processor.
        val idle = task(TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(idle)

        assertFailsWith<IllegalStateException> {
            server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)
        }

        val processor = assertNotNull(leaked)
        assertFailsWith<TurnNotActiveException> {
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
        }
        assertEquals(idle, taskStorage.get(TASK_ID))
    }

    @Test
    fun testStandaloneCancelProcessorRejectsMessage() = runTest(timeout = TEST_TIMEOUT) {
        var messageFailure: Throwable? = null
        val messagingCancel = CancelHandler { processor, _ ->
            val reply = Message(messageId = "agent-reply", role = Role.ROLE_AGENT, parts = listOf(TextPart("bye")), contextId = CONTEXT_ID)
            messageFailure = runCatching { processor.sendMessage(reply) }.exceptionOrNull()
        }
        val server = createServer(emptyMap(), messagingCancel)
        // An idle task: the executor gets a standalone processor.
        taskStorage.update(task(TaskState.TASK_STATE_INPUT_REQUIRED))

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)

        // The task exists, so a Message is invalid; the server still forces the cancellation.
        assertIs<InvalidEventException>(messageFailure)
        assertEquals(TaskState.TASK_STATE_CANCELED, canceled.status.state)
    }

    @Test
    fun testCancelRacingWithFollowUpTurnEndsInOneConsistentTerminalState() = runTest(timeout = TEST_TIMEOUT) {
        // Real concurrency: the cancellation and the follow-up turn run on different threads.
        withContext(Dispatchers.Default) {
            repeat(50) { attempt ->
                val storage = InMemoryTaskStorage()
                val finishScript = AgentScript { _, processor ->
                    processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
                    processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
                }
                val server = newServer(
                    mapOf("start" to idleScript, "finish" to finishScript),
                    doNothingOnCancel,
                    CoroutineScope(SupervisorJob() + Dispatchers.Default),
                    storage,
                )
                server.onSendMessage(request("start"), ctx)
                val followUp = request("finish").let { it.copy(message = it.message.copy(taskId = TASK_ID, messageId = "finish-")) }

                val results = listOf(
                    async { runCatching { server.onSendMessage(followUp, ctx) } },
                    async { runCatching { server.onCancelTask(CancelTaskRequest(TASK_ID), ctx) } },
                ).map { it.await() }

                val finalState = assertNotNull(storage.get(TASK_ID)).status.state
                assertEquals(true, finalState.terminal, "attempt : ")
                // The cancellation succeeds only if the task ended up canceled.
                val cancelResult = results[1]
                assertEquals(finalState == TaskState.TASK_STATE_CANCELED, cancelResult.isSuccess, "attempt : ")
            }
        }
    }
}
