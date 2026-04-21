import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.tombstoneCheckpoint
import ai.koog.agents.snapshot.providers.PersistenceUtils
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.toKoogJSONObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

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
            nodePath = "NodeA",
            lastOutput = JSONPrimitive("last-input"),
            messageHistory = sampleMessages(now),
            version = 0L
        )

        val json = PersistenceUtils.defaultCheckpointJson
        val serialized = json.encodeToString(AgentCheckpointData.serializer(), checkpoint)

        // properties should be omitted due to explicitNulls = false
        assertFalse(
            serialized.contains("\"properties\""),
            "Serialized JSON should not contain 'properties' when it is null"
        )

        val restored = json.decodeFromString(AgentCheckpointData.serializer(), serialized)

        // Thorough field-by-field assertions
        assertEquals("cp-1", restored.checkpointId)
        assertEquals(now, restored.createdAt)
        assertEquals("NodeA", restored.nodePath)
        assertEquals(JSONPrimitive("last-input"), restored.lastOutput)
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
        val properties = buildJsonObject {
            put("string", "value")
            put("number", 42)
            put("boolean", true)
            put(
                "nested",
                buildJsonObject {
                    put("a", 1)
                    put("b", "two")
                    putJsonArray("c") {
                        add(1)
                        add(2)
                        add(3)
                    }
                }
            )
        }.toKoogJSONObject()

        val checkpoint = AgentCheckpointData(
            checkpointId = "cp-2",
            createdAt = now,
            nodePath = "NodeB",
            lastOutput = JSONObject(mapOf("inputKey" to JSONPrimitive("inputVal"))),
            messageHistory = sampleMessages(now),
            properties = properties,
            version = 0L
        )

        val json = PersistenceUtils.defaultCheckpointJson
        val serialized = json.encodeToString(AgentCheckpointData.serializer(), checkpoint)
        val restored = json.decodeFromString(AgentCheckpointData.serializer(), serialized)

        // Full equality as a check
        assertEquals(checkpoint, restored)
    }

    @Test
    fun `serialize and deserialize tombstone checkpoint`() {
        val checkpoint = tombstoneCheckpoint(Clock.System.now(), 0L)
        val json = PersistenceUtils.defaultCheckpointJson
        val serialized = json.encodeToString(AgentCheckpointData.serializer(), checkpoint)
        val restored = json.decodeFromString(AgentCheckpointData.serializer(), serialized)

        // Full equality as a final check
        assertEquals(checkpoint, restored)
    }
}
