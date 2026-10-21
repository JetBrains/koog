package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AContentTypeNotSupportedException
import ai.koog.a2a.exceptions.A2ATaskNotCancelableException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
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
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.turn.TurnEventProcessor
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * CancelTask at the [A2AServer] level: the executor's cancellation, the server-forced CANCELED state, and the effect on
 * subscribers and on the requester of the cancelled turn.
 */
class A2AServerCancelTest {
    private companion object {
        /** Marks the coroutine of a CancelTask call whose task reads are counted, see [CancelReadCounter]. */
        private val CANCEL_CALL = CoroutineName("cancel-call")
    }

    /** Counts the task reads made in [CANCEL_CALL] coroutines, i.e. by CancelTask, not by the agent or the turn. */
    private class CancelReadCounter(private val delegate: TaskStorage) : TaskStorage by delegate {
        var reads = 0
            private set

        private suspend fun <T> counted(read: T): T = read.also {
            if (currentCoroutineContext()[CoroutineName] == CANCEL_CALL) reads++
        }

        override suspend fun get(request: GetTaskRequest, context: ServerCallContext): Task? =
            counted(delegate.get(request, context))

        override suspend fun get(taskId: String, historyLength: Int?, includeArtifacts: Boolean): Task? =
            counted(delegate.get(taskId, historyLength, includeArtifacts))

        override suspend fun getAll(taskIds: List<String>, historyLength: Int?, includeArtifacts: Boolean): List<Task> =
            counted(delegate.getAll(taskIds, historyLength, includeArtifacts))

        override suspend fun getByContext(contextId: String, historyLength: Int?, includeArtifacts: Boolean): List<Task> =
            counted(delegate.getByContext(contextId, historyLength, includeArtifacts))
    }

