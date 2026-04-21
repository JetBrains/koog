package ai.koog.agents.features.persistence.jdbc

import ai.koog.agents.features.sql.providers.SQLPersistenceSchemaMigrator
import javax.sql.DataSource

/**
 * MySQL-specific JDBC implementation of [SQLPersistenceSchemaMigrator].
 *
 * Creates the agent checkpoints table using MySQL-compatible DDL with native JSON column type,
 * InnoDB engine, and utf8mb4 charset. Indexes are defined inline since MySQL does not
 * support `CREATE INDEX IF NOT EXISTS`.
 *
 * @param dataSource The JDBC DataSource to use for obtaining connections
 * @param tableName Name of the table to create
 */
public class MySQLJdbcPersistenceSchemaMigrator @JvmOverloads constructor(
    private val dataSource: DataSource,
    private val tableName: String = "agent_checkpoints"
) : SQLPersistenceSchemaMigrator {

    override suspend fun migrate() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS $tableName (
                        persistence_id VARCHAR(255) NOT NULL,
                        checkpoint_id VARCHAR(255) NOT NULL,
                        created_at BIGINT NOT NULL,
                        checkpoint_json JSON NOT NULL,
                        ttl_timestamp BIGINT NULL,
                        version BIGINT NOT NULL,
                        PRIMARY KEY (persistence_id, checkpoint_id),
                        INDEX idx_${tableName}_created_at (created_at),
                        INDEX idx_${tableName}_ttl_timestamp (ttl_timestamp),
                        INDEX idx_${tableName}_persistence_id_created_at (persistence_id, created_at),
                        UNIQUE INDEX idx_${tableName}_persistence_id_version (persistence_id, version)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """.trimIndent()
                )
            }
        }
    }
}
