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
import ai.koog.a2a.server.exceptions.SessionClosedException
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class SessionEventProcessorTest {
    private companion object {
        private val TEST_TIMEOUT = 5.seconds
    }

    private lateinit var taskStorage: InMemoryTaskStorage
    private val contextId = "test-context-1"
    private val taskId = "task-1"

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
    }

    private fun createMessage(
        messageId: String,
        contextId: String,
        content: String
    ) = Message(
        messageId = messageId,
        role = Role.User,
        parts = listOf(TextPart(content)),
        contextId = contextId
    )

    private fun createTask(
        id: String,
        contextId: String,
        state: TaskState = TaskState.Submitted
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
        task: Task? = null
    ) = SessionEventProcessor(
        contextId = contextId,
        taskId = taskId,
        taskStorage = taskStorage,
        task = task
    )

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

        assertFailsWith<InvalidEventException> {
            processor.sendMessage(message2)
        }

        processor.close()
    }

    @Test
    fun message_testSendTaskEventAfterMessageFails() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val message = createMessage("msg-1", contextId, "Hello")
        val task = createTask(taskId, contextId)

        processor.sendMessage(message)

        assertFailsWith<InvalidEventException> {
            processor.sendTaskEvent(task)
        }

        processor.close()
    }

    // Task session tests

    @Test
    fun task_testSendTaskEventNewTask() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val task = createTask(taskId, contextId)

        // Start collecting events before sending
        val events = mutableListOf<Event>()
        val eventsJob = launch {
            processor.events.collect {
                events.add(it)
            }
        }

        // Let the eventJob job actually start
        yield()

        processor.sendTaskEvent(task)
        processor.close()

        // Wait for event collection to complete
        eventsJob.join()

        // Verify task was stored and collected
        val storedTask = taskStorage.get(taskId)

        assertEquals(task, storedTask)
        assertEquals(listOf(task), events.toList())
    }

    @Test
    fun task_testSendTaskEventWithExistingTask() = runTest(timeout = TEST_TIMEOUT) {
        // Store a task first
        val existingTask = createTask(taskId, contextId)
        taskStorage.update(existingTask)

        // Create processor with existing task
        val processor = createProcessor(contextId, taskId, existingTask)

        val statusUpdate = TaskStatusUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            status = TaskStatus(state = TaskState.Working),
            final = false
        )

        processor.sendTaskEvent(statusUpdate)
        processor.close()

        // Verify event was processed
        val updatedTask = taskStorage.get(taskId)
        assertEquals(statusUpdate.status, updatedTask?.status)
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
    fun task_testSendNonTaskEventForNewTaskFails() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val statusUpdate = TaskStatusUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            status = TaskStatus(state = TaskState.Working),
            final = false
        )

        assertFailsWith<InvalidEventException> {
            processor.sendTaskEvent(statusUpdate)
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
            status = TaskStatus(state = TaskState.Completed),
            final = true
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

        assertFailsWith<InvalidEventException> {
            processor.sendTaskEvent(anotherEvent)
        }

        processor.close()
    }

    @Test
    fun task_testSendEventWhenTaskInTerminalStateFails() = runTest(timeout = TEST_TIMEOUT) {
        // Create task in terminal state
        val completedTask = createTask(taskId, contextId, TaskState.Completed)
        taskStorage.update(completedTask)

        val processor = createProcessor(contextId, taskId)

        val artifactEvent = TaskArtifactUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            artifact = Artifact(
                artifactId = "artifact-1",
                parts = listOf(TextPart("content"))
            ),
            append = false
        )

        assertFailsWith<InvalidEventException> {
            processor.sendTaskEvent(artifactEvent)
        }

        processor.close()
    }

    @Test
    fun task_testTerminalStatusUpdateWithoutFinalFlagFails() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val task = createTask(taskId, contextId)
        val statusUpdate = TaskStatusUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            status = TaskStatus(state = TaskState.Failed),
            final = false // This should be true for terminal state
        )

        processor.sendTaskEvent(task)

        assertFailsWith<InvalidEventException> {
            processor.sendTaskEvent(statusUpdate)
        }

        processor.close()
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
            status = TaskStatus(state = TaskState.Working),
            final = false
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
            status = TaskStatus(state = TaskState.Completed),
            final = true
        )
        processor.sendTaskEvent(completedUpdate)

        processor.close()

        // Verify final state
        val finalTask = taskStorage.get(taskId, includeArtifacts = true)
        assertEquals(TaskState.Completed, finalTask?.status?.state)
        assertEquals(listOf(artifact), finalTask?.artifacts)
    }

    // Concurrent scenarios

    @Test
    fun concurrent_message_testSendMessageBroadcast() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val message = createMessage("msg-1", contextId, "Hello")

        fun collectionJob(events: MutableList<Event>) = launch {
            processor.events.collect {
                events.add(it)
            }
        }

        // Set up two collectors to test that events are broadcasted properly
        val eventsOne = mutableListOf<Event>()
        val eventsJobOne = collectionJob(eventsOne)

        val eventsTwo = mutableListOf<Event>()
        val eventsJobTwo = collectionJob(eventsTwo)

        // Let event jobs actually start
        yield()

        processor.sendMessage(message)
        processor.close()

        eventsJobOne.join()
        eventsJobTwo.join()

        assertEquals(listOf(message), eventsOne.toList(), "First collector should collect the message")
        assertEquals(listOf(message), eventsTwo.toList(), "Second collector should collect the message")
    }

    @Test
    fun concurrent_message_testClosedProcessorSendMessageFailsAndEventStreamIsEmpty() =
        runTest(timeout = TEST_TIMEOUT) {
            val processor = createProcessor(contextId, taskId)
            val message1 = createMessage("msg-1", contextId, "Hello")
            val message2 = createMessage("msg-2", contextId, "World")

            // Send first message
            processor.sendMessage(message1)

            // Close processor and then attempt to send more events
            processor.close()

            assertFailsWith<SessionClosedException>("Should not be possible to send events to closed session") {
                processor.sendMessage(message2)
            }

            assertNull(processor.events.lastOrNull(), "Events stream should be empty after closing")
        }

    @Test
    fun concurrent_task_testClosedProcessorSendTaskEventFailsAndEventStreamIsEmpty() = runTest(timeout = TEST_TIMEOUT) {
        val processor = createProcessor(contextId, taskId)
        val task = createTask(taskId, contextId)

        // Send first task event
        processor.sendTaskEvent(task)

        // Close processor and then attempt to send more events
        processor.close()

        val workingUpdate = TaskStatusUpdateEvent(
            taskId = taskId,
            contextId = contextId,
            status = TaskStatus(state = TaskState.Working),
            final = false
        )

        assertFailsWith<SessionClosedException>("Should not be possible to send events to closed session") {
            processor.sendTaskEvent(workingUpdate)
        }

        assertNull(processor.events.lastOrNull(), "Events stream should be empty after closing")
    }
}
