package ai.koog.agents.features.chathistory.jdbc

import ai.koog.test.utils.DockerAvailableCondition
import kotlinx.coroutines.runBlocking
import oracle.jdbc.pool.OracleDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Properties
import kotlin.test.assertEquals

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(DockerAvailableCondition::class)
@Execution(ExecutionMode.SAME_THREAD)
class OracleJdbcChatHistoryProviderTest : AbstractJdbcChatHistoryProviderTest() {

    private val fallbackHosts: List<String> = listOf("127.0.0.1")
    private lateinit var oracle: GenericContainer<*>
    private lateinit var dataSource: OracleDataSource

    @BeforeAll
    fun setUp() {
        val imageName = DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart")

        oracle = GenericContainer(imageName)
            .withEnv("ORACLE_PASSWORD", "test")
            .withExposedPorts(1521)
            .waitingFor(Wait.forLogMessage("(?s).*DATABASE IS READY TO USE!.*", 1))
        oracle.start()

        val jdbcUrl = waitForDatabaseReady(oracle.host, oracle.getMappedPort(1521))
        dataSource = OracleDataSource().apply {
            setURL(jdbcUrl)
            setUser("system")
            setPassword("test")
        }
    }

    @AfterAll
    fun tearDown() {
        oracle.stop()
    }

    override fun provider(tableName: String, ttlSeconds: Long?): OracleJdbcChatHistoryProvider {
        return OracleJdbcChatHistoryProvider(
            dataSource = dataSource,
            tableName = tableName,
            ttlSeconds = ttlSeconds
        )
    }

    @Test
    fun testMigrateTwiceIsIdempotent() = runBlocking {
        val p = provider(tableName = "chat_oracle_idempotent_test")
        p.migrate()
        p.migrate()

        p.store("oracle-conv-1", createTestMessages())
        assertEquals(1, p.getConversationCount())
    }

    private fun waitForDatabaseReady(host: String, port: Int): String {
        val timeoutAt = System.currentTimeMillis() + Duration.ofMinutes(15).toMillis()
        var lastError: Throwable? = null
        val hostsToTry = buildList {
            add(host)
            addAll(fallbackHosts)
        }.distinct()

        while (System.currentTimeMillis() < timeoutAt) {
            for (probeHost in hostsToTry) {
                val url = "jdbc:oracle:thin:@//$probeHost:$port/freepdb1"
                try {
                    val probeDataSource = OracleDataSource().apply {
                        setURL(url)
                        setUser("system")
                        setPassword("test")
                        connectionProperties = Properties().apply {
                            setProperty("oracle.net.CONNECT_TIMEOUT", "5000")
                            setProperty("oracle.jdbc.ReadTimeout", "10000")
                        }
                    }
                    probeDataSource.connection.use { connection ->
                        connection.prepareStatement("SELECT 1 FROM dual").use { stmt ->
                            stmt.executeQuery().use { rs ->
                                if (rs.next()) return url
                            }
                        }
                    }
                } catch (t: Throwable) {
                    lastError = t
                }
            }
            Thread.sleep(2_000)
        }

        throw IllegalStateException(
            "Oracle DB did not become ready",
            lastError
        )
    }
}
