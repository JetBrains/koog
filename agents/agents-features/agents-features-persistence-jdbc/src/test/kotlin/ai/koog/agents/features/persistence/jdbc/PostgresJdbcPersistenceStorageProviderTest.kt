package ai.koog.agents.features.persistence.jdbc

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.JSONPrimitive
import ai.koog.test.utils.DockerAvailableCondition
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(DockerAvailableCondition::class)
@Execution(ExecutionMode.SAME_THREAD)
class PostgresJdbcPersistenceStorageProviderTest : AbstractJdbcPersistenceStorageProviderTest() {

    private lateinit var postgres: PostgreSQLContainer<*>
    private lateinit var dataSource: PGSimpleDataSource

    @BeforeAll
    fun setUp() {
        postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
        postgres.start()

        dataSource = PGSimpleDataSource().apply {
            setUrl(postgres.jdbcUrl)
            user = postgres.username
            password = postgres.password
        }
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    override fun provider(tableName: String, ttlSeconds: Long?): PostgresJdbcPersistenceStorageProvider {
        return PostgresJdbcPersistenceStorageProvider(
            dataSource = dataSource,
            tableName = tableName,
            ttlSeconds = ttlSeconds
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testMultipleSessionsPersistAcrossRuns() = runBlocking {
        val tableName = "checkpoints_multi_persist_test"

        val run1 = provider(tableName = tableName)
        run1.migrate()

        run1.saveCheckpoint(
            "agent-alice",
            AgentCheckpointData(
                checkpointId = Uuid.random().toString(),
                createdAt = Clock.System.now(),
                nodePath = "graph/math/solve",
                lastOutput = JSONPrimitive("4"),
                messageHistory = listOf(
                    Message.System("You help with math.", RequestMetaInfo.create(Clock.System)),
                    Message.User("What is 2+2?", RequestMetaInfo.create(Clock.System)),
                    Message.Assistant("4", ResponseMetaInfo.create(Clock.System))
                ),
                version = 1L
            )
        )
        run1.saveCheckpoint(
            "agent-bob",
            AgentCheckpointData(
                checkpointId = Uuid.random().toString(),
                createdAt = Clock.System.now(),
                nodePath = "graph/history/answer",
                lastOutput = JSONPrimitive("July 20, 1969."),
                messageHistory = listOf(
                    Message.System("You help with history.", RequestMetaInfo.create(Clock.System)),
                    Message.User("When was the moon landing?", RequestMetaInfo.create(Clock.System)),
                    Message.Assistant("July 20, 1969.", ResponseMetaInfo.create(Clock.System))
                ),
                version = 1L
            )
        )

        assertEquals(1, run1.getCheckpointCount("agent-alice"))
        assertEquals(1, run1.getCheckpointCount("agent-bob"))

        val run2 = provider(tableName = tableName)
        run2.migrate()

        val aliceCheckpoints = run2.getCheckpoints("agent-alice")
        assertEquals(1, aliceCheckpoints.size)
        assertEquals("graph/math/solve", aliceCheckpoints[0].nodePath)

        run2.saveCheckpoint(
            "agent-alice",
            AgentCheckpointData(
                checkpointId = Uuid.random().toString(),
                createdAt = Clock.System.now(),
                nodePath = "graph/math/solve",
                lastOutput = JSONPrimitive("6"),
                messageHistory = aliceCheckpoints[0].messageHistory + listOf(
                    Message.User("And 3+3?", RequestMetaInfo.create(Clock.System)),
                    Message.Assistant("6", ResponseMetaInfo.create(Clock.System))
                ),
                version = 2L
            )
        )

        val run3 = provider(tableName = tableName)
        run3.migrate()

        assertEquals(2, run3.getCheckpointCount("agent-alice"))
        assertEquals(1, run3.getCheckpointCount("agent-bob"))

        val aliceFinal = run3.getLatestCheckpoint("agent-alice")
        assertEquals(2L, aliceFinal?.version)
        assertEquals(5, aliceFinal?.messageHistory?.size)
    }
}
