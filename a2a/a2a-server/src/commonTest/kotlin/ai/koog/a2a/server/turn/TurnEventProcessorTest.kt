package ai.koog.a2a.server.turn

import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.exceptions.TurnNotActiveException
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TurnEventProcessorTest {
    private companion object {
        private val TEST_TIMEOUT = 30.seconds
    }

    private lateinit var taskStorage: InMemoryTaskStorage
    private lateinit var stream: TaskEventStream
    private val contextId = "test-context-1"
    private val taskId = "task-1"

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
        stream = TaskEventStream(taskId, taskStorage, UuidIdGenerator)
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

    private fun statusUpdate(state: TaskState) = TaskStatusUpdateEvent(
        taskId = taskId,
        contextId = contextId,
        status = TaskStatus(state = state),
    )

    private fun createProcessor(
        contextId: String,
        taskId: String,
        taskExists: Boolean = false,
    ): TurnEventProcessor = TurnEventProcessor(
        contextId = contextId,
        taskId = taskId,
        stream = stream,
        taskExists = taskExists,
    )

    /**
     * Starts collecting all events of [channel]. Delivery is back-pressured, so every view must be consumed while
     * events are sent.
     */
    private fun TestScope.collect(channel: ReceiveChannel<Event>): Deferred<List<Event>> = async { channel.toList() }

    /**
     * Ends the turn and returns all events its view received.
     */
    private suspend fun Deferred<List<Event>>.endTurnAndCollect(): List<Event> {
        stream.endTurn(contextId, outcome = TurnOutcome.Completed(rejection = null))
        return await()
    }

    @Test
    fun testEventsWithInvalidContextIdAreRejected() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)

        assertFailsWith<InvalidEventException> {
            processor.sendMessage(createMessage("msg-1", "different-context", "Hello"))
        }
        assertFailsWith<InvalidEventException> {
            processor.sendTaskEvent(createTask(taskId, "different-context"))
        }

        processor.close()
    }

    @Test
    fun testEventsAfterMessageFail() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)

        processor.sendMessage(createMessage("msg-1", contextId, "Hello"))

        assertFailsWith<TurnNotActiveException> {
            processor.sendMessage(createMessage("msg-2", contextId, "World"))
        }
        assertFailsWith<TurnNotActiveException> {
            processor.sendTaskEvent(createTask(taskId, contextId))
        }
    }

    // Task turn tests

    @Test
    fun testSendTaskEventNewTask() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val task = createTask(taskId, contextId)

        // Register the turn view before sending
        val turnView = collect(stream.openTurnView())

        processor.sendTaskEvent(task)

        // Verify task was stored and delivered
        assertEquals(task, taskStorage.get(taskId))
        assertEquals(listOf(task), turnView.endTurnAndCollect())
    }

    @Test
    fun testSendTaskEventWithExistingTask() = runTest(timeout = TEST_TIMEOUT) {
        // Store a task first
        val existingTask = createTask(taskId, contextId)
        taskStorage.update(existingTask)

        // Create processor with existing task
        val processor = createProcessor(contextId, taskId)

        val statusUpdate = statusUpdate(TaskState.TASK_STATE_WORKING)

        processor.sendTaskEvent(statusUpdate)
        processor.close()

        // Verify event was processed
        assertEquals(existingTask.copy(status = statusUpdate.status), taskStorage.get(taskId))
    }

    @Test
    fun testMessageRejectedWhenTaskExists() = runTest(timeout = TEST_TIMEOUT) {
        val existingTask = createTask(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(existingTask)

        val processor = createProcessor(contextId, taskId, taskExists = true)
        val turnView = collect(stream.openTurnView())

        assertFailsWith<InvalidEventException> {
            processor.sendMessage(createMessage("msg-1", contextId, "Hello"))
        }

        // The processor still accepts updates of the existing task without a Task first.
        val statusUpdate = statusUpdate(TaskState.TASK_STATE_WORKING)
        processor.sendTaskEvent(statusUpdate)

        assertEquals(listOf(statusUpdate), turnView.endTurnAndCollect())
        assertEquals(existingTask.copy(status = statusUpdate.status), taskStorage.get(taskId))
    }

    @Test
    fun testTaskEventForExistingTaskIsIgnored() = runTest(timeout = TEST_TIMEOUT) {
        val existingTask = createTask(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED)
        taskStorage.update(existingTask)

        val processor = createProcessor(contextId, taskId, taskExists = true)
        val turnView = collect(stream.openTurnView())

        // Even a terminal Task is not applied, and doesn't close the processor.
        processor.sendTaskEvent(createTask(taskId, contextId, TaskState.TASK_STATE_COMPLETED))

        assertEquals(existingTask, taskStorage.get(taskId))

        val statusUpdate = statusUpdate(TaskState.TASK_STATE_WORKING)
        processor.sendTaskEvent(statusUpdate)

        assertEquals(listOf(statusUpdate), turnView.endTurnAndCollect())
    }

    @Test
    fun testNewTaskGetsUserMessageInHistory() = runTest(timeout = TEST_TIMEOUT) {
        val userMessage = createMessage("user-1", contextId, "Hello")
        val processor = TurnEventProcessor(
            contextId = contextId,
            taskId = taskId,
            stream = stream,
            userMessage = userMessage,
        )
        val turnView = collect(stream.openTurnView())

        processor.sendTaskEvent(createTask(taskId, contextId))

        val expected = createTask(taskId, contextId).copy(history = listOf(userMessage.copy(taskId = taskId)))
        assertEquals(expected, taskStorage.get(taskId))
        assertEquals(listOf(expected), turnView.endTurnAndCollect())
    }

    @Test
    fun testSendEventAfterFinalEventFails() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val task = createTask(taskId, contextId)
        val finalStatusUpdate = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        val anotherEvent = TaskArtifactUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            artifact = Artifact(
                artifactId = "artifact-1",
                parts = listOf(TextPart("content"))
            ),
            append = false
        )

        processor.sendTaskEvent(task)
        processor.sendTaskEvent(finalStatusUpdate)

        assertFailsWith<TurnNotActiveException> {
            processor.sendTaskEvent(anotherEvent)
        }
    }

    @Test
    fun testSendMessageAfterTaskEventFails() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val task = createTask(taskId, contextId)
        val message = createMessage("msg-1", contextId, "Hello")

        processor.sendTaskEvent(task)

        assertFailsWith<InvalidEventException> {
            processor.sendMessage(message)
        }

        processor.close()
    }

    @Test
    fun testCompleteTaskLifecycle() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)

        // Create task
        val task = createTask(taskId, contextId)
        processor.sendTaskEvent(task)

        // Update status to working
        val workingUpdate = statusUpdate(TaskState.TASK_STATE_WORKING)
        processor.sendTaskEvent(workingUpdate)

        // Add artifact
        val artifact = Artifact(
            artifactId = "artifact-1",
            parts = listOf(TextPart("result"))
        )
        val artifactEvent = TaskArtifactUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            artifact = artifact,
            append = false
        )
        processor.sendTaskEvent(artifactEvent)

        // Complete task
        val completedUpdate = statusUpdate(TaskState.TASK_STATE_COMPLETED)
        processor.sendTaskEvent(completedUpdate)

        processor.close()

        // Verify final state
        val finalTask = taskStorage.get(taskId, includeArtifacts = true)
        assertEquals(TaskState.TASK_STATE_COMPLETED, finalTask?.status?.state)
        assertEquals(listOf(artifact), finalTask?.artifacts)
    }

    // Concurrent scenarios

    @Test
    fun testMessageIsDeliveredOnlyToTurnView() = runTest(timeout = TEST_TIMEOUT) {
        taskStorage.update(createTask(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED))
        val processor = createProcessor(contextId, taskId)
        val message = createMessage("msg-1", contextId, "Hello")

        val turnView = collect(stream.openTurnView())
        val (_, taskSubscriber) = stream.subscribe { taskStorage.get(taskId) }

        processor.sendMessage(message)

        assertEquals(listOf(message), turnView.endTurnAndCollect(), "Turn view should receive the message")
        // Delivery would have suspended on the rendezvous channel, so there is nothing pending for the subscriber.
        assertTrue(taskSubscriber.tryReceive().isFailure, "Task subscriber should not receive the message")
    }

    @Test
    fun testClosedProcessorSendMessageFailsAndEventStreamIsEmpty() =
        runTest(timeout = TEST_TIMEOUT) {
            val processor = createProcessor(contextId, taskId)
            val message1 = createMessage("msg-1", contextId, "Hello")
            val message2 = createMessage("msg-2", contextId, "World")

            val turnView = collect(stream.openTurnView())

            // Send first message
            processor.sendMessage(message1)

            // Close processor and then attempt to send more events
            processor.close()

            assertFailsWith<TurnNotActiveException>("Should not be possible to send events to closed turn") {
                processor.sendMessage(message2)
            }

            assertEquals(listOf(message1), turnView.endTurnAndCollect(), "Only the first message should be delivered")
        }

    @Test
    fun testClosedProcessorSendTaskEventFailsAndEventStreamIsEmpty() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val task = createTask(taskId, contextId)
        val turnView = collect(stream.openTurnView())

        // Send first task event
        processor.sendTaskEvent(task)

        // Close processor and then attempt to send more events
        processor.close()

        val workingUpdate = statusUpdate(TaskState.TASK_STATE_WORKING)

        assertFailsWith<TurnNotActiveException>("Should not be possible to send events to closed turn") {
            processor.sendTaskEvent(workingUpdate)
        }

        assertEquals(listOf(task), turnView.endTurnAndCollect(), "Only the first event should be delivered")
        assertEquals(task, taskStorage.get(taskId))
    }
}
