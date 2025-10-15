package ai.koog.agents.features.sql.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.test.utils.DockerAvailableCondition
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestInstance(Lifecycle.PER_METHOD)
@ExtendWith(DockerAvailableCondition::class)
class H2PersistenceStorageProviderTest {

    private val agentId = "h2-agent"

    private fun provider(ttlSeconds: Long? = null): H2PersistenceStorageProvider {
        return H2PersistenceStorageProvider.inMemory(
            databaseName = "h2_test_db",
            tableName = "agent_checkpoints_test",
            ttlSeconds = ttlSeconds
        )
    }

    @Test
    fun `migrate and basic CRUD against H2 in-memory`() = runBlocking {
        val p = provider()
        p.migrate()

        // empty
        p.getLatestCheckpoint(agentId) shouldBe null
        p.getCheckpointCount(agentId) shouldBe 0

        // save
        val cp1 = createTestCheckpoint("cp-1", 0L)
        p.saveCheckpoint(agentId, cp1)

        // read
        val latest1 = p.getLatestCheckpoint(agentId)

        latest1 shouldNotBe null
        latest1?.checkpointId shouldBe "cp-1"
        latest1?.nodeId shouldBe "test-node"
        latest1?.messageHistory?.size shouldBe 3
        latest1?.isTombstone() shouldBe false

        // upsert same id should be idempotent (no duplicates due PK)
        p.saveCheckpoint(agentId, cp1)
        assertEquals(1, p.getCheckpoints(agentId).size)

        // insert second
        val cp2 = createTestCheckpoint("cp-2", cp1.version.plus(1))
        p.saveCheckpoint(agentId, cp2)
        val all = p.getCheckpoints(agentId)
        assertEquals(listOf("cp-1", "cp-2"), all.map { it.checkpointId })
        assertEquals("cp-2", p.getLatestCheckpoint(agentId)!!.checkpointId)

        // delete single
        p.deleteCheckpoint(agentId, "cp-1")
        assertEquals(listOf("cp-2"), p.getCheckpoints(agentId).map { it.checkpointId })

        // delete all
        p.deleteAllCheckpoints(agentId)
        assertEquals(0, p.getCheckpointCount(agentId))
    }

    @Test
    fun `ttl cleanup removes expired rows on H2`() = runBlocking {
        val p = provider(ttlSeconds = 1)
        p.migrate()

        p.saveCheckpoint(agentId, createTestCheckpoint("will-expire", 0L))
        assertEquals(1, p.getCheckpointCount(agentId))

        // Wait slightly over 1s to ensure ttl passes
        delay(1100)
        // Force cleanup directly to avoid interval throttling
        p.cleanupExpired()

        assertEquals(0, p.getCheckpointCount(agentId))
        assertNull(p.getLatestCheckpoint(agentId))
    }

    private fun createTestCheckpoint(id: String, version: Long): AgentCheckpointData {
        return AgentCheckpointData(
            checkpointId = id,
            createdAt = Clock.System.now(),
            nodeId = "test-node",
            lastInput = JsonPrimitive("Test input"),
            messageHistory = listOf(
                Message.System("You are a test assistant", RequestMetaInfo.create(Clock.System)),
                Message.User("Hello", RequestMetaInfo.create(Clock.System)),
                Message.Assistant("Hi there!", ResponseMetaInfo.create(Clock.System))
            ),
            version = version
        )
    }
}
