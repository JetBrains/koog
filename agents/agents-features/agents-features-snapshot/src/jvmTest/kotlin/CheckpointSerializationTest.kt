import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.feature.tombstoneCheckpoint
import ai.koog.agents.snapshot.providers.PersistencyUtils
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CheckpointSerializationTest {

    private fun sampleMessages(now: Instant): List<Message> = listOf(
        Message.User("Hello", metaInfo = RequestMetaInfo(now)),
        Message.Assistant("Hi!", metaInfo = ResponseMetaInfo(now))
    )

    @Test
    fun `serialize and deserialize without properties`() {
        val now = Clock.System.now()
        val checkpoint = AgentCheckpointData(
            checkpointId = "cp-1",
            createdAt = now,
            nodeId = "NodeA",
            lastInput = JsonPrimitive("last-input"),
            messageHistory = sampleMessages(now),
            properties = null
        )

        val json = PersistencyUtils.defaultCheckpointJson
        val serialized = json.encodeToString(AgentCheckpointData.serializer(), checkpoint)

        // properties should be omitted due to explicitNulls = false
        assertFalse(serialized.contains("\"properties\""), "Serialized JSON should not contain 'properties' when it is null")

        val restored = json.decodeFromString(AgentCheckpointData.serializer(), serialized)

        // Thorough field-by-field assertions
        assertEquals("cp-1", restored.checkpointId)
        assertEquals(now, restored.createdAt)
        assertEquals("NodeA", restored.nodeId)
        assertEquals(JsonPrimitive("last-input"), restored.lastInput)
        assertNull(restored.properties, "properties should be null after deserialization when omitted in JSON")

        // Message history assertions
        assertEquals(2, restored.messageHistory.size)
        val m0 = restored.messageHistory[0] as Message.User
        val m1 = restored.messageHistory[1] as Message.Assistant
        assertEquals("Hello", m0.content)
        assertEquals(now, m0.metaInfo.timestamp)
        assertEquals("Hi!", m1.content)
        assertEquals(now, m1.metaInfo.timestamp)

        // Full equality as a final check
        assertEquals(checkpoint, restored)
    }

    @Test
    fun `serialize and deserialize with diverse properties`() {
        val now = Clock.System.now()
        val properties: Map<String, JsonElement> = mapOf(
            "string" to JsonPrimitive("value"),
            "number" to JsonPrimitive(42),
            "boolean" to JsonPrimitive(true),
            "nested" to buildJsonObject {
                put("a", JsonPrimitive(1))
                put("b", JsonPrimitive("two"))
                put(
                    "c",
                    buildJsonArray {
                        add(JsonPrimitive(1))
                        add(JsonPrimitive(2))
                        add(JsonPrimitive(3))
                    }
                )
            }
        )

        val checkpoint = AgentCheckpointData(
            checkpointId = "cp-2",
            createdAt = now,
            nodeId = "NodeB",
            lastInput = JsonObject(mapOf("inputKey" to JsonPrimitive("inputVal"))),
            messageHistory = sampleMessages(now),
            properties = properties
        )

        val json = PersistencyUtils.defaultCheckpointJson
        val serialized = json.encodeToString(AgentCheckpointData.serializer(), checkpoint)
        val restored = json.decodeFromString(AgentCheckpointData.serializer(), serialized)

        // Top-level fields
        assertEquals("cp-2", restored.checkpointId)
        assertEquals(now, restored.createdAt)
        assertEquals("NodeB", restored.nodeId)
        assertEquals(JsonObject(mapOf("inputKey" to JsonPrimitive("inputVal"))), restored.lastInput)
        assertNotNull(restored.properties)

        // Properties map content and types
        val p = restored.properties
        assertEquals(JsonPrimitive("value"), p["string"])
        assertEquals(JsonPrimitive(42), p["number"])
        assertEquals(JsonPrimitive(true), p["boolean"])
        val nested = p["nested"] as JsonObject
        assertEquals(JsonPrimitive(1), nested["a"])
        assertEquals(JsonPrimitive("two"), nested["b"])
        val arr = nested["c"]!!.jsonArray
        assertEquals(3, arr.size)
        assertEquals(JsonPrimitive(1), arr[0])
        assertEquals(JsonPrimitive(2), arr[1])
        assertEquals(JsonPrimitive(3), arr[2])

        // Message history assertions
        assertEquals(2, restored.messageHistory.size)
        val um = restored.messageHistory[0] as Message.User
        val am = restored.messageHistory[1] as Message.Assistant
        assertEquals("Hello", um.content)
        assertEquals(now, um.metaInfo.timestamp)
        assertEquals("Hi!", am.content)
        assertEquals(now, am.metaInfo.timestamp)

        // Full equality as a final check
        assertEquals(checkpoint, restored)
    }

    @Test
    fun `serialize and deserialize tombstone checkpoint`() {
        val checkpoint = tombstoneCheckpoint(Clock.System.now())
        val json = PersistencyUtils.defaultCheckpointJson
        val serialized = json.encodeToString(AgentCheckpointData.serializer(), checkpoint)
        val restored = json.decodeFromString(AgentCheckpointData.serializer(), serialized)

        // Basic invariants
        assertEquals("tombstone", restored.nodeId)
        assertEquals(emptyList(), restored.messageHistory)
        assertEquals(checkpoint.createdAt, restored.createdAt)
        assertEquals(checkpoint.checkpointId, restored.checkpointId)
        assertEquals(true, restored.isTombstone())

        // lastInput must remain JsonNull and properties must contain tombstone=true
        assertEquals(checkpoint.lastInput, restored.lastInput)
        assertNotNull(restored.properties)
        assertEquals(JsonPrimitive(true), restored.properties["tombstone"])

        // Full equality as a final check
        assertEquals(checkpoint, restored)
    }
}
