package ai.koog.a2a.model

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventsSerializationTest {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    private val task = Task(
        id = "task-1",
        contextId = "context-1",
        status = TaskStatus(TaskState.TASK_STATE_SUBMITTED),
    )

    private val message = Message(
        messageId = "message-1",
        role = Role.ROLE_USER,
        parts = listOf(TextPart("Hi")),
    )

    @Test
    fun testStreamResponseMembers() {
        val events: Map<Event, String> = mapOf(
            task to """{"task": {"id": "task-1", "contextId": "context-1", "status": {"state": "TASK_STATE_SUBMITTED"}}}""",
            message to """{"message": {"messageId": "message-1", "role": "ROLE_USER", "parts": [{"text": "Hi"}]}}""",
            TaskStatusUpdateEvent(
                taskId = "task-1",
                contextId = "context-1",
                status = TaskStatus(TaskState.TASK_STATE_COMPLETED),
            ) to """{"statusUpdate": {"taskId": "task-1", "contextId": "context-1", "status": {"state": "TASK_STATE_COMPLETED"}}}""",
            TaskArtifactUpdateEvent(
                taskId = "task-1",
                contextId = "context-1",
                artifact = Artifact(artifactId = "artifact-1", parts = listOf(TextPart("chunk"))),
                append = true,
                lastChunk = false,
            ) to """
                {
                    "artifactUpdate": {
                        "taskId": "task-1",
                        "contextId": "context-1",
                        "artifact": {"artifactId": "artifact-1", "parts": [{"text": "chunk"}]},
                        "append": true,
                        "lastChunk": false
                    }
                }
            """.trimIndent(),
        )

        events.forEach { (event, expectedJson) ->
            json.encodeToString(event) shouldEqualJson expectedJson
            assertEquals(event, json.decodeFromString<Event>(expectedJson))
        }
    }

    @Test
    fun testSendMessageResponseMembers() {
        val taskJson = """{"task": {"id": "task-1", "contextId": "context-1", "status": {"state": "TASK_STATE_SUBMITTED"}}}"""
        val messageJson = """{"message": {"messageId": "message-1", "role": "ROLE_USER", "parts": [{"text": "Hi"}]}}"""

        json.encodeToString<ResponseEvent>(task) shouldEqualJson taskJson
        json.encodeToString<ResponseEvent>(message) shouldEqualJson messageJson
        assertEquals(task, json.decodeFromString<ResponseEvent>(taskJson))
        assertEquals(message, json.decodeFromString<ResponseEvent>(messageJson))
    }

    @Test
    fun testStreamResponseIgnoresUnknownSiblingKeys() {
        val decoded = json.decodeFromString<Event>(
            """
                {
                    "unknownMember": {"a": 1},
                    "task": {"id": "task-1", "contextId": "context-1", "status": {"state": "TASK_STATE_SUBMITTED"}}
                }
            """.trimIndent()
        )

        assertEquals(task, decoded)
    }

    @Test
    fun testStreamResponseWithoutKnownMemberFails() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<Event>("""{"unknownMember": {}}""")
        }
    }

    @Test
    fun testStreamResponseWithMultipleMembersFails() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<Event>(
                """
                    {
                        "task": {"id": "task-1", "status": {"state": "TASK_STATE_SUBMITTED"}},
                        "message": {"messageId": "message-1", "role": "ROLE_USER", "parts": []}
                    }
                """.trimIndent()
            )
        }
    }
}
