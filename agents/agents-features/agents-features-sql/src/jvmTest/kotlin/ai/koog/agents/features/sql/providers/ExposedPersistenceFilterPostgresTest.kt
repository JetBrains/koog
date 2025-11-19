package ai.koog.agents.features.sql.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.test.utils.DockerAvailableCondition
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * Tests ExposedPersistenceFilter in combination with PostgresPersistenceStorageProvider using Testcontainers.
 */
@ExtendWith(DockerAvailableCondition::class)
@TestInstance(Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class ExposedPersistenceFilterPostgresTest {

    private lateinit var postgres: PostgreSQLContainer<*>

    private val tableName = "agent_checkpoints_filter_test"

    @BeforeAll
    fun setUp() {
        postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
        postgres.start()
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    private fun provider(ttlSeconds: Long? = null): PostgresPersistenceStorageProvider {
        val db: Database = Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )
        return PostgresPersistenceStorageProvider(
            database = db,
            tableName = tableName,
            ttlSeconds = ttlSeconds
        )
    }

    @Test
    fun `filter by createdAt and agent id returns expected checkpoints`() = runBlocking {
        val p = provider()
        p.migrate()

        val agentId = "agent-filter-1"
        val otherAgent = "agent-filter-2"

        val baseTime = Clock.System.now()

        val checkpoint1 = createTestCheckpoint("cp-a1", baseTime - 10.seconds, version = 0)
        val checkpoint2 = createTestCheckpoint("cp-a2", baseTime - 5.seconds, version = checkpoint1.version.plus(1))
        val checkpoint3 = createTestCheckpoint("cp-a3", baseTime, version = checkpoint2.version.plus(1))

        // Seed data: 3 for agentId, 2 for otherAgent
        p.saveCheckpoint(agentId, checkpoint1)
        p.saveCheckpoint(agentId, checkpoint2)
        p.saveCheckpoint(agentId, checkpoint3)

        val checkpointB1 = createTestCheckpoint("cp-b1", baseTime - 3.seconds, version = 0)
        val checkpointB2 = createTestCheckpoint("cp-b2", baseTime + 2.seconds, version = checkpointB1.version.plus(1))
        p.saveCheckpoint(otherAgent, checkpointB1)
        p.saveCheckpoint(otherAgent, checkpointB2)

        // Build filter: for agentId and createdAt >= baseTime - 5s
        val filter = CreatedAfterForAgentFilter(agentId, threshold = baseTime - 5.seconds)

        val filtered = p.getCheckpoints(agentId, filter)
        // Should include cp-a2 and cp-a3, in ascending createdAt order
        assertEquals(listOf("cp-a3", "cp-a2"), filtered.map { it.checkpointId })

        val latest = p.getLatestCheckpoint(agentId, filter)
        assertNotNull(latest)
        assertEquals("cp-a3", latest.checkpointId)
    }

    @Test
    fun `filter by checkpoint id prefix limits results`() = runBlocking {
        val p = provider()
        p.migrate()

        val agentId = "agent-prefix-1"
        val baseTime = Clock.System.now()

        val checkpoint1 = createTestCheckpoint("order-001", baseTime - 2.seconds, version = 0)
        val checkpoint2 = createTestCheckpoint("order-002", baseTime - 1.seconds, version = checkpoint1.version.plus(1))
        val checkpoint3 = createTestCheckpoint("note-001", baseTime, version = checkpoint2.version.plus(1))

        p.saveCheckpoint(agentId, checkpoint1)
        p.saveCheckpoint(agentId, checkpoint2)
        p.saveCheckpoint(agentId, checkpoint3)

        val filter = CheckpointIdPrefixFilter(agentId, prefix = "order-")

        val all = p.getCheckpoints(agentId, filter)
        assertEquals(listOf("order-002", "order-001"), all.map { it.checkpointId })

        val latest = p.getLatestCheckpoint(agentId, filter)
        assertNotNull(latest)
        assertEquals("order-002", latest.checkpointId)
    }

    private fun createTestCheckpoint(id: String, createdAt: Instant, version: Long): AgentCheckpointData {
        return AgentCheckpointData(
            checkpointId = id,
            createdAt = createdAt,
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

    /**
     * Test implementation of ExposedPersistenceFilter filtering by agent id and createdAt threshold.
     */
    private class CreatedAfterForAgentFilter(
        private val agentId: String,
        private val threshold: Instant
    ) : ExposedPersistenceFilter {
        override fun query(table: CheckpointsTable) =
            table.select(table.checkpointJson).where {
                (table.persistenceId eq agentId) and (table.createdAt greaterEq threshold.toEpochMilliseconds())
            }.orderBy(table.createdAt, SortOrder.DESC)
    }

    /**
     * Test implementation of ExposedPersistenceFilter filtering by agent id and checkpoint id prefix.
     */
    private class CheckpointIdPrefixFilter(
        private val agentId: String,
        private val prefix: String
    ) : ExposedPersistenceFilter {
        override fun query(table: CheckpointsTable) =
            table.select(table.checkpointJson).where {
                (table.persistenceId eq agentId) and (table.checkpointId like "$prefix%")
            }.orderBy(table.createdAt, SortOrder.DESC)
    }
}
