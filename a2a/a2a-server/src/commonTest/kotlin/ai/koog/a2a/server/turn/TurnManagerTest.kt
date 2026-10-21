@file:OptIn(InternalA2AApi::class)

package ai.koog.a2a.server.turn

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.exceptions.TurnNotActiveException
import ai.koog.a2a.server.messages.ContextMessageStorage
import ai.koog.a2a.server.messages.InMemoryMessageStorage
import ai.koog.a2a.server.notifications.GatedPushNotificationSender
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.notifications.pushConfig
import ai.koog.a2a.server.notifications.pushes
import ai.koog.a2a.server.tasks.ContextTaskStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TurnManagerTest {
    private companion object Companion {
        private val TEST_TIMEOUT = 30.seconds
    }

    private lateinit var taskStorage: InMemoryTaskStorage
    private lateinit var pushConfigStorage: InMemoryPushNotificationConfigStorage
    private lateinit var pushSender: GatedPushNotificationSender

    private val contextId = "context-1"
    private val taskId = "task-1"
    private val callContext = ServerCallContext()

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
        pushConfigStorage = InMemoryPushNotificationConfigStorage()
        pushSender = GatedPushNotificationSender()
    }

    private fun createMessage(
        messageId: String,
        contextId: String,
        content: String
    ) = Message(
        messageId = messageId,
        role = Role.ROLE_USER,
        parts = listOf(TextPart(content)),
        contextId = contextId
    )

    private fun createTask(
        id: String,
        contextId: String,
        state: TaskState = TaskState.TASK_STATE_SUBMITTED
    ) = Task(
        id = id,
        contextId = contextId,
        status = TaskStatus(
            state = state,
            timestamp = Instant.parse("2023-01-01T10:00:00Z")
        )
    )

    /**
     * The task as stored when the agent created it in a turn started with [createContext]: the server adds the user
     * message of the request to its history.
     */
    private fun Task.withUserMessage() =
        copy(history = listOf(createMessage("user-msg", this@TurnManagerTest.contextId, "Hello").copy(taskId = id)))

    private fun statusUpdate(
        taskId: String,
        contextId: String,
        state: TaskState,
        text: String? = null,
    ) = TaskStatusUpdateEvent(
        taskId = taskId,
        contextId = contextId,
        status = TaskStatus(
            state = state,
            message = text?.let {
                Message(
                    messageId = "agent-$it",
                    role = Role.ROLE_AGENT,
                    parts = listOf(TextPart(it)),
                    contextId = contextId,
                    taskId = taskId,
                )
            },
            timestamp = Instant.parse("2023-01-01T10:00:01Z")
        )
    )

    private fun createContext(
        taskId: String,
        contextId: String,
        task: Task? = null,
    ) = RequestContext(
        callContext = callContext,
        params = SendMessageRequest(createMessage("user-msg", contextId, "Hello")),
        taskStorage = ContextTaskStorage(contextId, taskStorage),
        messageStorage = ContextMessageStorage(contextId, InMemoryMessageStorage()),
        contextId = contextId,
        taskId = taskId,
        task = task,
    )

    /** Scope for agent jobs: a supervisor, so that a failing agent doesn't fail the test. */
    private fun TestScope.agentScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))

    private fun TestScope.createManager() = TurnManager(
        coroutineScope = agentScope(),
        taskStorage = taskStorage,
        pushConfigStorage = pushConfigStorage,
        pushSender = pushSender,
        idGenerator = FixedMessageIdGenerator,
    )

    /**
     * The events of a turn are delivered with back-pressure, so the requester's view must be consumed for the agent
     * to make progress.
     */
    private fun TestScope.drain(turn: Turn): Deferred<List<Event>> = async { turn.events.toList() }

    private suspend fun storeTask(
        taskId: String = this.taskId,
        contextId: String = this.contextId,
        state: TaskState = TaskState.TASK_STATE_INPUT_REQUIRED,
    ): Task = createTask(taskId, contextId, state).also { taskStorage.update(it) }

    @Test
    fun testMessageTurn() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val message = createMessage("msg-1", contextId, "Hello")
        assertEquals(0, turnManager.activeTurns())
        assertNull(turnManager.getTurn("any-task-id"))

        val turn = turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendMessage(message)
        }

        assertEquals(listOf<Event>(message), turn.events.toList())
        turn.join()

        // Turn and task stream should be automatically cleaned up after completion
        assertEquals(0, turnManager.activeTurns())
        assertEquals(0, turnManager.activeStreams())
    }

    @Test
    fun testTaskTurn() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val gate = CompletableDeferred<Unit>()
        val task = createTask(taskId, contextId)
        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)

        val turn = turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(task)
            gate.await()
            processor.sendTaskEvent(completed)
        }

        assertSame(turn, turnManager.getTurn(taskId))
        assertEquals(1, turnManager.activeTurns())

        gate.complete(Unit)

        assertEquals(listOf(task.withUserMessage(), completed), turn.events.toList())
        turn.join()

        assertEquals(0, turnManager.activeTurns())
        assertEquals(0, turnManager.activeStreams())
        assertEquals(task.withUserMessage().copy(status = completed.status), taskStorage.get(taskId))
    }

    @Test
    fun testStartTurnWhileTurnIsRunningFails() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val gate = CompletableDeferred<Unit>()

        val turn = turnManager.startTurn(createContext(taskId, contextId)) { gate.await() }

        assertFailsWith<IllegalStateException> {
            turnManager.startTurn(createContext(taskId, contextId)) {}
        }

        gate.complete(Unit)
        turn.join()
    }

    @Test
    fun testMultipleTurns() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val gate = CompletableDeferred<Unit>()

        fun turn(taskId: String, contextId: String): suspend (TurnEventProcessor) -> Unit = { processor ->
            processor.sendTaskEvent(createTask(taskId, contextId))
            gate.await()
            processor.sendTaskEvent(statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED))
        }

        val turn1 = turnManager.startTurn(createContext("task-1", "context-1"), turn("task-1", "context-1"))
        val turn2 = turnManager.startTurn(createContext("task-2", "context-2"), turn("task-2", "context-2"))
        drain(turn1)
        drain(turn2)

        assertSame(turn1, turnManager.getTurn("task-1"))
        assertSame(turn2, turnManager.getTurn("task-2"))
        assertEquals(2, turnManager.activeTurns())

        gate.complete(Unit)
        turn1.join()
        turn2.join()

        // All turns should be automatically cleaned up
        assertEquals(0, turnManager.activeTurns())
        assertEquals(0, turnManager.activeStreams())
    }

    @Test
    fun testFailedTurnMarksTaskFailed() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val task = createTask(taskId, contextId)

        val turn = turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(task)
            throw IllegalStateException("boom")
        }

        val events = turn.events.toList()
        assertFailsWith<IllegalStateException> { turn.agentJob.await() }
        turn.join()

        val stored = assertNotNull(taskStorage.get(taskId))
        assertNotNull(stored.status.timestamp)
        val failedStatus = failedStatus(taskId, contextId, stored.status.timestamp)
        val failedEvent = TaskStatusUpdateEvent(taskId = taskId, contextId = contextId, status = failedStatus)
        assertEquals(failedStatus, stored.status)
        assertEquals(listOf(task.withUserMessage(), failedEvent), events)
    }

    @Test
    fun testFailedTurnWithoutTaskCreatesNothing() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()

        val turn = turnManager.startTurn(createContext(taskId, contextId)) {
            throw IllegalStateException("boom")
        }

        assertEquals(emptyList(), turn.events.toList())
        assertFailsWith<IllegalStateException> { turn.agentJob.await() }
        turn.join()

        assertNull(taskStorage.get(taskId))
        assertEquals(0, turnManager.activeStreams())
    }

    @Test
    fun testAgentOwnTimeoutMarksTaskFailedAndEndsSubscribers() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        val received = mutableListOf<Event>()
        val subscriber = subscribeWith(turnManager) { received += it }
        runCurrent()

        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val turn = turnManager.startTurn(createContext(taskId, contextId, task = inputRequired)) { processor ->
            processor.sendTaskEvent(working)
            withTimeout(1) { awaitCancellation() }
        }

        val events = turn.events.toList()
        val failure = assertFailsWith<AgentCancellationFailure> { turn.agentJob.await() }
        assertIs<TimeoutCancellationException>(failure.cause)
        turn.join()
        subscriber.join()

        val stored = assertNotNull(taskStorage.get(taskId))
        val failedStatus = failedStatus(taskId, contextId, stored.status.timestamp)
        val failedEvent = TaskStatusUpdateEvent(taskId = taskId, contextId = contextId, status = failedStatus)
        assertEquals(failedStatus, stored.status)
        assertEquals(listOf<Event>(working, failedEvent), events)
        assertEquals(listOf<Event>(inputRequired, working, failedEvent), received)
        assertEquals(0, turnManager.activeStreams())
    }

    @Test
    fun testAgentOwnCancellationWithoutTaskCreatesNothing() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()

        val turn = turnManager.startTurn(createContext(taskId, contextId)) {
            throw CancellationException("agent gave up")
        }

        assertEquals(emptyList(), turn.events.toList())
        val failure = assertFailsWith<AgentCancellationFailure> { turn.agentJob.await() }
        assertEquals("agent gave up", failure.message)
        turn.join()

        assertNull(taskStorage.get(taskId))
        assertEquals(0, turnManager.activeStreams())
    }

    @Test
    fun testCancelledAgentJobDoesNotMarkTaskFailed() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val working = createTask(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val agentStarted = CompletableDeferred<Unit>()

        val turn = turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(working)
            agentStarted.complete(Unit)
            awaitCancellation()
        }
        val events = drain(turn)
        agentStarted.await()

        turn.agentJob.cancelAndJoin()
        turn.join()

        assertFailsWith<CancellationException> { turn.agentJob.await() }
        assertEquals(listOf<Event>(working.withUserMessage()), events.await())
        assertEquals(working.withUserMessage(), taskStorage.get(taskId))
    }

    /** A scope with a plain, non-supervisor job, like `CoroutineScope(Dispatchers.IO)`. */
    private fun TestScope.nonSupervisorScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))

    @Test
    fun testFailedTurnDoesNotCancelNonSupervisorScope() = runTest(timeout = TEST_TIMEOUT) {
        val scope = nonSupervisorScope()
        val turnManager = TurnManager(scope, taskStorage, pushConfigStorage, pushSender, FixedMessageIdGenerator)
        savePushConfig()
        val task = createTask(taskId, contextId)
        val runningTask = createTask("task-2", contextId)
        val gate = CompletableDeferred<Unit>()

        val running = turnManager.startTurn(createContext("task-2", contextId)) { processor ->
            processor.sendTaskEvent(runningTask)
            gate.await()
            processor.sendTaskEvent(statusUpdate("task-2", contextId, TaskState.TASK_STATE_COMPLETED))
        }
        val runningEvents = drain(running)

        turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(task)
            throw IllegalStateException("boom")
        }.also { drain(it) }.join()
        runCurrent()

        assertTrue(scope.isActive)
        val stored = assertNotNull(taskStorage.get(taskId))
        val failed = TaskStatusUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            status = failedStatus(taskId, contextId, stored.status.timestamp),
        )
        assertEquals(failed.status, stored.status)
        assertEquals(listOf<Pair<String, Event>>("c1" to task.withUserMessage(), "c1" to failed), pushSender.sent)

        // The other running agent goes on, and a new turn runs normally.
        gate.complete(Unit)
        assertEquals(
            listOf(runningTask.withUserMessage(), statusUpdate("task-2", contextId, TaskState.TASK_STATE_COMPLETED)),
            runningEvents.await(),
        )

        val newTask = createTask("task-3", contextId, TaskState.TASK_STATE_COMPLETED)
        val next = turnManager.startTurn(createContext("task-3", contextId)) { processor ->
            processor.sendTaskEvent(newTask)
        }
        assertEquals(listOf(newTask.withUserMessage()), next.events.toList())
        assertTrue(scope.isActive)
    }

    @Test
    fun testCancellingNonSupervisorScopeCancelsRunningAgent() = runTest(timeout = TEST_TIMEOUT) {
        val scope = nonSupervisorScope()
        val turnManager = TurnManager(coroutineScope = scope, taskStorage = taskStorage)
        val agentStarted = CompletableDeferred<Unit>()

        val turn = turnManager.startTurn(createContext(taskId, contextId)) {
            agentStarted.complete(Unit)
            awaitCancellation()
        }
        drain(turn)
        agentStarted.await()

        scope.cancel()
        turn.join()

        assertFailsWith<CancellationException> { turn.agentJob.await() }
        assertEquals(0, turnManager.activeTurns())
    }

    @Test
    fun testSubscribeFollowsTaskAcrossTurns() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        val received = mutableListOf<Event>()
        val subscriber = subscribeWith(turnManager) { received += it }
        runCurrent()

        // No turn is running, the subscriber alone keeps the stream.
        assertEquals(0, turnManager.activeTurns())
        assertEquals(1, turnManager.activeStreams())

        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val inputRequiredAgain = statusUpdate(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED)
        turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(working)
            processor.sendTaskEvent(inputRequiredAgain)
        }.also { drain(it) }.join()

        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)
        turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(completed)
        }.also { drain(it) }.join()

        subscriber.join()

        assertEquals(listOf<Event>(inputRequired, working, inputRequiredAgain, completed), received)
        assertEquals(0, turnManager.activeStreams())
    }

    @Test
    fun testCancelledSubscriberReleasesStream() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        storeTask()

        val subscriber = subscribeWith(turnManager) {}
        runCurrent()
        assertEquals(1, turnManager.activeStreams())

        subscriber.cancelAndJoin()

        assertEquals(0, turnManager.activeStreams())
    }

    @Test
    fun testWithTaskProcessorOnIdleTaskReachesSubscribers() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        val received = mutableListOf<Event>()
        val subscriber = subscribeWith(turnManager) { received += it }
        runCurrent()

        val canceled = statusUpdate(taskId, contextId, TaskState.TASK_STATE_CANCELED)
        turnManager.withTaskProcessor(taskId, contextId) { processor, agentJob, _ ->
            assertNull(agentJob)
            processor.sendTaskEvent(canceled)
        }

        subscriber.join()
        assertEquals(listOf<Event>(inputRequired, canceled), received)
    }

    @Test
    fun testStandaloneProcessorIsClosedAfterWithTaskProcessor() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        val leaked = turnManager.withTaskProcessor(taskId, contextId) { processor, _, _ -> processor }

        assertFailsWith<TurnNotActiveException> {
            leaked.sendTaskEvent(statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING))
        }
        assertEquals(inputRequired, taskStorage.get(taskId))
        assertEquals(0, turnManager.activeStreams())
    }

    @Test
    fun testTurnProcessorIsClosedAfterTurnEnds() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val task = createTask(taskId, contextId)
        var leaked: TurnEventProcessor? = null

        val turn = turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            leaked = processor
            processor.sendTaskEvent(task)
        }
        val events = drain(turn)
        turn.join()

        assertFailsWith<TurnNotActiveException> {
            assertNotNull(leaked).sendTaskEvent(statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED))
        }
        assertEquals(listOf<Event>(task.withUserMessage()), events.await())
        assertEquals(task.withUserMessage(), taskStorage.get(taskId))
    }

    @Test
    fun testTurnStartedInCancelledScopeEndsWithoutRunningAgent() = runTest(timeout = TEST_TIMEOUT) {
        // The scope of a server that has been shut down.
        val cancelledScope = CoroutineScope(backgroundScope.coroutineContext + Job().apply { cancel() })
        val turnManager = TurnManager(coroutineScope = cancelledScope, taskStorage = taskStorage)
        var agentStarted = false

        val turn = turnManager.startTurn(createContext(taskId, contextId)) { agentStarted = true }

        withTimeout(5.seconds) {
            assertEquals(emptyList(), turn.events.toList())
            turn.join()
        }
        assertFailsWith<CancellationException> { turn.agentJob.await() }
        assertEquals(false, agentStarted)
        assertNull(turnManager.getTurn(taskId))
        assertEquals(0, turnManager.activeTurns())
        assertEquals(0, turnManager.activeStreams())
    }

    @Test
    fun testWithTaskProcessorKeepsRunningTurnOpen() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val task = createTask(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val taskSent = CompletableDeferred<Unit>()

        val turn = turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(task)
            taskSent.complete(Unit)
            CompletableDeferred<Unit>().await() // runs until cancelled
        }
        val events = drain(turn)
        taskSent.await()

        val canceled = statusUpdate(taskId, contextId, TaskState.TASK_STATE_CANCELED)
        turnManager.withTaskProcessor(taskId, contextId) { processor, agentJob, _ ->
            assertSame(turn.agentJob, agentJob)
            agentJob?.cancelAndJoin()
            // Give the turn a chance to finish, if it (wrongly) could.
            advanceUntilIdle()
            processor.sendTaskEvent(canceled)
        }

        // The requester of the turn receives the events published by the cancellation.
        assertEquals(listOf(task.withUserMessage(), canceled), events.await())
        turn.join()
        assertEquals(0, turnManager.activeTurns())
    }

    @Test
    fun testJoinReturnsOnlyAfterTurnTeardown() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        storeTask()
        val blockEntered = CompletableDeferred<Unit>()
        val releaseBlock = CompletableDeferred<Unit>()

        val turn = turnManager.startTurn(createContext(taskId, contextId)) {}
        val events = drain(turn)
        // Holds the turn gate, so the turn can't end after its agent job has completed.
        val block = launch {
            turnManager.withTaskProcessor(taskId, contextId) { _, _, _ ->
                blockEntered.complete(Unit)
                releaseBlock.await()
            }
        }
        blockEntered.await()
        val join = launch { turn.join() }
        runCurrent()

        assertEquals(true, turn.agentJob.isCompleted)
        assertEquals(false, join.isCompleted)
        assertSame(turn, turnManager.getTurn(taskId))

        releaseBlock.complete(Unit)
        join.join()

        assertNull(turnManager.getTurn(taskId))
        assertEquals(emptyList(), events.await())
        block.join()
        assertEquals(0, turnManager.activeStreams())
    }

    @Test
    fun testServerPublishDuringFollowUpTurnSkipsPendingUserMessage() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeInputRequiredTask()
        savePushConfig()
        val agentStarted = CompletableDeferred<Unit>()

        val turn = turnManager.startTurn(createContext(taskId, contextId, inputRequired)) {
            agentStarted.complete(Unit)
            CompletableDeferred<Unit>().await() // sends nothing, runs until cancelled
        }
        val events = drain(turn)
        agentStarted.await()

        val canceled = statusUpdate(taskId, contextId, TaskState.TASK_STATE_CANCELED)
        turnManager.withTaskProcessor(taskId, contextId) { _, agentJob, publish ->
            agentJob?.cancelAndJoin()
            publish(canceled)
        }
        turn.join()
        runCurrent()

        // The requester gets the event, it's pushed, and the follow-up's user message isn't added to the history.
        assertEquals(listOf<Event>(canceled), events.await())
        assertEquals(listOf<Pair<String, Event>>("c1" to canceled), pushSender.sent)
        assertEquals(
            inputRequired.copy(status = canceled.status, history = listOf(followUpPrompt())),
            taskStorage.get(taskId)
        )
    }

    @Test
    fun testServerPublishAfterWithTaskProcessorReturnsFails() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        val leaked = turnManager.withTaskProcessor(taskId, contextId) { _, _, publish -> publish }

        assertFailsWith<IllegalStateException> {
            leaked(statusUpdate(taskId, contextId, TaskState.TASK_STATE_CANCELED))
        }
        assertEquals(inputRequired, taskStorage.get(taskId))
    }

    @Test
    fun testCancelledServerPublishOfTerminalEventEndsEverySubscriber() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        // The stuck subscriber holds the snapshot and doesn't receive further; it is registered first.
        val stuckGate = CompletableDeferred<Unit>()
        val stuck = subscribeWith(turnManager) { stuckGate.await() }
        runCurrent()
        val received = mutableListOf<Event>()
        val other = subscribeWith(turnManager) { received += it }
        runCurrent()

        // E.g. CancelTask's forced cancellation, whose request is cancelled while it waits for the stuck subscriber.
        val canceled = statusUpdate(taskId, contextId, TaskState.TASK_STATE_CANCELED)
        val canceller = launch {
            turnManager.withTaskProcessor(taskId, contextId) { _, _, publish -> publish(canceled) }
        }
        advanceUntilIdle()
        assertTrue(canceller.isActive)

        canceller.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(inputRequired.copy(status = canceled.status), taskStorage.get(taskId))
        assertTrue(other.isCompleted)
        assertEquals(listOf<Event>(inputRequired), received)
        assertEquals(1, turnManager.activeStreams())

        // Once the stuck subscriber goes on, its flow ends too and the stream is dropped.
        stuckGate.complete(Unit)
        stuck.join()
        assertEquals(0, turnManager.activeStreams())
    }

    private fun followUpPrompt() = Message(
        messageId = "agent-prompt",
        role = Role.ROLE_AGENT,
        parts = listOf(TextPart("Which one?")),
        contextId = contextId,
        taskId = taskId,
    )

    private suspend fun storeInputRequiredTask(): Task =
        createTask(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED).let {
            it.copy(status = it.status.copy(message = followUpPrompt()))
        }.also { taskStorage.update(it) }

    @Test
    fun testFollowUpTurnPersistsUserMessageOnFirstTaskEvent() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeInputRequiredTask()
        val userMessage = createMessage("user-msg", contextId, "Hello").copy(taskId = taskId)
        var storedWhenAgentStarted: Task? = null
        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)

        val turn = turnManager.startTurn(createContext(taskId, contextId, inputRequired)) {
            storedWhenAgentStarted = taskStorage.get(taskId)
            it.sendTaskEvent(working)
        }
        drain(turn)
        turn.join()

        assertEquals(inputRequired, storedWhenAgentStarted)
        assertEquals(inputRequired, turn.taskSnapshot)
        assertEquals(
            inputRequired.copy(
                status = working.status,
                history = listOf(followUpPrompt(), userMessage),
            ),
            taskStorage.get(taskId)
        )
    }

    @Test
    fun testFollowUpTurnWithoutTaskEventsDoesNotPersistUserMessage() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeInputRequiredTask()

        val turn = turnManager.startTurn(createContext(taskId, contextId, inputRequired)) {}
        drain(turn)
        turn.join()

        assertEquals(inputRequired, taskStorage.get(taskId))
    }

    @Test
    fun testFollowUpTurnOfTaskThatBecameTerminalFails() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeInputRequiredTask()
        // The task is cancelled after the caller validated it.
        val canceled = createTask(taskId, contextId, TaskState.TASK_STATE_CANCELED)
        taskStorage.update(canceled)
        var agentStarted = false

        assertFailsWith<A2AUnsupportedOperationException> {
            turnManager.startTurn(createContext(taskId, contextId, inputRequired)) { agentStarted = true }
        }
        advanceUntilIdle()

        assertEquals(false, agentStarted)
        assertNull(turnManager.getTurn(taskId))
        assertEquals(0, turnManager.activeStreams())
        assertEquals(canceled, taskStorage.get(taskId))
    }

    private suspend fun savePushConfig() {
        pushConfigStorage.save(pushConfig(taskId, "c1"), callContext)
    }

    @Test
    fun testEveryPersistedTaskEventIsPushedInOrder() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        savePushConfig()
        val task = createTask(taskId, contextId)
        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val artifact = TaskArtifactUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            artifact = Artifact(artifactId = "artifact-1", parts = listOf(TextPart("result"))),
        )
        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)

        turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            listOf(task, working, artifact, completed).forEach { processor.sendTaskEvent(it) }
        }.also { drain(it) }.join()
        runCurrent()

        // The initial task is pushed as persisted, with the user message.
        assertEquals(pushes("c1", listOf(task.withUserMessage(), working, artifact, completed)), pushSender.sent)
    }

    @Test
    fun testMessageReplyIsNotPushed() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        savePushConfig()

        turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendMessage(createMessage("agent-msg", contextId, "Hi"))
        }.also { drain(it) }.join()
        runCurrent()

        assertEquals(emptyList(), pushSender.started)
    }

    @Test
    fun testIgnoredTaskOfExistingTaskIsNotPushed() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeInputRequiredTask()
        savePushConfig()
        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)

        turnManager.startTurn(createContext(taskId, contextId, inputRequired)) { processor ->
            processor.sendTaskEvent(createTask(taskId, contextId, TaskState.TASK_STATE_WORKING))
            processor.sendTaskEvent(working)
        }.also { drain(it) }.join()
        runCurrent()

        // Neither the ignored Task nor the user message added to the history is pushed.
        assertEquals(listOf<Pair<String, Event>>("c1" to working), pushSender.sent)
    }

    @Test
    fun testFailedStatusOfCrashedTurnIsPushed() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        savePushConfig()
        val task = createTask(taskId, contextId)

        turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(task)
            throw IllegalStateException("boom")
        }.also { drain(it) }.join()
        runCurrent()

        val stored = assertNotNull(taskStorage.get(taskId))
        val failed = TaskStatusUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            status = failedStatus(taskId, contextId, stored.status.timestamp),
        )
        assertEquals(listOf<Pair<String, Event>>("c1" to task.withUserMessage(), "c1" to failed), pushSender.sent)
    }

    @Test
    fun testServerEventOnIdleTaskIsPushed() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        storeTask()
        savePushConfig()
        val canceled = statusUpdate(taskId, contextId, TaskState.TASK_STATE_CANCELED)

        turnManager.withTaskProcessor(taskId, contextId) { _, _, publish ->
            publish(canceled)
        }
        runCurrent()

        assertEquals(listOf<Pair<String, Event>>("c1" to canceled), pushSender.sent)
    }

    @Test
    fun testBlockedPushSenderDoesNotBlockAgentStreamsOrTurnEnd() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()
        savePushConfig()
        pushSender.block("c1")

        val received = mutableListOf<Event>()
        val subscriber = subscribeWith(turnManager) { received += it }
        runCurrent()

        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)
        val turn = turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(working)
            processor.sendTaskEvent(completed)
        }
        val requesterEvents = drain(turn)

        withTimeout(5.seconds) {
            turn.join()
            subscriber.join()
        }

        assertEquals(listOf<Event>(inputRequired, working, completed), received)
        assertEquals(listOf<Event>(working, completed), requesterEvents.await())
        assertEquals(0, turnManager.activeStreams())
        assertEquals(listOf<Pair<String, Event>>("c1" to working), pushSender.started)

        pushSender.unblock("c1")
        runCurrent()

        assertEquals(listOf<Pair<String, Event>>("c1" to working, "c1" to completed), pushSender.sent)
    }

    /** Starts the SubscribeToTask flow in a new coroutine. [onEvent] is called for every received event. */
    private fun TestScope.subscribeWith(
        turnManager: TurnManager,
        onEvent: suspend (Event) -> Unit,
    ): Job = launch {
        turnManager.subscribe(SubscribeToTaskRequest(taskId), callContext).collect { onEvent(it) }
    }

    @Test
    fun testSendTaskEventSuspendsUntilSubscriberConsumes() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        val gate = CompletableDeferred<Unit>()
        val received = mutableListOf<Event>()
        val subscriber = subscribeWith(turnManager) {
            received += it
            if (received.size == 1) gate.await() // holds the snapshot until released
        }
        runCurrent()

        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)
        val sent = mutableListOf<Event>()
        val turn = turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(working)
            sent += working
            processor.sendTaskEvent(completed)
            sent += completed
        }
        val requesterEvents = drain(turn)
        runCurrent()

        // The first event is persisted, but the agent is stuck until the subscriber consumes it.
        assertEquals(emptyList(), sent)
        assertEquals(inputRequired.copy(status = working.status), taskStorage.get(taskId))

        gate.complete(Unit)
        turn.join()
        subscriber.join()

        assertEquals(listOf<Event>(working, completed), sent)
        assertEquals(listOf<Event>(inputRequired, working, completed), received)
        assertEquals(listOf<Event>(working, completed), requesterEvents.await())
    }

    @Test
    fun testSlowSubscriberReceivesEveryEventInOrder() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        val received = mutableListOf<Event>()
        val subscriber = subscribeWith(turnManager) {
            received += it
            delay(1_000) // much slower than the agent
        }
        runCurrent()

        val updates = (0 until 200).map { statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING, "step-$it") }
        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)
        // The agent doesn't suspend between events on its own; only back-pressure paces it.
        val turn = turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            updates.forEach { processor.sendTaskEvent(it) }
            processor.sendTaskEvent(completed)
        }
        val requesterEvents = drain(turn)
        turn.join()
        subscriber.join()

        assertEquals(listOf<Event>(inputRequired) + updates + completed, received)
        assertEquals(updates + completed, requesterEvents.await())
    }

    @Test
    fun testCancellingStuckSubscriberUnblocksAgentAndOtherListeners() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val inputRequired = storeTask()

        val stuckReceived = mutableListOf<Event>()
        val stuck = subscribeWith(turnManager) {
            stuckReceived += it
            CompletableDeferred<Unit>().await() // never consumes anything more
        }
        val activeReceived = mutableListOf<Event>()
        val active = subscribeWith(turnManager) { activeReceived += it }
        runCurrent()

        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)
        val turn = turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(working)
            processor.sendTaskEvent(completed)
        }
        val requesterEvents = drain(turn)
        runCurrent()

        // The stuck collector, which holds the snapshot, blocks the agent and everybody else.
        assertEquals(listOf<Event>(inputRequired), stuckReceived)
        assertEquals(listOf<Event>(inputRequired), activeReceived)
        assertEquals(1, turnManager.activeTurns())

        stuck.cancelAndJoin()
        turn.join()
        active.join()

        assertEquals(listOf<Event>(inputRequired, working, completed), activeReceived)
        assertEquals(listOf<Event>(working, completed), requesterEvents.await())
        assertEquals(0, turnManager.activeTurns())
        assertEquals(0, turnManager.activeStreams())
    }

    @Test
    fun testCancellingRequesterViewUnblocksAgent() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val gate = CompletableDeferred<Unit>()
        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)

        val turn = turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(createTask(taskId, contextId, TaskState.TASK_STATE_WORKING))
            gate.await()
            processor.sendTaskEvent(working)
            processor.sendTaskEvent(completed)
        }
        // The requester goes away after the first event (e.g. returnImmediately, or the client disconnects).
        val firstEvent = turn.events.first()
        gate.complete(Unit)
        turn.join()

        assertEquals(createTask(taskId, contextId, TaskState.TASK_STATE_WORKING).withUserMessage(), firstEvent)
        assertEquals(TaskState.TASK_STATE_COMPLETED, taskStorage.get(taskId)?.status?.state)
        assertEquals(0, turnManager.activeStreams())
    }

    /**
     * Makes the stream of [taskId] stuck: the stream lock is held by a fan-out waiting for a subscriber that doesn't
     * consume. Returns the job of the stuck subscriber; cancelling it unblocks the fan-out.
     */
    private fun TestScope.stickStream(turnManager: TurnManager, taskId: String, contextId: String): Job {
        val stuck = launch {
            turnManager.subscribe(SubscribeToTaskRequest(taskId), callContext).collect {
                CompletableDeferred<Unit>().await() // never consumes anything more
            }
        }
        runCurrent()

        launch {
            turnManager.withTaskProcessor(taskId, contextId) { processor, _, _ ->
                processor.sendTaskEvent(statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING))
            }
        }
        runCurrent()

        return stuck
    }

    @Test
    fun testStuckFanOutOfOneTaskDoesNotBlockOtherTasks() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        val taskA = "task-a"
        val contextA = "context-a"
        val taskB = "task-b"
        val contextB = "context-b"
        storeTask(taskA, contextA)
        storeTask(taskB, contextB)

        val stuck = stickStream(turnManager, taskA, contextA)

        // A follow-up turn for A has to wait for A's stuck stream (whose publisher holds A's turn gate).
        val completedA = statusUpdate(taskA, contextA, TaskState.TASK_STATE_COMPLETED)
        val startA = async {
            turnManager.startTurn(createContext(taskA, contextA)) { processor ->
                processor.sendTaskEvent(completedA)
            }
        }
        runCurrent()
        assertEquals(false, startA.isCompleted)

        // The turn of A isn't visible until its view is registered.
        assertNull(turnManager.getTurn(taskA))

        // Everything for the other task, and the registry queries, go through while A is stuck.
        val messageB = createMessage("msg-b", contextB, "Hello B")
        val operationsB = async {
            val subscriptionB = turnManager.subscribe(SubscribeToTaskRequest(taskB), callContext).first()
            val turnB = turnManager.startTurn(createContext(taskB, contextB)) { processor ->
                processor.sendMessage(messageB)
            }
            val turnLookup = turnManager.getTurn(taskB)
            val eventsB = turnB.events.toList()
            turnB.join()

            listOf(subscriptionB, turnLookup === turnB, eventsB, turnManager.activeTurns())
        }
        runCurrent()

        assertEquals(true, operationsB.isCompleted)
        assertEquals(
            listOf(
                createTask(taskB, contextB, TaskState.TASK_STATE_INPUT_REQUIRED),
                true,
                listOf<Event>(messageB),
                0, // B has ended, and A is still starting
            ),
            operationsB.await()
        )
        assertEquals(false, startA.isCompleted)

        // Unblocking A lets everything finish.
        stuck.cancelAndJoin()
        val turnA = startA.await()
        assertSame(turnA, turnManager.getTurn(taskA))
        val eventsA = drain(turnA)
        turnA.join()

        assertEquals(listOf<Event>(completedA), eventsA.await())
        assertEquals(TaskState.TASK_STATE_COMPLETED, taskStorage.get(taskA)?.status?.state)
        assertEquals(0, turnManager.activeTurns())
        assertEquals(0, turnManager.activeStreams())
    }

    @Test
    fun testCancelledStartTurnReleasesReservation() = runTest(timeout = TEST_TIMEOUT) {
        val turnManager = createManager()
        storeTask()
        val stuck = stickStream(turnManager, taskId, contextId)

        var agentStarted = false
        val start = async {
            turnManager.startTurn(createContext(taskId, contextId)) { agentStarted = true }
        }
        runCurrent()
        assertEquals(false, start.isCompleted)
        assertNull(turnManager.getTurn(taskId))

        // The caller goes away while the turn is waiting to register its view.
        start.cancelAndJoin()

        assertNull(turnManager.getTurn(taskId))
        assertEquals(0, turnManager.activeTurns())

        // The task can start a new turn, and the cancelled one never ran.
        stuck.cancelAndJoin()
        val turn = turnManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED))
        }
        drain(turn)
        turn.join()

        assertEquals(false, agentStarted)
        assertEquals(0, turnManager.activeTurns())
        assertEquals(0, turnManager.activeStreams())
    }
}
