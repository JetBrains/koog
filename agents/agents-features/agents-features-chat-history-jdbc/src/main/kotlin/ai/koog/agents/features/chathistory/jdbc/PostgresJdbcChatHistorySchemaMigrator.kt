package ai.koog.agents.features.chathistory.jdbc

import ai.koog.agents.features.chatmemory.sql.SQLChatHistorySchemaMigrator
import javax.sql.DataSource

/**
 * PostgreSQL-specific JDBC implementation of [SQLChatHistorySchemaMigrator].
 *
 * Creates the chat history table with appropriate columns and indexes
 * using PostgreSQL-compatible SQL DDL.
 *
 * @param dataSource The JDBC DataSource to use for obtaining connections
 * @param tableName Name of the table to create
 */
public class PostgresJdbcChatHistorySchemaMigrator @JvmOverloads constructor(
    private val dataSource: DataSource,
    private val tableName: String = "chat_history"
) : SQLChatHistorySchemaMigrator {

    override suspend fun migrate() {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.createStatement().use { stmt ->
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS $tableName (
                            conversation_id VARCHAR(255) NOT NULL,
                            messages_json TEXT NOT NULL,
                            updated_at BIGINT NOT NULL,
                            ttl_timestamp BIGINT NULL,
                            CONSTRAINT ${tableName}_pkey PRIMARY KEY (conversation_id)
                        )
                        """.trimIndent()
                    )
                }
                connection.createStatement().use { stmt ->
                    stmt.execute(
                        "CREATE INDEX IF NOT EXISTS idx_${tableName}_updated_at ON $tableName (updated_at)"
                    )
                }
                connection.createStatement().use { stmt ->
                    stmt.execute(
                        "CREATE INDEX IF NOT EXISTS idx_${tableName}_ttl_timestamp ON $tableName (ttl_timestamp)"
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
