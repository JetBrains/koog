package ai.koog.a2a.server.session

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
import ai.koog.a2a.server.messages.ContextMessageStorage
import ai.koog.a2a.server.messages.InMemoryMessageStorage
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.tasks.ContextTaskStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

    private fun statusUpdate(
        taskId: String,
        contextId: String,
        state: TaskState,
    ) = TaskStatusUpdateEvent(
        taskId = taskId,
        contextId = contextId,
        status = TaskStatus(state = state, timestamp = Instant.parse("2023-01-01T10:00:01Z"))
    )

    private fun createContext(
        taskId: String,
        contextId: String,
    ) = RequestContext(
        callContext = callContext,
        params = SendMessageRequest(createMessage("user-msg", contextId, "Hello")),
        taskStorage = ContextTaskStorage(contextId, taskStorage),
        messageStorage = ContextMessageStorage(contextId, InMemoryMessageStorage()),
        contextId = contextId,
        taskId = taskId,
        task = null,
    )

    /**
     * Scope for agent jobs: a supervisor, so that a failing agent doesn't fail the test.
     */
    private fun TestScope.agentScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))

    private fun TestScope.createManager() = SessionManager(
        coroutineScope = agentScope(),
        taskStorage = taskStorage,
        pushConfigStorage = pushConfigStorage,
        pushSender = pushSender,
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

        assertEquals(listOf(task, completed), session.events.toList())
        session.join()

        assertEquals(0, sessionManager.activeSessions())
        assertEquals(0, sessionManager.activeStreams())
        assertEquals(task.copy(status = completed.status), taskStorage.get(taskId))
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
        assertEquals(TaskState.TASK_STATE_FAILED, stored.status.state)
        assertNotNull(stored.status.timestamp)
        assertEquals(
            listOf(task, TaskStatusUpdateEvent(taskId = taskId, contextId = contextId, status = stored.status)),
            events
        )
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
        }.join()

        val completed = statusUpdate(taskId, contextId, TaskState.TASK_STATE_COMPLETED)
        sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(completed)
        }.join()

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
    fun testWithTaskProcessorKeepsRunningTurnOpen() = runTest(timeout = TEST_TIMEOUT) {
        val sessionManager = createManager()
        val task = createTask(taskId, contextId, TaskState.TASK_STATE_WORKING)
        val taskSent = CompletableDeferred<Unit>()

        val session = sessionManager.startTurn(createContext(taskId, contextId)) { processor ->
            processor.sendTaskEvent(task)
            taskSent.complete(Unit)
            CompletableDeferred<Unit>().await() // runs until cancelled
        }
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
        assertEquals(listOf(task, canceled), session.events.toList())
        session.join()
        assertEquals(0, sessionManager.activeSessions())
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
        }.join()

        // Let the push notifications be sent after the turn.
        advanceUntilIdle()

        // Verify push notification was sent with the current task state
        assertEquals(listOf<Pair<TaskPushNotificationConfig, Event>>(config to task.copy(status = completed.status)), pushSender.sentNotifications)
    }
}
