package ai.koog.agents.features.persistence.jdbc

import ai.koog.agents.features.sql.providers.SQLPersistenceSchemaMigrator
import javax.sql.DataSource

/**
 * PostgreSQL-specific JDBC implementation of [SQLPersistenceSchemaMigrator].
 *
 * Creates the agent checkpoints table with appropriate columns and indexes
 * using PostgreSQL-compatible SQL DDL.
 *
 * @param dataSource The JDBC DataSource to use for obtaining connections
 * @param tableName Name of the table to create
 */
public class PostgresJdbcPersistenceSchemaMigrator @JvmOverloads constructor(
    private val dataSource: DataSource,
    private val tableName: String = "agent_checkpoints"
) : SQLPersistenceSchemaMigrator {

    override suspend fun migrate() {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.createStatement().use { stmt ->
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS $tableName (
                            persistence_id VARCHAR(255) NOT NULL,
                            checkpoint_id VARCHAR(255) NOT NULL,
                            created_at BIGINT NOT NULL,
                            checkpoint_json TEXT NOT NULL,
                            ttl_timestamp BIGINT NULL,
                            version BIGINT NOT NULL,
                            CONSTRAINT ${tableName}_pkey PRIMARY KEY (persistence_id, checkpoint_id)
                        )
                        """.trimIndent()
                    )
                }
                connection.createStatement().use { stmt ->
                    stmt.execute(
                        "CREATE INDEX IF NOT EXISTS idx_${tableName}_created_at ON $tableName (created_at)"
                    )
                }
                connection.createStatement().use { stmt ->
                    stmt.execute(
                        "CREATE INDEX IF NOT EXISTS idx_${tableName}_ttl_timestamp ON $tableName (ttl_timestamp)"
                    )
                }
                connection.createStatement().use { stmt ->
                    stmt.execute(
                        "CREATE INDEX IF NOT EXISTS idx_${tableName}_persistence_id_created_at ON $tableName (persistence_id, created_at)"
                    )
                }
                connection.createStatement().use { stmt ->
                    stmt.execute(
                        "CREATE UNIQUE INDEX IF NOT EXISTS idx_${tableName}_persistence_id_version ON $tableName (persistence_id, version)"
                    )
                }
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }
    }
}
