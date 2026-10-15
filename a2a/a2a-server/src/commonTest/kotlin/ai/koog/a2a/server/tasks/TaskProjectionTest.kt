package ai.koog.a2a.server.tasks

import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TextPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TaskProjectionTest {
    private val messages = (1..5).map {
        Message(messageId = "m$it", role = Role.ROLE_USER, parts = listOf(TextPart("message $it")))
    }
    private val artifacts = listOf(Artifact(artifactId = "a1", parts = listOf(TextPart("artifact"))))
    private val task = Task(
        id = "task-1",
        contextId = "context-1",
        status = TaskStatus(state = TaskState.TASK_STATE_COMPLETED),
        history = messages,
        artifacts = artifacts,
    )

    @Test
    fun testHistoryLengthKeepsMostRecentMessages() {
        // Nothing to cut: the same instance comes back.
        assertSame(task, task.project(historyLength = null, includeArtifacts = true))
        assertEquals(task, task.project(historyLength = 100, includeArtifacts = true))
        assertEquals(task.copy(history = null), task.project(historyLength = 0, includeArtifacts = true))
        assertEquals(task.copy(history = messages.takeLast(2)), task.project(historyLength = 2, includeArtifacts = true))

        val withoutHistory = task.copy(history = null)
        assertEquals(withoutHistory, withoutHistory.project(historyLength = 3, includeArtifacts = true))
    }

    @Test
    fun testExcludedArtifactsAreOmitted() {
        assertEquals(task.copy(artifacts = null), task.project(historyLength = null, includeArtifacts = false))
    }

    @Test
    fun testNegativeHistoryLengthIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            task.project(historyLength = -1, includeArtifacts = true)
        }
    }
}
