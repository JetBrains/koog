package ai.koog.agents.snapshot

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.GraphCheckpointProperties
import ai.koog.agents.snapshot.feature.tombstoneCheckpoint
import ai.koog.agents.snapshot.providers.PersistenceUtils
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.toKoogJSONObject
import ai.koog.utils.time.KoogClock
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CheckpointSerializationTest {

    private fun sampleMessages(now: Instant): List<Message> = listOf(
        Message.User("Hello", metaInfo = RequestMetaInfo(now)),
        Message.Assistant("Hi!", metaInfo = ResponseMetaInfo(now))
    )

    @Test
    fun `serialize and deserialize without properties`() {
        val now = KoogClock.System.now()
        val checkpoint = AgentCheckpointData(
            checkpointId = "cp-1",
            createdAt = now,
            messageHistory = sampleMessages(now),
            version = 0L,
            graphProperties = GraphCheckpointProperties(
                nodePath = "NodeA",
                lastOutput = JSONPrimitive("last-input")
            ),
            plannerProperties = null,
            properties = null,
        )

        val json = PersistenceUtils.defaultCheckpointJson
        val serialized = json.encodeToString(checkpoint)

        val restored = json.decodeFromString<AgentCheckpointData>(serialized)

        // Thorough field-by-field assertions
        assertEquals("cp-1", restored.checkpointId)
        assertEquals(now, restored.createdAt)
        val nodePath = restored.graphProperties?.nodePath
        assertEquals("NodeA", nodePath)
        assertEquals(JSONPrimitive("last-input"), restored.graphProperties?.lastOutput)

        // Message history assertions
        assertEquals(2, restored.messageHistory.size)
        val m0 = restored.messageHistory[0] as Message.User
        val m1 = restored.messageHistory[1] as Message.Assistant
        assertEquals("Hello", (m0.parts[0] as MessagePart.Text).text)
        assertEquals(now, m0.metaInfo.timestamp)
        assertEquals("Hi!", (m1.parts[0] as MessagePart.Text).text)
        assertEquals(now, m1.metaInfo.timestamp)

        // Full equality as a final check
        assertEquals(checkpoint, restored)
    }

    @Test
    fun `serialize and deserialize with diverse properties`() {
        val now = KoogClock.System.now()
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
            messageHistory = sampleMessages(now),
            properties = properties,
            version = 0L,
            graphProperties = GraphCheckpointProperties(
                nodePath = "NodeB",
                lastOutput = JSONObject(mapOf("inputKey" to JSONPrimitive("inputVal")))
            ),
            plannerProperties = null,
        )

        val json = PersistenceUtils.defaultCheckpointJson
        val serialized = json.encodeToString(checkpoint)
        val restored = json.decodeFromString<AgentCheckpointData>(serialized)

        // Full equality as a check
        assertEquals(checkpoint, restored)
    }

    @Test
    fun `serialize and deserialize tombstone checkpoint`() {
        val checkpoint = tombstoneCheckpoint(KoogClock.System.now(), 0L)
        val json = PersistenceUtils.defaultCheckpointJson
        val serialized = json.encodeToString(checkpoint)
        val restored = json.decodeFromString<AgentCheckpointData>(serialized)

        // Full equality as a final check
        assertEquals(checkpoint, restored)
    }
}
