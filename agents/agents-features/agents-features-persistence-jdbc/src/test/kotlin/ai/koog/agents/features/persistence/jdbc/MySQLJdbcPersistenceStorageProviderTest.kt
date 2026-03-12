package ai.koog.agents.features.persistence.jdbc

import ai.koog.test.utils.DockerAvailableCondition
import com.mysql.cj.jdbc.MysqlDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(DockerAvailableCondition::class)
@Execution(ExecutionMode.SAME_THREAD)
class MySQLJdbcPersistenceStorageProviderTest : AbstractJdbcPersistenceStorageProviderTest() {

    private lateinit var mysql: MySQLContainer<*>
    private lateinit var dataSource: MysqlDataSource

    @BeforeAll
    fun setUp() {
        mysql = MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
        mysql.start()

        dataSource = MysqlDataSource().apply {
            setUrl(mysql.jdbcUrl)
            user = mysql.username
            password = mysql.password
        }
    }

    @AfterAll
    fun tearDown() {
        mysql.stop()
    }

    override fun provider(tableName: String, ttlSeconds: Long?): MySQLJdbcPersistenceStorageProvider {
        return MySQLJdbcPersistenceStorageProvider(
            dataSource = dataSource,
            tableName = tableName,
            ttlSeconds = ttlSeconds
        )
    }
}
