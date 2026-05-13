package ai.koog.agents.features.persistence.jdbc

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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests that checkpoint data serialized in the old format (before the properties refactoring)
 * can be correctly deserialized using the new format when read back through a JDBC storage provider.
 *
 * Mirrors `ai.koog.agents.snapshot.CheckpointBackwardCompatibilityTest` for in-memory storage.
 *
 * Old format: nodePath, lastInput, lastOutput were top-level fields on AgentCheckpointData.
 * New format: those fields live inside the properties JSONObject.
 *
 * [OldAgentCheckpointData] mirrors the primary constructor of the old class so that serialization
 * produces byte-for-byte the same JSON that was stored before the refactoring.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalUuidApi::class)
@TestInstance(Lifecycle.PER_METHOD)
@Execution(ExecutionMode.SAME_THREAD)
class H2JdbcCheckpointBackwardCompatibilityTest {

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

    private lateinit var dataSource: JdbcDataSource
    private lateinit var provider: H2JdbcPersistenceStorageProvider

    @BeforeEach
    fun setUp() {
        dataSource = JdbcDataSource().apply {
            setUrl("jdbc:h2:mem:compat_test_${System.nanoTime()};DB_CLOSE_DELAY=-1")
        }
        provider = H2JdbcPersistenceStorageProvider(dataSource = dataSource, tableName = TABLE_NAME)
        runBlocking { provider.migrate() }
    }

    /**
     * Inserts [oldJson] (old-format checkpoint JSON) directly into the database, then reads it
     * back through the provider's normal deserialization path.
     */
    private fun loadCheckpoint(oldJson: String): AgentCheckpointData {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO $TABLE_NAME (persistence_id, checkpoint_id, created_at, checkpoint_json, ttl_timestamp, version) VALUES (?, ?, ?, ?, NULL, ?)"
            ).use { stmt ->
                stmt.setString(1, SESSION_ID)
                stmt.setString(2, Uuid.random().toString())
                stmt.setLong(3, Clock.System.now().toEpochMilliseconds())
                stmt.setString(4, oldJson)
                stmt.setLong(5, 1L)
                stmt.executeUpdate()
            }
        }
        return runBlocking { provider.getLatestCheckpoint(SESSION_ID) }
            ?: error("No checkpoint found after inserting old-format JSON")
    }

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

        val checkpoint = loadCheckpoint(oldJson)

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

        val checkpoint = loadCheckpoint(oldJson)

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

        val checkpoint = loadCheckpoint(oldJson)

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

        val checkpoint = loadCheckpoint(oldJson)

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

        val checkpoint = loadCheckpoint(oldJson)

        assertEquals(nodePath, checkpoint.nodePath)
        assertEquals(lastOutput, checkpoint.lastOutput)
        assertEquals(customValue, checkpoint.properties?.entries[customKey])
    }

    companion object {
        private const val TABLE_NAME = "compat_checkpoints"
        private const val SESSION_ID = "compat-session"
    }
}
