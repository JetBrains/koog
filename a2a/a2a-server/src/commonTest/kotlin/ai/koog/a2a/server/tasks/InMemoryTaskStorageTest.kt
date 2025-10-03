package ai.koog.a2a.server.tasks

import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.exceptions.TaskOperationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryTaskStorageTest {
    private lateinit var storage: InMemoryTaskStorage

    @BeforeTest
    fun setUp() {
        storage = InMemoryTaskStorage()
    }

    @Test
    fun testGetNonExistentTask() = runTest {
        val result = storage.get("non-existent-id")
        assertNull(result)
    }

    @Test
    fun testStoreAndRetrieveTask() = runTest {
        val task = createTask(id = "task-1", contextId = "context-1")

        storage.update(task)

        val retrieved = storage.get("task-1")

        assertNotNull(retrieved)
        assertEquals(task.id, retrieved.id)
        assertEquals(task.contextId, retrieved.contextId)
    }

    @Test
    fun testDeleteTask() = runTest {
        val task = createTask(id = "task-1", contextId = "context-1")
        storage.update(task)

        storage.delete("task-1")

        val retrieved = storage.get("task-1")
        assertNull(retrieved)
    }

    @Test
    fun testGetByContext() = runTest {
        val task1 = createTask(id = "task-1", contextId = "context-1")
        val task2 = createTask(id = "task-2", contextId = "context-1")
        val task3 = createTask(id = "task-3", contextId = "context-2")

        storage.update(task1)
        storage.update(task2)
        storage.update(task3)

        val result = storage.getByContext("context-1")

        assertEquals(2, result.size)
        assertTrue(result.all { it.contextId == "context-1" })
        assertTrue(result.any { it.id == "task-1" })
        assertTrue(result.any { it.id == "task-2" })
    }

    @Test
    fun testTaskStatusUpdateEvent() = runTest {
        // Create and store initial task with metadata
        val initialMetadata = buildJsonObject {
            put("initialKey", JsonPrimitive("initialValue"))
            put("sharedKey", JsonPrimitive("originalValue"))
        }
        val task = createTask(id = "task-1", contextId = "context-1", metadata = initialMetadata)
        storage.update(task)

        // Create a status update event with additional metadata
        val updateMetadata = buildJsonObject {
            put("newKey", JsonPrimitive("newValue"))
            put("sharedKey", JsonPrimitive("updatedValue"))
        }
        val newMessage = createUserMessage("status-msg", "context-1", "Task completed successfully")
        val newStatus = TaskStatus(
            state = TaskState.Completed,
            message = newMessage,
            timestamp = Instant.parse("2023-01-01T12:00:00Z")
        )
        val statusUpdateEvent = TaskStatusUpdateEvent(
            taskId = "task-1",
            contextId = "context-1",
            status = newStatus,
            metadata = updateMetadata,
            final = true
        )

        // Update task status
        storage.update(statusUpdateEvent)

        // Verify the status was updated and metadata was merged
        val retrieved = storage.get("task-1")
        assertEquals(newStatus, retrieved?.status)

        // Verify metadata merging: original + new with updates overriding
        val expectedMetadata = buildJsonObject {
            put("initialKey", JsonPrimitive("initialValue")) // preserved from original
            put("sharedKey", JsonPrimitive("updatedValue")) // updated from event
            put("newKey", JsonPrimitive("newValue")) // added from event
        }
        assertEquals(expectedMetadata, retrieved?.metadata)
    }

    @Test
    fun testTaskStatusUpdateEventForNonExistentTask() = runTest {
        val statusUpdateEvent = TaskStatusUpdateEvent(
            taskId = "non-existent",
            contextId = "context-1",
            status = TaskStatus(state = TaskState.Completed),
            final = true
        )

        assertFailsWith<TaskOperationException> {
            storage.update(statusUpdateEvent)
        }
    }

    @Test
    fun testTaskArtifactUpdateEventNewArtifact() = runTest {
        // Create and store initial task
        val task = createTask(id = "task-1", contextId = "context-1")
        storage.update(task)

        // Create artifact update event with new artifact
        val artifact = Artifact(
            artifactId = "artifact-1",
            parts = listOf(TextPart("Initial content"))
        )
        val artifactUpdateEvent = TaskArtifactUpdateEvent(
            taskId = "task-1",
            contextId = "context-1",
            artifact = artifact,
            append = false
        )

        // Update task with artifact
        storage.update(artifactUpdateEvent)

        // Verify the artifact was added
        val retrieved = storage.get("task-1", includeArtifacts = true)
        assertEquals(listOf(artifact), retrieved?.artifacts)
    }

    @Test
    fun testTaskArtifactUpdateEventReplaceExisting() = runTest {
        // Create and store initial task with artifact
        val initialArtifact = Artifact(
            artifactId = "artifact-1",
            parts = listOf(TextPart("Initial content"))
        )
        val task = createTask(id = "task-1", contextId = "context-1", artifacts = listOf(initialArtifact))
        storage.update(task)

        // Create artifact update event to replace existing artifact
        val newArtifact = Artifact(
            artifactId = "artifact-1",
            parts = listOf(TextPart("Replaced content"))
        )
        val artifactUpdateEvent = TaskArtifactUpdateEvent(
            taskId = "task-1",
            contextId = "context-1",
            artifact = newArtifact,
            append = false
        )

        // Update task with new artifact
        storage.update(artifactUpdateEvent)

        // Verify the artifact was replaced
        val retrieved = storage.get("task-1", includeArtifacts = true)
        assertEquals(listOf(newArtifact), retrieved?.artifacts)
    }

    @Test
    fun testTaskArtifactUpdateEventAppendToExisting() = runTest {
        // Create and store initial task with artifact
        val initialArtifact = Artifact(
            artifactId = "artifact-1",
            parts = listOf(TextPart("Initial content"))
        )
        val task = createTask(id = "task-1", contextId = "context-1", artifacts = listOf(initialArtifact))
        storage.update(task)

        // Create artifact update event to append to existing artifact
        val appendArtifact = Artifact(
            artifactId = "artifact-1",
            parts = listOf(TextPart(" Additional content"))
        )
        val artifactUpdateEvent = TaskArtifactUpdateEvent(
            taskId = "task-1",
            contextId = "context-1",
            artifact = appendArtifact,
            append = true
        )

        val resultingArtifact = initialArtifact.copy(parts = initialArtifact.parts + appendArtifact.parts)

        // Update task with appended artifact
        storage.update(artifactUpdateEvent)

        // Verify the content was appended
        val retrieved = storage.get("task-1", includeArtifacts = true)
        assertEquals(listOf(resultingArtifact), retrieved?.artifacts)
    }

    @Test
    fun testTaskArtifactUpdateEventForNonExistentTask() = runTest {
        val artifact = Artifact(
            artifactId = "artifact-1",
            parts = listOf(TextPart("Content"))
        )
        val artifactUpdateEvent = TaskArtifactUpdateEvent(
            taskId = "non-existent",
            contextId = "context-1",
            artifact = artifact,
            append = false
        )

        assertFailsWith<TaskOperationException> {
            storage.update(artifactUpdateEvent)
        }
    }

    @Test
    fun testTaskStatusHistoryPreservation() = runTest {
        // Initial task with no message in status
        val initialTask = createTask(
            id = "task-1",
            contextId = "context-1"
        )
        storage.update(initialTask)

        // Verify initial task - should have the initial status message but no history yet
        val initialTaskFromStorage = storage.get("task-1", historyLength = null)
        assertNotNull(initialTaskFromStorage)
        assertNull(initialTaskFromStorage.history)

        // Update status with a new message - history is still empty
        val firstUpdateMessage = createUserMessage("update-msg-1", "context-1", "Making progress")
        val firstUpdateStatus = TaskStatus(
            state = TaskState.Working,
            message = firstUpdateMessage,
            timestamp = Instant.parse("2023-01-01T11:00:00Z")
        )
        val firstUpdate = TaskStatusUpdateEvent(
            taskId = "task-1",
            contextId = "context-1",
            status = firstUpdateStatus,
            final = false
        )

        storage.update(firstUpdate)

        val afterFirstUpdate = storage.get("task-1", historyLength = null) // Get full history
        assertNotNull(afterFirstUpdate)
        assertEquals(firstUpdateStatus, afterFirstUpdate.status)
        assertNull(afterFirstUpdate.history)

        // Second status update - this should add the previous status message to history
        val secondUpdateMessage = createUserMessage("update-msg-2", "context-1", "Almost done")
        val secondUpdateStatus = TaskStatus(
            state = TaskState.Working,
            message = secondUpdateMessage,
            timestamp = Instant.parse("2023-01-01T12:00:00Z")
        )
        val secondUpdate = TaskStatusUpdateEvent(
            taskId = "task-1",
            contextId = "context-1",
            status = secondUpdateStatus,
            final = false
        )
        storage.update(secondUpdate)

        // Verify second update
        val afterSecondUpdate = storage.get("task-1", historyLength = null)
        assertNotNull(afterSecondUpdate)
        assertEquals(secondUpdateStatus, afterSecondUpdate.status)
        assertEquals(listOf(firstUpdateMessage), afterSecondUpdate.history)

        // Final status update
        val completionMessage = createUserMessage("completion-msg", "context-1", "Task completed successfully")
        val completionStatus = TaskStatus(
            state = TaskState.Completed,
            message = completionMessage,
            timestamp = Instant.parse("2023-01-01T13:00:00Z")
        )
        val completionUpdate = TaskStatusUpdateEvent(
            taskId = "task-1",
            contextId = "context-1",
            status = completionStatus,
            final = true
        )
        storage.update(completionUpdate)

        // Verify final update
        val finalTask = storage.get("task-1", historyLength = null)
        assertNotNull(finalTask)
        assertEquals(completionStatus, finalTask.status)
        assertEquals(listOf(firstUpdateMessage, secondUpdateMessage), finalTask.history)
    }

    private fun createUserMessage(
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
        status: TaskStatus = TaskStatus(state = TaskState.Submitted),
        history: List<Message>? = null,
        artifacts: List<Artifact>? = null,
        metadata: JsonObject? = null
    ) = Task(
        id = id,
        contextId = contextId,
        status = status,
        history = history,
        artifacts = artifacts,
        metadata = metadata
    )
}
