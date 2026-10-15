package ai.koog.a2a.server.session

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
import ai.koog.a2a.server.exceptions.SessionNotActiveException
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SessionEventProcessorTest {
    private companion object {
        private val TEST_TIMEOUT = 30.seconds
    }

    private lateinit var taskStorage: InMemoryTaskStorage
    private lateinit var stream: TaskEventStream
    private val contextId = "test-context-1"
    private val taskId = "task-1"
    private val turnId = 1L

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
        stream = TaskEventStream(taskId, taskStorage, bufferCapacity = 64)
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

    private fun createProcessor(
        contextId: String,
        taskId: String,
    ): SessionEventProcessor = SessionEventProcessor(
        contextId = contextId,
        taskId = taskId,
        stream = stream,
        turnId = turnId,
    )

    /**
     * Ends the turn and returns all events its view received.
     */
    private suspend fun ReceiveChannel<Event>.endTurnAndCollect(): List<Event> {
        stream.endTurn(turnId, contextId, failure = null)
        return toList()
    }

    @Test
    fun message_testSendMessageWithInvalidContextId() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val message = createMessage("msg-1", "different-context", "Hello")

        assertFailsWith<InvalidEventException> {
            processor.sendMessage(message)
        }

        processor.close()
    }

    @Test
    fun message_testSendSecondMessageFails() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val message1 = createMessage("msg-1", contextId, "Hello")
        val message2 = createMessage("msg-2", contextId, "World")

        processor.sendMessage(message1)

        assertFailsWith<SessionNotActiveException> {
            processor.sendMessage(message2)
        }
        assertFalse(processor.isOpen)
    }

    @Test
    fun message_testSendTaskEventAfterMessageFails() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val message = createMessage("msg-1", contextId, "Hello")
        val task = createTask(taskId, contextId)

        processor.sendMessage(message)

        assertFailsWith<SessionNotActiveException> {
            processor.sendTaskEvent(task)
        }
        assertFalse(processor.isOpen)
    }

    // Task session tests

    @Test
    fun task_testSendTaskEventNewTask() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val task = createTask(taskId, contextId)

        // Register the turn view before sending
        val turnView = stream.openTurnView(turnId)

        processor.sendTaskEvent(task)

        // Verify task was stored and delivered
        assertEquals(task, taskStorage.get(taskId))
        assertEquals(listOf(task), turnView.endTurnAndCollect())
    }

    @Test
    fun task_testSendTaskEventWithExistingTask() = runTest(timeout = TEST_TIMEOUT) {
        // Store a task first
        val existingTask = createTask(taskId, contextId)
        taskStorage.update(existingTask)

        // Create processor with existing task
        val processor = createProcessor(contextId, taskId)

        val statusUpdate = TaskStatusUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            status = TaskStatus(state = TaskState.TASK_STATE_WORKING)
        )

        processor.sendTaskEvent(statusUpdate)
        processor.close()

        // Verify event was processed
        assertEquals(existingTask.copy(status = statusUpdate.status), taskStorage.get(taskId))
    }

    @Test
    fun task_testSendTaskEventWithInvalidContextId() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val task = createTask(taskId, "different-context")

        assertFailsWith<InvalidEventException> {
            processor.sendTaskEvent(task)
        }

        processor.close()
    }

    @Test
    fun task_testSendEventAfterFinalEventFails() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val task = createTask(taskId, contextId)
        val finalStatusUpdate = TaskStatusUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            status = TaskStatus(state = TaskState.TASK_STATE_COMPLETED)
        )
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

        assertFailsWith<SessionNotActiveException> {
            processor.sendTaskEvent(anotherEvent)
        }
        assertFalse(processor.isOpen)
    }

    @Test
    fun task_testSendMessageAfterTaskEventFails() = runTest(timeout = TEST_TIMEOUT) {
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
    fun task_testTaskArtifactUpdateEvent() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val task = createTask(taskId, contextId)
        val artifact = Artifact(
            artifactId = "artifact-1",
            parts = listOf(TextPart("content"))
        )
        val artifactEvent = TaskArtifactUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            artifact = artifact,
            append = false
        )

        processor.sendTaskEvent(task)
        processor.sendTaskEvent(artifactEvent)
        processor.close()

        // Verify artifact was stored
        val storedTask = taskStorage.get("task-1", includeArtifacts = true)
        assertEquals(listOf(artifact), storedTask?.artifacts)
    }

    @Test
    fun task_testCompleteTaskLifecycle() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)

        // Create task
        val task = createTask(taskId, contextId)
        processor.sendTaskEvent(task)

        // Update status to working
        val workingUpdate = TaskStatusUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            status = TaskStatus(state = TaskState.TASK_STATE_WORKING)
        )
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
        val completedUpdate = TaskStatusUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            status = TaskStatus(state = TaskState.TASK_STATE_COMPLETED)
        )
        processor.sendTaskEvent(completedUpdate)

        processor.close()

        // Verify final state
        val finalTask = taskStorage.get(taskId, includeArtifacts = true)
        assertEquals(TaskState.TASK_STATE_COMPLETED, finalTask?.status?.state)
        assertEquals(listOf(artifact), finalTask?.artifacts)
    }

    // Concurrent scenarios

    @Test
    fun message_testMessageIsDeliveredOnlyToOwnTurnView() = runTest(timeout = TEST_TIMEOUT) {
        taskStorage.update(createTask(taskId, contextId, TaskState.TASK_STATE_INPUT_REQUIRED))
        val processor = createProcessor(contextId, taskId)
        val message = createMessage("msg-1", contextId, "Hello")

        val turnView = stream.openTurnView(turnId)
        val otherTurnView = stream.openTurnView(turnId + 1)
        val (_, taskSubscriber) = stream.subscribe { taskStorage.get(taskId) }

        processor.sendMessage(message)
        stream.endTurn(turnId + 1, contextId, failure = null)
        stream.unsubscribe(taskSubscriber)

        assertEquals(listOf(message), turnView.endTurnAndCollect(), "Own turn view should receive the message")
        assertEquals(emptyList(), otherTurnView.toList(), "Other turn view should not receive the message")
        assertEquals(emptyList(), taskSubscriber.toList(), "Task subscriber should not receive the message")
    }

    @Test
    fun concurrent_message_testClosedProcessorSendMessageFailsAndEventStreamIsEmpty() =
        runTest(timeout = TEST_TIMEOUT) {
            val processor = createProcessor(contextId, taskId)
            val message1 = createMessage("msg-1", contextId, "Hello")
            val message2 = createMessage("msg-2", contextId, "World")

            val turnView = stream.openTurnView(turnId)

            // Send first message
            processor.sendMessage(message1)

            // Close processor and then attempt to send more events
            processor.close()

            assertFailsWith<SessionNotActiveException>("Should not be possible to send events to closed session") {
                processor.sendMessage(message2)
            }

            assertEquals(listOf(message1), turnView.endTurnAndCollect(), "Only the first message should be delivered")
            assertFalse(processor.isOpen)
        }

    @Test
    fun concurrent_task_testClosedProcessorSendTaskEventFailsAndEventStreamIsEmpty() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val task = createTask(taskId, contextId)
        val turnView = stream.openTurnView(turnId)

        // Send first task event
        processor.sendTaskEvent(task)

        // Close processor and then attempt to send more events
        processor.close()

        val workingUpdate = TaskStatusUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            status = TaskStatus(state = TaskState.TASK_STATE_WORKING)
        )

        assertFailsWith<SessionNotActiveException>("Should not be possible to send events to closed session") {
            processor.sendTaskEvent(workingUpdate)
        }

        assertEquals(listOf(task), turnView.endTurnAndCollect(), "Only the first event should be delivered")
        assertEquals(task, taskStorage.get(taskId))
        assertFalse(processor.isOpen)
    }
}
