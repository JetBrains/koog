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
class SQLPersistenceProvidersTest {

    @Test
    fun `test save and retrieve checkpoint`() = runBlocking {
        val provider = H2PersistenceStorageProvider.inMemory(
            persistenceId = "test-agent",
            databaseName = "test_db"
        )

        provider.migrate()

        // Create and save checkpoint
        val checkpoint = createTestCheckpoint("test-1")
        provider.saveCheckpoint(checkpoint)

        // Retrieve and verify
        val retrieved = provider.getLatestCheckpoint()
        assertNotNull(retrieved)
        assertEquals(checkpoint.checkpointId, retrieved.checkpointId)
        assertEquals(checkpoint.nodeId, retrieved.nodeId)
        assertEquals(checkpoint.messageHistory.size, retrieved.messageHistory.size)
    }

    @Test
    fun `test multiple checkpoints and ordering`() = runBlocking {
        val provider = H2PersistenceStorageProvider.inMemory(
            persistenceId = "test-agent",
            databaseName = "test_ordering"
        )

        provider.migrate()

        // Save multiple checkpoints
        provider.saveCheckpoint(createTestCheckpoint("checkpoint-1"))
        provider.saveCheckpoint(createTestCheckpoint("checkpoint-2"))
        provider.saveCheckpoint(createTestCheckpoint("checkpoint-3"))

        // Verify count and ordering
        val allCheckpoints = provider.getCheckpoints()
        assertEquals(3, allCheckpoints.size)
        assertEquals("checkpoint-1", allCheckpoints[0].checkpointId)
        assertEquals("checkpoint-3", allCheckpoints[2].checkpointId)

        // Verify latest
        val latest = provider.getLatestCheckpoint()
        assertEquals("checkpoint-3", latest?.checkpointId)
    }

    @Test
    fun `test persistence ID isolation`() = runBlocking {
        val provider1 = H2PersistenceStorageProvider.inMemory(
            persistenceId = "agent-1",
            databaseName = "shared_db"
        )
        val provider2 = H2PersistenceStorageProvider.inMemory(
            persistenceId = "agent-2",
            databaseName = "shared_db" // Same database
        )

        provider1.migrate()
        provider2.migrate()

        // Save to different agents
        provider1.saveCheckpoint(createTestCheckpoint("agent1-data"))
        provider2.saveCheckpoint(createTestCheckpoint("agent2-data"))

        // Verify isolation
        val agent1Checkpoints = provider1.getCheckpoints()
        val agent2Checkpoints = provider2.getCheckpoints()

        assertEquals(1, agent1Checkpoints.size)
        assertEquals(1, agent2Checkpoints.size)
        assertEquals("agent1-data", agent1Checkpoints[0].checkpointId)
        assertEquals("agent2-data", agent2Checkpoints[0].checkpointId)
    }

    @Test
    fun `test TTL expiration`() = runBlocking {
        val provider = H2PersistenceStorageProvider.inMemory(
            persistenceId = "ttl-test",
            databaseName = "ttl_db",
            ttlSeconds = 1 // 1 second TTL
        )

        provider.migrate()

        // Save checkpoint
        provider.saveCheckpoint(createTestCheckpoint("expire-soon"))
        assertEquals(1, provider.getCheckpointCount())

        // Wait for expiration
        delay(1500)
        provider.conditionalCleanup()
        // Should be cleaned up on next operation
        val afterExpiry = provider.getLatestCheckpoint()
        assertNull(afterExpiry)
        assertEquals(0, provider.getCheckpointCount())
    }

    @Test
    fun `verify all providers can be instantiated`() {
        // H2
        assertNotNull(H2PersistenceStorageProvider.inMemory("test", "test_db"))

        // PostgreSQL
        assertNotNull(
            PostgresPersistenceStorageProvider(
                persistenceId = "test",
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
                persistenceId = "test",
                database = Database.connect(
                    url = "jdbc:mysql://localhost:3306/test",
                    driver = "com.mysql.cj.jdbc.Driver",
                    user = "test",
                    password = "test"
                )
            )
        )
    }

    private fun createTestCheckpoint(id: String): AgentCheckpointData {
        return AgentCheckpointData(
            checkpointId = id,
            createdAt = Clock.System.now(),
            nodeId = "test-node",
            lastInput = JsonPrimitive("Test input"),
            messageHistory = listOf(
                Message.System("You are a test assistant", RequestMetaInfo.create(Clock.System)),
                Message.User("Hello", RequestMetaInfo.create(Clock.System)),
                Message.Assistant("Hi there!", ResponseMetaInfo.create(Clock.System))
            )
        )
    }
}
