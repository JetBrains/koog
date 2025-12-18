package ai.koog.agents.features.sql.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for SQL persistence providers.
 *
 * Focuses on H2 in-memory database for actual functionality tests
 * and compilation verification for other providers.
 */
@Execution(ExecutionMode.SAME_THREAD)
class SQLPersistenceProvidersTest {

    @Test
    fun `test save and retrieve checkpoint`() = runBlocking {
        val agentId = "test-agent"
        val provider = H2PersistenceStorageProvider.inMemory(
            databaseName = "test_db"
        )

        provider.migrate()

        // Create and save checkpoint
        val checkpoint = createTestCheckpoint("test-1", 0L)
        provider.saveCheckpoint(agentId, checkpoint)

        // Retrieve and verify
        val retrieved = provider.getLatestCheckpoint(agentId)
        assertNotNull(retrieved)
        assertEquals(checkpoint.checkpointId, retrieved.checkpointId)
        assertEquals(checkpoint.nodePath, retrieved.nodePath)
        assertEquals(checkpoint.messageHistory.size, retrieved.messageHistory.size)
    }

    @Test
    fun `test multiple checkpoints and ordering`() = runBlocking {
        val agentId = "test-agent"
        val provider = H2PersistenceStorageProvider.inMemory(
            databaseName = "test_ordering"
        )

        provider.migrate()

        // Save multiple checkpoints
        val checkpoint1 = createTestCheckpoint("checkpoint-1", 0L)
        val checkpoint2 = createTestCheckpoint("checkpoint-2", checkpoint1.version.plus(1))
        val checkpoint3 = createTestCheckpoint("checkpoint-3", checkpoint2.version.plus(1))

        provider.saveCheckpoint(agentId, checkpoint1)
        provider.saveCheckpoint(agentId, checkpoint2)
        provider.saveCheckpoint(agentId, checkpoint3)

        // Verify count and ordering
        val allCheckpoints = provider.getCheckpoints(agentId)
        assertEquals(3, allCheckpoints.size)
        assertEquals("checkpoint-1", allCheckpoints[0].checkpointId)
        assertEquals("checkpoint-3", allCheckpoints[2].checkpointId)

        // Verify latest
        val latest = provider.getLatestCheckpoint(agentId)
        assertEquals("checkpoint-3", latest?.checkpointId)
    }

    @Test
    fun `test persistence ID isolation`() = runBlocking {
        val agentId = "test-agent"
        val agentId2 = "test-agent2"

        val provider1 = H2PersistenceStorageProvider.inMemory(
            databaseName = "shared_db"
        )
        val provider2 = H2PersistenceStorageProvider.inMemory(
            databaseName = "shared_db" // Same database
        )

        provider1.migrate()
        provider2.migrate()

        // Save to different agents
        provider1.saveCheckpoint(agentId, createTestCheckpoint("agent1-data", 0L))
        provider2.saveCheckpoint(agentId2, createTestCheckpoint("agent2-data", 0L))

        // Verify isolation
        val agent1Checkpoints = provider1.getCheckpoints(agentId)
        val agent2Checkpoints = provider2.getCheckpoints(agentId2)

        assertEquals(1, agent1Checkpoints.size)
        assertEquals(1, agent2Checkpoints.size)
        assertEquals("agent1-data", agent1Checkpoints[0].checkpointId)
        assertEquals("agent2-data", agent2Checkpoints[0].checkpointId)
    }

    @Test
    fun `test TTL expiration`() = runBlocking {
        val agentId = "test-agent"

        val provider = H2PersistenceStorageProvider.inMemory(
            databaseName = "ttl_db",
            ttlSeconds = 1 // 1 second TTL
        )

        provider.migrate()

        // Save checkpoint
        provider.saveCheckpoint(agentId, createTestCheckpoint("expire-soon", 0L))
        assertEquals(1, provider.getCheckpointCount(agentId))

        // Wait for expiration
        delay(1500)
        provider.conditionalCleanup()
        // Should be cleaned up on next operation
        val afterExpiry = provider.getLatestCheckpoint(agentId)
        assertNull(afterExpiry)
        assertEquals(0, provider.getCheckpointCount(agentId))
    }

    @Test
    fun `verify all providers can be instantiated`() {
        // H2
        assertNotNull(H2PersistenceStorageProvider.inMemory("test", "test_db"))

        // PostgreSQL
        assertNotNull(
            PostgresPersistenceStorageProvider(
                database = Database.connect(
                    url = "jdbc:postgresql://localhost:5432/test",
                    driver = "org.postgresql.Driver",
                    user = "test",
                    password = "test"
                )
            )
        )

        // MySQL
        assertNotNull(
            MySQLPersistenceStorageProvider(
                database = Database.connect(
                    url = "jdbc:mysql://localhost:3306/test",
                    driver = "com.mysql.cj.jdbc.Driver",
                    user = "test",
                    password = "test"
                )
            )
        )
    }

    private fun createTestCheckpoint(id: String, version: Long): AgentCheckpointData {
        return AgentCheckpointData(
            checkpointId = id,
            createdAt = Clock.System.now(),
            nodePath = "test-node",
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
