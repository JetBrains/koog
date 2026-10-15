package ai.koog.a2a.server.session

import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TaskEventStreamTest {
    private companion object {
        private val TEST_TIMEOUT = 30.seconds
    }

    private val taskId = "task-1"
    private val contextId = "context-1"

    private lateinit var taskStorage: InMemoryTaskStorage

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
    }

    private fun createStream(bufferCapacity: Int = 64) = TaskEventStream(taskId, taskStorage, bufferCapacity)

    private fun task(state: TaskState) = Task(
        id = taskId,
        contextId = contextId,
        status = TaskStatus(state = state, timestamp = Instant.parse("2025-01-01T10:00:00Z")),
    )

    private fun statusUpdate(state: TaskState, text: String = state.name) = TaskStatusUpdateEvent(
        taskId = taskId,
        contextId = contextId,
        status = TaskStatus(
            state = state,
            message = Message(
                messageId = "msg-$text",
                role = Role.ROLE_AGENT,
                parts = listOf(TextPart(text)),
                contextId = contextId,
                taskId = taskId,
            ),
            timestamp = Instant.parse("2025-01-01T10:00:01Z"),
        ),
    )

    private suspend fun TaskEventStream.subscribeToStored(): Pair<Task, ReceiveChannel<Event>> =
        subscribe { taskStorage.get(taskId) }

    @Test
    fun testSubscribeReturnsSnapshotAndSubsequentEventsUntilTerminal() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        val inputRequired = task(TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(inputRequired)

        val (snapshot, channel) = stream.subscribeToStored()

        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        stream.publish(working, turnId = 1)
        stream.publish(completed, turnId = 1)

        assertEquals(inputRequired, snapshot)
        // The channel is closed after the terminal event, so toList completes.
        assertEquals(listOf(working, completed), channel.toList())
    }

    @Test
    fun testSubscribeToUnknownTaskFails() = runTest(timeout = TEST_TIMEOUT) {
        assertFailsWith<A2ATaskNotFoundException> {
            createStream().subscribeToStored()
        }
    }

    @Test
    fun testSubscribeToTerminalTaskFails() = runTest(timeout = TEST_TIMEOUT) {
        taskStorage.update(task(TaskState.TASK_STATE_COMPLETED))

        assertFailsWith<A2AUnsupportedOperationException> {
            createStream().subscribeToStored()
        }
    }

    @Test
    fun testEndTurnClosesOnlyItsTurnView() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))

        val turnView = stream.openTurnView(1)
        val (_, subscriber) = stream.subscribeToStored()

        val inputRequired = statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED)
        stream.publish(inputRequired, turnId = 1)
        stream.endTurn(1, contextId, failure = null)

        assertEquals(listOf(inputRequired), turnView.toList())

        // The task subscriber stays open across turns and receives the next turn's events.
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        stream.publish(completed, turnId = 2)

        assertEquals(listOf(inputRequired, completed), subscriber.toList())
    }

    @Test
    fun testEndTurnWithFailureMarksTaskFailedAndClosesAllSubscribers() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))

        val turnView = stream.openTurnView(1)
        val (_, subscriber) = stream.subscribeToStored()

        stream.endTurn(1, contextId, failure = IllegalStateException("boom"))

        val stored = assertNotNull(taskStorage.get(taskId))
        assertEquals(TaskState.TASK_STATE_FAILED, stored.status.state)
        assertNotNull(stored.status.timestamp)

        val failed = TaskStatusUpdateEvent(taskId = taskId, contextId = contextId, status = stored.status)
        assertEquals(listOf(failed), turnView.toList())
        assertEquals(listOf(failed), subscriber.toList())
    }

    @Test
    fun testEndTurnWithFailureKeepsTerminalTask() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        val completed = task(TaskState.TASK_STATE_COMPLETED)
        taskStorage.update(completed)
        val turnView = stream.openTurnView(1)

        stream.endTurn(1, contextId, failure = IllegalStateException("boom"))

        assertEquals(completed, taskStorage.get(taskId))
        assertEquals(emptyList(), turnView.toList())
    }

    @Test
    fun testEndTurnWithCancellationDoesNotMarkTaskFailed() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream()
        val working = task(TaskState.TASK_STATE_WORKING)
        taskStorage.update(working)
        val turnView = stream.openTurnView(1)

        stream.endTurn(1, contextId, failure = CancellationException("canceled"))

        assertEquals(working, taskStorage.get(taskId))
        assertEquals(emptyList(), turnView.toList())
    }

    @Test
    fun testSlowSubscriberIsEvictedWithoutAffectingOthers() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream(bufferCapacity = 1)
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))

        val (_, slow) = stream.subscribeToStored()
        val (_, fast) = stream.subscribeToStored()

        val first = statusUpdate(TaskState.TASK_STATE_WORKING, "first")
        val second = statusUpdate(TaskState.TASK_STATE_WORKING, "second")
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)

        stream.publish(first, turnId = 1)
        assertEquals(first, fast.receive())

        // The slow subscriber hasn't consumed the first event, its buffer is full.
        stream.publish(second, turnId = 1)
        assertEquals(second, fast.receive())

        stream.publish(completed, turnId = 1)

        assertEquals(listOf(completed), fast.toList())
        // The slow subscriber still gets the buffered event, then the error.
        assertEquals(first, slow.receive())
        assertFailsWith<A2AInternalErrorException> { slow.receive() }
    }

    @Test
    fun testCancelledSubscriberDoesNotAffectOthers() = runTest(timeout = TEST_TIMEOUT) {
        val stream = createStream(bufferCapacity = 1)
        taskStorage.update(task(TaskState.TASK_STATE_WORKING))

        val (_, cancelled) = stream.subscribeToStored()
        val (_, active) = stream.subscribeToStored()

        cancelled.cancel()

        val working = statusUpdate(TaskState.TASK_STATE_WORKING)
        val completed = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        stream.publish(working, turnId = 1)
        assertEquals(working, active.receive())
        stream.publish(completed, turnId = 1)

        assertEquals(listOf(completed), active.toList())
    }
}
