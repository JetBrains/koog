package ai.koog.agents.snapshot

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.providers.PersistenceUtils
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONNull
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests that checkpoint data serialized in the old format (before the properties refactoring)
 * can be correctly deserialized using the new format.
 *
 * Old format: nodePath, lastInput, lastOutput were top-level fields on AgentCheckpointData.
 * New format: those fields live inside the properties JSONObject.
 *
 * [OldAgentCheckpointData] mirrors the primary constructor of the old class so that serialization
 * produces byte-for-byte the same JSON that was stored before the refactoring.
 */
@Suppress("DEPRECATION")
class CheckpointBackwardCompatibilityTest {

    /**
     * Mirrors the old AgentCheckpointData primary constructor to produce JSON in the pre-refactoring format.
     */
    @Serializable
    private data class OldAgentCheckpointData(
        val checkpointId: String,
        val createdAt: Instant,
        val nodePath: String,
        val lastInput: JSONElement? = null,
        val lastOutput: JSONElement? = null,
        val messageHistory: List<Message>,
        val version: Long,
        val properties: JSONObject? = null
    )

    private val json = PersistenceUtils.defaultCheckpointJson
    private val timestamp = Clock.System.now()
    private val nodePath = "node.path"
    private val lastInput = JSONPrimitive("user input")
    private val lastOutput = JSONPrimitive("agent response")

    /**
     * Old format (post-0.6.1): nodePath and lastOutput are top-level fields; properties is absent.
     */
    @Test
    fun testDeserializeOldFormatWithLastOutput() {
        val oldJson = json.encodeToString(
            OldAgentCheckpointData(
                checkpointId = "cp-old-output",
                createdAt = timestamp,
                nodePath = nodePath,
                lastOutput = lastOutput,
                messageHistory = emptyList(),
                version = 1
            )
        )

        val checkpoint = json.decodeFromString<AgentCheckpointData>(oldJson)

        assertEquals("cp-old-output", checkpoint.checkpointId)
        assertEquals(nodePath, checkpoint.nodePath)
        assertEquals(JSONNull, checkpoint.lastInput)
        assertEquals(lastOutput, checkpoint.lastOutput)
    }

    /**
     * Old format (pre-0.6.0): nodePath and lastInput are top-level fields; properties is absent.
     */
    @Test
    fun testDeserializeOldFormatWithLastInput() {
        val oldJson = json.encodeToString(
            OldAgentCheckpointData(
                checkpointId = "cp-old-input",
                createdAt = timestamp,
                nodePath = nodePath,
                lastInput = lastInput,
                messageHistory = emptyList(),
                version = 1
            )
        )

        val checkpoint = json.decodeFromString<AgentCheckpointData>(oldJson)

        assertEquals("cp-old-input", checkpoint.checkpointId)
        assertEquals(nodePath, checkpoint.nodePath)
        assertEquals(lastInput, checkpoint.lastInput)
        assertEquals(JSONNull, checkpoint.lastOutput)
    }

    /**
     * Old tombstone format: nodePath="tombstone", lastOutput=JSONNull, properties={"tombstone":true}.
     */
    @Test
    fun testDeserializeOldFormatTombstone() {
        val oldJson = json.encodeToString(
            OldAgentCheckpointData(
                checkpointId = "tombstone-id",
                createdAt = timestamp,
                nodePath = PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME,
                lastOutput = JSONNull,
                messageHistory = emptyList(),
                version = 1,
                properties = JSONObject(mapOf(PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME to JSONPrimitive(true)))
            )
        )

        val checkpoint = json.decodeFromString<AgentCheckpointData>(oldJson)

        assertTrue(checkpoint.isTombstone())
    }

    /**
     * Old format with a message history: verifies that both messages and checkpoint position survive migration.
     */
    @Test
    fun testDeserializeOldFormatWithMessages() {
        val oldJson = json.encodeToString(
            OldAgentCheckpointData(
                checkpointId = "cp-with-messages",
                createdAt = timestamp,
                nodePath = nodePath,
                lastOutput = lastOutput,
                messageHistory = listOf(
                    Message.User("Hello", metaInfo = RequestMetaInfo(timestamp)),
                    Message.Assistant("Hi!", metaInfo = ResponseMetaInfo(timestamp))
                ),
                version = 1
            )
        )

        val checkpoint = json.decodeFromString<AgentCheckpointData>(oldJson)

        assertEquals("cp-with-messages", checkpoint.checkpointId)
        assertEquals(2, checkpoint.messageHistory.size)
        assertEquals("Hello", checkpoint.messageHistory[0].content)
        assertEquals("Hi!", checkpoint.messageHistory[1].content)
        assertEquals(nodePath, checkpoint.nodePath)
        assertEquals(JSONNull, checkpoint.lastInput)
        assertEquals(lastOutput, checkpoint.lastOutput)
    }

    /**
     * Old format with pre-existing properties alongside top-level nodePath/lastOutput.
     * Migration must merge them: nodePath/lastOutput go into properties alongside pre-existing keys.
     */
    @Test
    fun testDeserializeOldFormatWithExistingProperties() {
        val customKey = "customKey"
        val customValue = JSONPrimitive("customValue")
        val oldJson = json.encodeToString(
            OldAgentCheckpointData(
                checkpointId = "cp-merged-props",
                createdAt = timestamp,
                nodePath = nodePath,
                lastOutput = lastOutput,
                messageHistory = emptyList(),
                version = 1,
                properties = JSONObject(mapOf(customKey to customValue))
            )
        )

        val checkpoint = json.decodeFromString<AgentCheckpointData>(oldJson)

        assertEquals(nodePath, checkpoint.nodePath)
        assertEquals(lastOutput, checkpoint.lastOutput)
        assertEquals(customValue, checkpoint.properties?.entries[customKey])
    }
}
