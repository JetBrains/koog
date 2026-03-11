package ai.koog.agents.features.chathistory.jdbc

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
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

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(DockerAvailableCondition::class)
@Execution(ExecutionMode.SAME_THREAD)
class PostgresJdbcChatHistoryProviderTest : AbstractJdbcChatHistoryProviderTest() {

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

    override fun provider(tableName: String, ttlSeconds: Long?): PostgresJdbcChatHistoryProvider {
        return PostgresJdbcChatHistoryProvider(
            dataSource = dataSource,
            tableName = tableName,
            ttlSeconds = ttlSeconds
        )
    }

    @Test
    fun testMultipleConversationsPersistAcrossRuns() = runBlocking {
        val tableName = "chat_multi_persist_test"

        val run1 = provider(tableName = tableName)
        run1.migrate()

        run1.store(
            "agent-alice",
            listOf(
                Message.System("You help with math.", RequestMetaInfo.create(Clock.System)),
                Message.User("What is 2+2?", RequestMetaInfo.create(Clock.System)),
                Message.Assistant("4", ResponseMetaInfo.create(Clock.System))
            )
        )
        run1.store(
            "agent-bob",
            listOf(
                Message.System("You help with history.", RequestMetaInfo.create(Clock.System)),
                Message.User("When was the moon landing?", RequestMetaInfo.create(Clock.System)),
                Message.Assistant("July 20, 1969.", ResponseMetaInfo.create(Clock.System))
            )
        )
        assertEquals(2, run1.getConversationCount())

        val run2 = provider(tableName = tableName)
        run2.migrate()

        val aliceHistory = run2.load("agent-alice")
        assertEquals(3, aliceHistory.size)
        val aliceUpdated = aliceHistory + listOf(
            Message.User("And 3+3?", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("6", ResponseMetaInfo.create(Clock.System))
        )
        run2.store("agent-alice", aliceUpdated)

        val bobHistory = run2.load("agent-bob")
        assertEquals(3, bobHistory.size)
        val bobUpdated = bobHistory + listOf(
            Message.User("Who was the first person on the moon?", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("Neil Armstrong.", ResponseMetaInfo.create(Clock.System))
        )
        run2.store("agent-bob", bobUpdated)

        val run3 = provider(tableName = tableName)
        run3.migrate()

        assertEquals(2, run3.getConversationCount())

        val aliceFinal = run3.load("agent-alice")
        assertEquals(5, aliceFinal.size)
        assertEquals("And 3+3?", aliceFinal[3].content)
        assertEquals("6", aliceFinal[4].content)

        val bobFinal = run3.load("agent-bob")
        assertEquals(5, bobFinal.size)
        assertEquals("Who was the first person on the moon?", bobFinal[3].content)
        assertEquals("Neil Armstrong.", bobFinal[4].content)
    }
}
