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
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(DockerAvailableCondition::class)
class MySQLPersistencyStorageProviderTest {

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

    private fun provider(ttlSeconds: Long? = null): MySQLPersistencyStorageProvider {
        val db: Database = Database.connect(
            url = mysql.jdbcUrl + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            driver = "com.mysql.cj.jdbc.Driver",
            user = mysql.username,
            password = mysql.password
        )
        return MySQLPersistencyStorageProvider(
            persistenceId = "mysql-agent",
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
        assertNull(p.getLatestCheckpoint())
        assertEquals(0, p.getCheckpointCount())

        // save
        val cp1 = createTestCheckpoint("cp-1")
        p.saveCheckpoint(cp1)

        // read
        val latest1 = p.getLatestCheckpoint()
        assertNotNull(latest1)
        assertEquals("cp-1", latest1.checkpointId)
        assertEquals(1, p.getCheckpoints().size)
        assertEquals(1, p.getCheckpointCount())

        // upsert same id should be idempotent (no duplicates due PK)
        p.saveCheckpoint(cp1)
        assertEquals(1, p.getCheckpoints().size)

        // insert second
        val cp2 = createTestCheckpoint("cp-2")
        p.saveCheckpoint(cp2)
        val all = p.getCheckpoints()
        assertEquals(listOf("cp-1", "cp-2"), all.map { it.checkpointId })
        assertEquals("cp-2", p.getLatestCheckpoint()!!.checkpointId)

        // delete single
        p.deleteCheckpoint("cp-1")
        assertEquals(listOf("cp-2"), p.getCheckpoints().map { it.checkpointId })

        // delete all
        p.deleteAllCheckpoints()
        assertEquals(0, p.getCheckpointCount())
    }

    @Test
    fun `ttl cleanup removes expired rows`() = runBlocking {
        val p = provider(ttlSeconds = 1)
        p.migrate()

        p.saveCheckpoint(createTestCheckpoint("will-expire"))
        assertEquals(1, p.getCheckpointCount())

        // Sleep slightly over 1s to ensure ttl passes
        delay(1100)
        // force cleanup
        p.cleanupExpired()

        assertEquals(0, p.getCheckpointCount())
        assertNull(p.getLatestCheckpoint())
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
