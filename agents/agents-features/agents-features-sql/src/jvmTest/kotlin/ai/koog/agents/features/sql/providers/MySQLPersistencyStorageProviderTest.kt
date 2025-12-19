package ai.koog.agents.features.sql.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.test.utils.DockerAvailableCondition
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(DockerAvailableCondition::class)
@Execution(ExecutionMode.SAME_THREAD)
class MySQLPersistenceStorageProviderTest {

    private val agentId = "mysql-agent"

    private lateinit var mysql: MySQLContainer<*>

    @BeforeAll
    fun setUp() {
        mysql = MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
        mysql.start()
    }

    @AfterAll
    fun tearDown() {
        mysql.stop()
    }

    private fun provider(ttlSeconds: Long? = null): MySQLPersistenceStorageProvider {
        val db: Database = Database.connect(
            url = mysql.jdbcUrl + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            driver = "com.mysql.cj.jdbc.Driver",
            user = mysql.username,
            password = mysql.password
        )
        return MySQLPersistenceStorageProvider(
            database = db,
            tableName = "agent_checkpoints_test",
            ttlSeconds = ttlSeconds
        )
    }

    @Test
    fun `migrate and basic CRUD against real MySQL`() = runBlocking {
        val p = provider()
        p.migrate()

        // empty
        assertNull(p.getLatestCheckpoint(agentId))
        assertEquals(0, p.getCheckpointCount(agentId))

        // save
        val cp1 = createTestCheckpoint("cp-1", 0L)
        p.saveCheckpoint(agentId, cp1)

        // read
        val latest1 = p.getLatestCheckpoint(agentId)
        assertNotNull(latest1)
        assertEquals("cp-1", latest1.checkpointId)
        assertEquals(1, p.getCheckpoints(agentId).size)
        assertEquals(1, p.getCheckpointCount(agentId))

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
    fun `ttl cleanup removes expired rows`() = runBlocking {
        val p = provider(ttlSeconds = 1)
        p.migrate()

        p.saveCheckpoint(agentId, createTestCheckpoint("will-expire", 0L))
        assertEquals(1, p.getCheckpointCount(agentId))

        // Sleep slightly over 1s to ensure ttl passes
        delay(1100)
        // force cleanup
        p.cleanupExpired()

        assertEquals(0, p.getCheckpointCount(agentId))
        assertNull(p.getLatestCheckpoint(agentId))
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
