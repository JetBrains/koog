@file:OptIn(InternalA2AApi::class)

package ai.koog.a2a.server.session

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.exceptions.SessionNotActiveException
import ai.koog.a2a.server.messages.ContextMessageStorage
import ai.koog.a2a.server.messages.InMemoryMessageStorage
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.tasks.ContextTaskStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SessionManagerTest {
    private companion object Companion {
        private val TEST_TIMEOUT = 30.seconds
    }

    private lateinit var taskStorage: InMemoryTaskStorage
    private lateinit var pushConfigStorage: InMemoryPushNotificationConfigStorage
    private lateinit var pushSender: MockPushNotificationSender

    private val contextId = "context-1"
    private val taskId = "task-1"
    private val callContext = ServerCallContext()

    private class MockPushNotificationSender : PushNotificationSender {
        val sentNotifications = mutableListOf<Pair<TaskPushNotificationConfig, Event>>()

        override suspend fun send(config: TaskPushNotificationConfig, event: Event) {
            sentNotifications.add(config to event)
        }
    }

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
        pushConfigStorage = InMemoryPushNotificationConfigStorage()
        pushSender = MockPushNotificationSender()
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
        copy(history = listOf(createMessage("user-msg", this@SessionManagerTest.contextId, "Hello").copy(taskId = id)))

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

    /**
     * Scope for agent jobs: a supervisor, so that a failing agent doesn't fail the test.
     */
    private fun TestScope.agentScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))

    private object FixedMessageIdGenerator : IdGenerator {
        const val MESSAGE_ID = "generated-message-id"

        override suspend fun generateContextId(message: Message): String = error("Not expected")
        override suspend fun generateTaskId(message: Message): String = error("Not expected")
        override suspend fun generateMessageId(taskId: String, contextId: String): String = MESSAGE_ID
    }

    private fun TestScope.createManager() = SessionManager(
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
    private fun TestScope.drain(session: Session): Deferred<List<Event>> = async { session.events.toList() }

    private fun failedStatus() = TaskStatus(
        state = TaskState.TASK_STATE_FAILED,
        message = Message(
            messageId = FixedMessageIdGenerator.MESSAGE_ID,
            role = Role.ROLE_AGENT,
            parts = listOf(TextPart("Task failed due to an internal error")),
            contextId = contextId,
            taskId = taskId,
        ),
    )

    @Test
    fun testSessionManagerCreation() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()

        assertEquals(0, sessionManager.activeSessions())
        assertNull(sessionManager.getSession("any-task-id"))
    }

    @Test
    fun testMessageTurn() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val message = createMessage("msg-1", contextId, "Hello")

        val session = sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendMessage(message)
        }

        assertEquals(listOf<Event>(message), session.events.toList())
        session.join()

        // Session and task stream should be automatically cleaned up after completion
        assertEquals(0, sessionManager.activeSessions())
        assertEquals(0, sessionManager.activeStreams())
    }

    @Test
    fun testTaskTurn() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val gate = CompletableDeferred<Unit>()
        val task = createTask(taskId, contextId)
        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)

        val session = sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(task)
            gate.await()
            processor.sendTaskEvent(completed)
        }

        assertSame(session, sessionManager.getSession(taskId))
        assertEquals(1, sessionManager.activeSessions())

        gate.complete(Unit)

        assertEquals(listOf(task.withUserMessage(), completed), session.events.toList())
        session.join()

        assertEquals(0, sessionManager.activeSessions())
        assertEquals(0, sessionManager.activeStreams())
        assertEquals(task.withUserMessage().copy(status = completed.status), taskStorage.get(taskId))
    }

    @Test
    fun testStartTurnWhileTurnIsRunningFails() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val gate = CompletableDeferred<Unit>()

        val session = sessionManager.startTurn(createContext(taskId, contextId)) { gate.await() }

        assertFailsWith<IllegalStateException> {
            sessionManager.startTurn(createContext(taskId, contextId)) {}
        }

        gate.complete(Unit)
        session.join()
    }

    @Test
    fun testMultipleSessions() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val gate = CompletableDeferred<Unit>()

        fun turn(taskId: String, contextId: String): suspend (SessionEventProcessor) -> Unit = { processor ->
            processor.sendTaskEvent(createTask(taskId, contextId))
            gate.await()
            processor.sendTaskEvent(statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED))
        }

        val session1 = sessionManager.startTurn(createContext("task-1", "context-1"), turn("task-1", "context-1"))
        val session2 = sessionManager.startTurn(createContext("task-2", "context-2"), turn("task-2", "context-2"))
        drain(session1)
        drain(session2)

        assertSame(session1, sessionManager.getSession("task-1"))
        assertSame(session2, sessionManager.getSession("task-2"))
        assertEquals(2, sessionManager.activeSessions())

        gate.complete(Unit)
        session1.join()
        session2.join()

        // All sessions should be automatically cleaned up
        assertEquals(0, sessionManager.activeSessions())
        assertEquals(0, sessionManager.activeStreams())
    }

    @Test
    fun testFailedTurnMarksTaskFailed() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val task = createTask(taskId, contextId)

        val session = sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(task)
            throw IllegalStateException("boom")
        }

        val events = session.events.toList()
        assertFailsWith<IllegalStateException> { session.agentJob.await() }
        session.join()

        val stored = assertNotNull(taskStorage.get(taskId))
        val failedEvent = TaskStatusUpdateEvent(taskId = taskId, contextId = contextId, status = failedStatus().copy(timestamp = stored.status.timestamp))
        assertNotNull(stored.status.timestamp)
        assertEquals(failedEvent.status, stored.status)
        assertEquals(listOf(task.withUserMessage(), failedEvent), events)
    }

    @Test
    fun testFailedTurnWithoutTaskCreatesNothing() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()

        val session = sessionManager.startTurn(createContext(taskId, contextId)) {
            throw IllegalStateException("boom")
        }

        assertEquals(emptyList(), session.events.toList())
        assertFailsWith<IllegalStateException> { session.agentJob.await() }
        session.join()

        assertNull(taskStorage.get(taskId))
        assertEquals(0, sessionManager.activeStreams())
    }

    @Test
    fun testSubscribeFollowsTaskAcrossTurns() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val inputRequired = createTask(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(inputRequired)

        val received = mutableListOf<Event>()
        val snapshotReceived = CompletableDeferred<Unit>()
        val subscriber = launch {
            sessionManager.subscribe(SubscribeToTaskRequest(taskId), callContext).collect {
                received += it
                snapshotReceived.complete(Unit)
            }
        }
        snapshotReceived.await()

        // No turn is running, the subscriber alone keeps the stream.
        assertEquals(0, sessionManager.activeSessions())
        assertEquals(1, sessionManager.activeStreams())

        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val inputRequiredAgain = statusUpdate(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED)
        sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(working)
            processor.sendTaskEvent(inputRequiredAgain)
        }.also { drain(it) }.join()

        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)
        sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(completed)
        }.also { drain(it) }.join()

        subscriber.join()

        assertEquals(listOf<Event>(inputRequired, working, inputRequiredAgain, completed), received)
        assertEquals(0, sessionManager.activeStreams())
    }

    @Test
    fun testCancelledSubscriberReleasesStream() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        taskStorage.update(createTask(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED))

        val snapshotReceived = CompletableDeferred<Unit>()
        val subscriber = launch {
            sessionManager.subscribe(SubscribeToTaskRequest(taskId), callContext).collect {
                snapshotReceived.complete(Unit)
            }
        }
        snapshotReceived.await()
        assertEquals(1, sessionManager.activeStreams())

        subscriber.cancelAndJoin()

        assertEquals(0, sessionManager.activeStreams())
    }

    @Test
    fun testWithTaskProcessorOnIdleTaskReachesSubscribers() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val inputRequired = createTask(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(inputRequired)

        val received = mutableListOf<Event>()
        val snapshotReceived = CompletableDeferred<Unit>()
        val subscriber = launch {
            sessionManager.subscribe(SubscribeToTaskRequest(taskId), callContext).collect {
                received += it
                snapshotReceived.complete(Unit)
            }
        }
        snapshotReceived.await()

        val canceled = statusUpdate(taskId, contextId, TaskState.TASK_STATE_CANCELED)
        sessionManager.withTaskProcessor(taskId, contextId) { processor, agentJob ->
            assertNull(agentJob)
            processor.sendTaskEvent(canceled)
        }

        subscriber.join()
        assertEquals(listOf<Event>(inputRequired, canceled), received)
    }

    @Test
    fun testStandaloneProcessorIsClosedAfterWithTaskProcessor() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val inputRequired = createTask(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(inputRequired)

        val leaked = sessionManager.withTaskProcessor(taskId, contextId) { processor, _ -> processor }

        assertFailsWith<SessionNotActiveException> {
            leaked.sendTaskEvent(statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING))
        }
        assertEquals(inputRequired, taskStorage.get(taskId))
        assertEquals(0, sessionManager.activeStreams())
    }

    @Test
    fun testTurnStartedInCancelledScopeEndsWithoutRunningAgent() = runTest(timeout = TEST_TIMEOUT) {
        // The scope of a server that has been shut down.
        val cancelledScope = CoroutineScope(backgroundScope.coroutineContext + Job().apply { cancel() })
        val sessionManager = SessionManager(coroutineScope = cancelledScope, taskStorage = taskStorage)
        var agentStarted = false

        val session = sessionManager.startTurn(createContext(taskId, contextId)) { agentStarted = true }

        withTimeout(5.seconds) {
            assertEquals(emptyList(), session.events.toList())
            session.join()
        }
        assertFailsWith<CancellationException> { session.agentJob.await() }
        assertEquals(false, agentStarted)
        assertNull(sessionManager.getSession(taskId))
        assertEquals(0, sessionManager.activeSessions())
        assertEquals(0, sessionManager.activeStreams())
    }

    @Test
    fun testWithTaskProcessorKeepsRunningTurnOpen() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val task = createTask(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val taskSent = CompletableDeferred<Unit>()

        val session = sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(task)
            taskSent.complete(Unit)
            CompletableDeferred<Unit>().await() // runs until cancelled
        }
        val events = drain(session)
        taskSent.await()

        val canceled = statusUpdate(taskId, contextId, TaskState.TASK_STATE_CANCELED)
        sessionManager.withTaskProcessor(taskId, contextId) { processor, agentJob ->
            assertSame(session.agentJob, agentJob)
            agentJob?.cancelAndJoin()
            // Give the turn a chance to finish, if it (wrongly) could.
            advanceUntilIdle()
            processor.sendTaskEvent(canceled)
        }

        // The requester of the turn receives the events published by the cancellation.
        assertEquals(listOf(task.withUserMessage(), canceled), events.await())
        session.join()
        assertEquals(0, sessionManager.activeSessions())
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
        val sessionManager = createManager()
        val inputRequired = storeInputRequiredTask()
        val userMessage = createMessage("user-msg", contextId, "Hello").copy(taskId = taskId)
        var storedWhenAgentStarted: Task? = null
        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)

        val session = sessionManager.startTurn(createContext(taskId, contextId, inputRequired)) {
            storedWhenAgentStarted = taskStorage.get(taskId)
            it.sendTaskEvent(working)
        }
        drain(session)
        session.join()

        assertEquals(inputRequired, storedWhenAgentStarted)
        assertEquals(inputRequired, session.taskSnapshot)
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
        val sessionManager = createManager()
        val inputRequired = storeInputRequiredTask()

        val session = sessionManager.startTurn(createContext(taskId, contextId, inputRequired)) {}
        drain(session)
        session.join()

        assertEquals(inputRequired, taskStorage.get(taskId))
    }

    @Test
    fun testFollowUpTurnOfTaskThatBecameTerminalFails() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val inputRequired = storeInputRequiredTask()
        // The task is cancelled after the caller validated it.
        val canceled = createTask(taskId, contextId, TaskState.TASK_STATE_CANCELED)
        taskStorage.update(canceled)
        var agentStarted = false

        assertFailsWith<A2AUnsupportedOperationException> {
            sessionManager.startTurn(createContext(taskId, contextId, inputRequired)) { agentStarted = true }
        }
        advanceUntilIdle()

        assertEquals(false, agentStarted)
        assertNull(sessionManager.getSession(taskId))
        assertEquals(0, sessionManager.activeStreams())
        assertEquals(canceled, taskStorage.get(taskId))
    }

    @Test
    fun testSessionWithPushNotifications() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()

        // Configure push notification
        val config = TaskPushNotificationConfig(
            taskId = "task-1",
            id = "config-1",
            url = "https://example.com/webhook"
        )
        pushConfigStorage.save(config, ServerCallContext())

        val task = createTask("task-1", contextId)
        val completed = statusUpdate("task-1", contextId, TaskState.TASK_STATE_COMPLETED)

        sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(task)
            processor.sendTaskEvent(completed)
        }.also { drain(it) }.join()

        // Let the push notifications be sent after the turn.
        advanceUntilIdle()

        // Verify push notification was sent with the current task state
        assertEquals(listOf<Pair<TaskPushNotificationConfig, Event>>(config to task.copy(status = completed.status)), pushSender.sentNotifications)
    }

    /**
     * Starts the SubscribeToTask flow in a new coroutine. [onEvent] is called for every received event.
     */
    private fun TestScope.subscribeWith(
        sessionManager: SessionManager,
        onEvent: suspend (Event) -> Unit,
    ): Job = launch {
        sessionManager.subscribe(SubscribeToTaskRequest(taskId), callContext).collect { onEvent(it) }
    }

    @Test
    fun testSendTaskEventSuspendsUntilSubscriberConsumes() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val inputRequired = createTask(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(inputRequired)

        val gate = CompletableDeferred<Unit>()
        val received = mutableListOf<Event>()
        val subscriber = subscribeWith(sessionManager) {
            received += it
            if (received.size == 1) gate.await() // holds the snapshot until released
        }
        runCurrent()

        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)
        val sent = mutableListOf<Event>()
        val session = sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(working)
            sent += working
            processor.sendTaskEvent(completed)
            sent += completed
        }
        val requesterEvents = drain(session)
        runCurrent()

        // The first event is persisted, but the agent is stuck until the subscriber consumes it.
        assertEquals(emptyList(), sent)
        assertEquals(inputRequired.copy(status = working.status), taskStorage.get(taskId))

        gate.complete(Unit)
        session.join()
        subscriber.join()

        assertEquals(listOf<Event>(working, completed), sent)
        assertEquals(listOf<Event>(inputRequired, working, completed), received)
        assertEquals(listOf<Event>(working, completed), requesterEvents.await())
    }

    @Test
    fun testSlowSubscriberReceivesEveryEventInOrder() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val inputRequired = createTask(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(inputRequired)

        val received = mutableListOf<Event>()
        val subscriber = subscribeWith(sessionManager) {
            received += it
            delay(1_000) // much slower than the agent
        }
        runCurrent()

        val updates = (0 until 200).map { statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING, "step-$it") }
        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)
        // The agent doesn't suspend between events on its own; only back-pressure paces it.
        val session = sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            updates.forEach { processor.sendTaskEvent(it) }
            processor.sendTaskEvent(completed)
        }
        val requesterEvents = drain(session)
        session.join()
        subscriber.join()

        assertEquals(listOf<Event>(inputRequired) + updates + completed, received)
        assertEquals(updates + completed, requesterEvents.await())
    }

    @Test
    fun testCancellingStuckSubscriberUnblocksAgentAndOtherListeners() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val inputRequired = createTask(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(inputRequired)

        val stuckReceived = mutableListOf<Event>()
        val stuck = subscribeWith(sessionManager) {
            stuckReceived += it
            CompletableDeferred<Unit>().await() // never consumes anything more
        }
        val activeReceived = mutableListOf<Event>()
        val active = subscribeWith(sessionManager) { activeReceived += it }
        runCurrent()

        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)
        val session = sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(working)
            processor.sendTaskEvent(completed)
        }
        val requesterEvents = drain(session)
        runCurrent()

        // The stuck collector, which holds the snapshot, blocks the agent and everybody else.
        assertEquals(listOf<Event>(inputRequired), stuckReceived)
        assertEquals(listOf<Event>(inputRequired), activeReceived)
        assertEquals(1, sessionManager.activeSessions())

        stuck.cancelAndJoin()
        session.join()
        active.join()

        assertEquals(listOf<Event>(inputRequired, working, completed), activeReceived)
        assertEquals(listOf<Event>(working, completed), requesterEvents.await())
        assertEquals(0, sessionManager.activeSessions())
        assertEquals(0, sessionManager.activeStreams())
    }

    @Test
    fun testCancellingRequesterViewUnblocksAgent() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val gate = CompletableDeferred<Unit>()
        val working = statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)

        val session = sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(createTask(taskId, contextId, TaskState.TASK_STATE_WORKING))
            gate.await()
            processor.sendTaskEvent(working)
            processor.sendTaskEvent(completed)
        }
        // The requester goes away after the first event (e.g. returnImmediately, or the client disconnects).
        val firstEvent = session.events.first()
        gate.complete(Unit)
        session.join()

        assertEquals(createTask(taskId, contextId, TaskState.TASK_STATE_WORKING).withUserMessage(), firstEvent)
        assertEquals(TaskState.TASK_STATE_COMPLETED, taskStorage.get(taskId)?.status?.state)
        assertEquals(0, sessionManager.activeStreams())
    }

    /**
     * Makes the stream of [taskId] stuck: the stream lock is held by a fan-out waiting for a subscriber that doesn't
     * consume. Returns the job of the stuck subscriber; cancelling it unblocks the fan-out.
     */
    private fun TestScope.stickStream(sessionManager: SessionManager, taskId: String, contextId: String): Job {
        val stuck = launch {
            sessionManager.subscribe(SubscribeToTaskRequest(taskId), callContext).collect {
                CompletableDeferred<Unit>().await() // never consumes anything more
            }
        }
        runCurrent()

        launch {
            sessionManager.withTaskProcessor(taskId, contextId) { processor, _ ->
                processor.sendTaskEvent(statusUpdate(taskId, contextId, TaskState.TASK_STATE_WORKING))
            }
        }
        runCurrent()

        return stuck
    }

    @Test
    fun testStuckFanOutOfOneTaskDoesNotBlockOtherTasks() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val taskA = "task-a"
        val contextA = "context-a"
        val taskB = "task-b"
        val contextB = "context-b"
        taskStorage.update(createTask(taskA, contextA, TaskState.TASK_STATE_INPUT_REQUIRED))
        taskStorage.update(createTask(taskB, contextB, TaskState.TASK_STATE_INPUT_REQUIRED))

        val stuck = stickStream(sessionManager, taskA, contextA)

        // A follow-up turn for A has to wait for A's stuck stream (whose publisher holds A's turn gate).
        val completedA = statusUpdate(taskA, contextA, TaskState.TASK_STATE_COMPLETED)
        val startA = async {
            sessionManager.startTurn(createContext(taskA, contextA)) { processor ->
                processor.sendTaskEvent(completedA)
            }
        }
        runCurrent()
        assertEquals(false, startA.isCompleted)

        // The turn of A isn't visible until its view is registered.
        assertNull(sessionManager.getSession(taskA))

        // Everything for the other task, and the registry queries, go through while A is stuck.
        val messageB = createMessage("msg-b", contextB, "Hello B")
        val operationsB = async {
            val subscriptionB = sessionManager.subscribe(SubscribeToTaskRequest(taskB), callContext).first()
            val sessionB = sessionManager.startTurn(createContext(taskB, contextB)) { processor ->
                processor.sendMessage(messageB)
            }
            val sessionLookup = sessionManager.getSession(taskB)
            val eventsB = sessionB.events.toList()
            sessionB.join()

            listOf(subscriptionB, sessionLookup === sessionB, eventsB, sessionManager.activeSessions())
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
        val sessionA = startA.await()
        assertSame(sessionA, sessionManager.getSession(taskA))
        val eventsA = drain(sessionA)
        sessionA.join()

        assertEquals(listOf<Event>(completedA), eventsA.await())
        assertEquals(TaskState.TASK_STATE_COMPLETED, taskStorage.get(taskA)?.status?.state)
        assertEquals(0, sessionManager.activeSessions())
        assertEquals(0, sessionManager.activeStreams())
    }

    @Test
    fun testCancelledStartTurnReleasesReservation() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        taskStorage.update(createTask(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED))
        val stuck = stickStream(sessionManager, taskId, contextId)

        var agentStarted = false
        val start = async {
            sessionManager.startTurn(createContext(taskId, contextId)) { agentStarted = true }
        }
        runCurrent()
        assertEquals(false, start.isCompleted)
        assertNull(sessionManager.getSession(taskId))

        // The caller goes away while the turn is waiting to register its view.
        start.cancelAndJoin()

        assertNull(sessionManager.getSession(taskId))
        assertEquals(0, sessionManager.activeSessions())

        // The task can start a new turn, and the cancelled one never ran.
        stuck.cancelAndJoin()
        val session = sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED))
        }
        drain(session)
        session.join()

        assertEquals(false, agentStarted)
        assertEquals(0, sessionManager.activeSessions())
        assertEquals(0, sessionManager.activeStreams())
    }
}
