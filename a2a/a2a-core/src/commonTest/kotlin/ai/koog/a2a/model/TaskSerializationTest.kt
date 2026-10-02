package ai.koog.a2a.model

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TaskSerializationTest {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testTaskSerialization() {
        val task = Task(
            id = "task-1",
            contextId = "context-1",
            status = TaskStatus(
                state = TaskState.TASK_STATE_WORKING,
                message = Message(
                    messageId = "message-1",
                    role = Role.ROLE_AGENT,
                    parts = listOf(TextPart("Working on it")),
                ),
                timestamp = Instant.parse("2025-01-01T12:00:00Z"),
            ),
            artifacts = listOf(
                Artifact(artifactId = "artifact-1", parts = listOf(TextPart("result"))),
            ),
        )

        //language=JSON
        val expectedJson = """
            {
                "id": "task-1",
                "contextId": "context-1",
                "status": {
                    "state": "TASK_STATE_WORKING",
                    "message": {
                        "messageId": "message-1",
                        "role": "ROLE_AGENT",
                        "parts": [{"text": "Working on it"}]
                    },
                    "timestamp": "2025-01-01T12:00:00Z"
                },
                "artifacts": [
                    {
                        "artifactId": "artifact-1",
                        "parts": [{"text": "result"}]
                    }
                ]
            }
        """.trimIndent()

        json.encodeToString(task) shouldEqualJson expectedJson
        assertEquals(task, json.decodeFromString<Task>(expectedJson))
    }

    @Test
    fun testTaskStatusWithoutTimestampDecodesToNull() {
        val status = json.decodeFromString<TaskStatus>("""{"state": "TASK_STATE_SUBMITTED"}""")

        assertNull(status.timestamp)
        assertEquals(TaskStatus(TaskState.TASK_STATE_SUBMITTED), status)
        json.encodeToString(status) shouldEqualJson """{"state": "TASK_STATE_SUBMITTED"}"""
    }

    @Test
    fun testTaskWithoutContextIdDecodesToNull() {
        val task = json.decodeFromString<Task>(
            """{"id": "task-1", "status": {"state": "TASK_STATE_COMPLETED"}}"""
        )

        assertEquals(Task(id = "task-1", status = TaskStatus(TaskState.TASK_STATE_COMPLETED)), task)
        assertNull(task.contextId)
    }

    @Test
    fun testEnumNamesMatchProto() {
        val expectedStates = listOf(
            "TASK_STATE_UNSPECIFIED",
            "TASK_STATE_SUBMITTED",
            "TASK_STATE_WORKING",
            "TASK_STATE_INPUT_REQUIRED",
            "TASK_STATE_COMPLETED",
            "TASK_STATE_CANCELED",
            "TASK_STATE_FAILED",
            "TASK_STATE_REJECTED",
            "TASK_STATE_AUTH_REQUIRED",
        )

        assertEquals(expectedStates, TaskState.entries.map { it.name })
        assertEquals(listOf("ROLE_UNSPECIFIED", "ROLE_USER", "ROLE_AGENT"), Role.entries.map { it.name })
    }
}