    private val ctx = ServerCallContext()
    private lateinit var taskStorage: InMemoryTaskStorage
    private lateinit var cancelReads: CancelReadCounter
    private lateinit var pushConfigStorage: InMemoryPushNotificationConfigStorage
    private lateinit var pushSender: GatedPushNotificationSender

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
        cancelReads = CancelReadCounter(taskStorage)
        pushConfigStorage = InMemoryPushNotificationConfigStorage()
        pushSender = GatedPushNotificationSender()
    }

    private fun newServer(
        scripts: Map<String, AgentScript>,
        cancelHandler: CancelHandler,
        scope: CoroutineScope,
        storage: TaskStorage = cancelReads,
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

    /** Cancels the task, counting its task reads in [cancelReads]. */
    private suspend fun A2AServer.cancelCountingReads(): Task =
        withContext(CANCEL_CALL) { onCancelTask(CancelTaskRequest(TASK_ID), ctx) }

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
    fun testCancelRequestCancelledDuringForcedCanceledFanOutStillEndsSubscribers() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("start" to idleScript), doNothingOnCancel)
        server.onSendMessage(request("start"), ctx)
        val stored = assertNotNull(taskStorage.get(TASK_ID))

        // The stuck subscriber holds the snapshot and doesn't receive further; it is registered first.
        val stuckGate = CompletableDeferred<Unit>()
        val stuck = subscribe(server) { stuckGate.await() }.also { it.firstEvent.await() }
        val other = subscribe(server).also { it.firstEvent.await() }

        // E.g. the CancelTask client times out while the forced CANCELED waits for the stuck subscriber.
        val cancelRequest = launch { server.onCancelTask(CancelTaskRequest(TASK_ID), ctx) }
        advanceUntilIdle()
        assertTrue(cancelRequest.isActive)

        cancelRequest.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(TaskState.TASK_STATE_CANCELED, assertNotNull(taskStorage.get(TASK_ID)).status.state)
        assertTrue(other.job.isCompleted)
        assertEquals(listOf<Event>(stored), other.events)

        stuckGate.complete(Unit)
        stuck.job.join()
        assertEquals(listOf<Event>(stored), stuck.events)
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

        val result = runCatching { server.cancelCountingReads() }

        return result to response.await()
    }

    @Test
    fun testCancelRacingWithTurnReachingCompletedFailsWithNotCancelable() = runTest(timeout = TEST_TIMEOUT) {
        val (result, response) = cancelRacingAgentThatReaches(TaskState.TASK_STATE_COMPLETED)

        assertIs<A2ATaskNotCancelableException>(result.exceptionOrNull())
        assertEquals(TaskState.TASK_STATE_COMPLETED, assertIs<Task>(response).status.state)
        assertEquals(response, taskStorage.get(TASK_ID))
        // The task before the executor runs, and its state after the executor's send failed on the closed processor.
        assertEquals(2, cancelReads.reads)
    }

    @Test
    fun testCancelRacingWithTurnReachingCanceledReturnsCanceledTask() = runTest(timeout = TEST_TIMEOUT) {
        val (result, response) = cancelRacingAgentThatReaches(TaskState.TASK_STATE_CANCELED)

        assertEquals(TaskState.TASK_STATE_CANCELED, assertIs<Task>(response).status.state)
        assertEquals(response.copy(history = null), result.getOrThrow())
        assertEquals(2, cancelReads.reads)
    }

    /**
     * A running agent whose invalid event is rejected, failing its job, while CancelTask holds the turn: the cancellation
     * (by the executor if [executorCancels], else forced by the server) reaches the requester before the turn ends.
     *
     * @return the CancelTask result and the requester's response
     */
    private suspend fun TestScope.cancelAfterRejectedEvent(executorCancels: Boolean): Pair<Task, Event> {
        val started = CompletableDeferred<Unit>()
        val rejectNow = CompletableDeferred<Unit>()
        val script = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
            started.complete(Unit)
            rejectNow.await()
            // Another task's id: rejected, and the agent lets the exception fail its job.
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING).copy(taskId = "other-task"))
        }
        val cancelAfterRejection = CancelHandler { processor, agentJob ->
            rejectNow.complete(Unit)
            assertIs<InvalidEventException>(runCatching { assertNotNull(agentJob).await() }.exceptionOrNull())
            if (executorCancels) processor.sendTaskEvent(canceledByExecutor)
        }
        val server = createServer(mapOf("run" to script), cancelAfterRejection)
        val response = async { server.onSendMessage(request("run"), ctx) }
        started.await()

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)

        return canceled to response.await()
    }

    @Test
    fun testRequesterGetsCanceledTaskWhenExecutorCancelsAfterRejectedEvent() = runTest(timeout = TEST_TIMEOUT) {
        val (canceled, response) = cancelAfterRejectedEvent(executorCancels = true)

        assertEquals(canceledByExecutor.status, canceled.status)
        assertEquals<Event?>(taskStorage.get(TASK_ID), response)
    }

    @Test
    fun testRequesterGetsCanceledTaskWhenServerForcesCancelAfterRejectedEvent() = runTest(timeout = TEST_TIMEOUT) {
        val (canceled, response) = cancelAfterRejectedEvent(executorCancels = false)

        assertEquals(TaskState.TASK_STATE_CANCELED, canceled.status.state)
        assertEquals<Event?>(taskStorage.get(TASK_ID), response)
    }

    @Test
    fun testRequesterGetsCanceledTaskWhenFollowUpAgentThrowsA2AErrorDuringCancel() = runTest(timeout = TEST_TIMEOUT) {
        val followUpStarted = CompletableDeferred<Unit>()
        val failNow = CompletableDeferred<Unit>()
        val failingScript = AgentScript { _, _ ->
            followUpStarted.complete(Unit)
            failNow.await()
            throw A2AContentTypeNotSupportedException("Unsupported content type")
        }
        // Lets the agent fail before its first event, then leaves the cancellation to the server.
        val failAgentFirst = CancelHandler { _, agentJob ->
            failNow.complete(Unit)
            assertIs<A2AContentTypeNotSupportedException>(runCatching { assertNotNull(agentJob).await() }.exceptionOrNull())
        }
        val server = createServer(mapOf("start" to idleScript, "fail" to failingScript), failAgentFirst)
        server.onSendMessage(request("start"), ctx)
        val followUpRequest = request("fail").let { it.copy(message = it.message.copy(taskId = TASK_ID)) }
        val response = async { server.onSendMessage(followUpRequest, ctx) }
        followUpStarted.await()

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)

        // The forced CANCELED reaches the requester before the turn ends, so it wins over the agent's error.
        assertEquals(TaskState.TASK_STATE_CANCELED, canceled.status.state)
        assertEquals<Event?>(taskStorage.get(TASK_ID), response.await())
    }

    @Test
    fun testCancelOfIdleTaskByExecutorReadsTaskTwice() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(emptyMap(), cancelingExecutor)
        // An idle task: no turn is running.
        val idle = task(TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(idle)

        val canceled = server.cancelCountingReads()

        assertEquals(idle.copy(status = canceledByExecutor.status), canceled)
        // The task before the executor runs, and after it, which is the result.
        assertEquals(2, cancelReads.reads)
    }

    @Test
    fun testCancelOfRunningTaskByExecutorReadsTaskTwice() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningTask()
        val server = createServer(mapOf("run" to running.script), cancelingExecutor)
        val response = async { server.onSendMessage(request("run"), ctx) }
        running.started.await()

        val canceled = server.cancelCountingReads()

        assertEquals(canceledByExecutor.status, canceled.status)
        assertEquals(2, cancelReads.reads)
        response.await()
    }

    @Test
    fun testForcedCancelOfIdleTaskReadsTaskThreeTimes() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(emptyMap(), doNothingOnCancel)
        // An idle task: no turn is running.
        taskStorage.update(task(TaskState.TASK_STATE_INPUT_REQUIRED))

        val canceled = server.cancelCountingReads()

        assertEquals(TaskState.TASK_STATE_CANCELED, canceled.status.state)
        // No agent to stop, so no re-read before the forced CANCELED; the result is read back after it.
        assertEquals(3, cancelReads.reads)
    }

    @Test
    fun testForcedCancelOfRunningTaskReadsTaskFourTimes() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningTask()
        val server = createServer(mapOf("run" to running.script), doNothingOnCancel)
        val response = async { server.onSendMessage(request("run"), ctx) }
        running.started.await()

        val canceled = server.cancelCountingReads()

        assertEquals(TaskState.TASK_STATE_CANCELED, canceled.status.state)
        // Also re-read after the agent is stopped, since it may have ended the task first.
        assertEquals(4, cancelReads.reads)
        response.await()
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

        // The cancellation of the idle task is in progress when the follow-up arrives: it waits for the cancellation
        // to finish to validate the task and start its turn.
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
    fun testCancelOfRunningTurnRejectsFollowUpWaitingForIt() = runTest(timeout = TEST_TIMEOUT) {
        val running = RunningTask()
        var followUpRan = false
        val server = createServer(
            mapOf("run" to running.script, "finish" to AgentScript { _, _ -> followUpRan = true }),
            doNothingOnCancel,
        )
        val response = async { server.onSendMessage(request("run"), ctx) }
        running.started.await()

        // The follow-up waits for the running turn to end; the cancellation doesn't wait for the follow-up.
        val followUpRequest = request("finish").let {
            it.copy(
                message = it.message.copy(taskId = TASK_ID),
                configuration = SendMessageConfiguration(
                    taskPushNotificationConfig = TaskPushNotificationConfig(url = "https://example.com/hook"),
                ),
            )
        }
        val followUp = async { runCatching { server.onSendMessage(followUpRequest, ctx) } }
        runCurrent()
        assertEquals(false, followUp.isCompleted)

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), ctx)

        assertEquals(
            A2AUnsupportedOperationException::class to "Task '$TASK_ID' is in terminal state TASK_STATE_CANCELED and can't accept messages",
            followUp.await().exceptionOrNull()?.let { it::class to it.message }
        )
        // Neither the follow-up's agent ran, nor its user message or inline config were saved.
        val stored = canceled.copy(history = listOf(request("run").message.copy(taskId = TASK_ID)))
        assertEquals(stored, taskStorage.get(TASK_ID))
        assertEquals<Event?>(stored, response.await())
        assertEquals(false, followUpRan)
        assertEquals(emptyList(), pushConfigStorage.getAll(TASK_ID))
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
