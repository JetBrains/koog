package ai.koog.agents.features.persistence.jdbc

import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@TestInstance(Lifecycle.PER_METHOD)
@Execution(ExecutionMode.SAME_THREAD)
class H2JdbcPersistenceStorageProviderTest : AbstractJdbcPersistenceStorageProviderTest() {

    private lateinit var dataSource: JdbcDataSource

    @BeforeEach
    fun setUp() {
        dataSource = JdbcDataSource().apply {
            setUrl("jdbc:h2:mem:checkpoints_test_${System.nanoTime()};DB_CLOSE_DELAY=-1")
        }
    }

    override fun provider(tableName: String, ttlSeconds: Long?): H2JdbcPersistenceStorageProvider {
        return H2JdbcPersistenceStorageProvider(
            dataSource = dataSource,
            tableName = tableName,
            ttlSeconds = ttlSeconds
        )
    }
}
