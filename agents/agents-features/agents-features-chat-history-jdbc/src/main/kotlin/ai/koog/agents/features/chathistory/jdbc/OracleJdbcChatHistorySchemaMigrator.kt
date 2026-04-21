package ai.koog.agents.features.chathistory.jdbc

import ai.koog.agents.features.chatmemory.sql.SQLChatHistorySchemaMigrator
import java.sql.SQLException
import javax.sql.DataSource

/**
 * Oracle JDBC implementation of [SQLChatHistorySchemaMigrator].
 *
 * Creates the chat history table and its indexes.
 *
 * **Requires Oracle 23ai.** `CREATE TABLE IF NOT EXISTS` was introduced in 23ai;
 * earlier versions are not supported.
 **
 * @param dataSource  The JDBC [DataSource] to use for obtaining connections.
 * @param tableName   Name of the table to create (default: `"chat_history"`).
 */
public class OracleJdbcChatHistorySchemaMigrator @JvmOverloads constructor(
    private val dataSource: DataSource,
    private val tableName: String = "chat_history"
) : SQLChatHistorySchemaMigrator {

    override suspend fun migrate() {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                // Requires Oracle 23ai — IF NOT EXISTS for CREATE TABLE is a 23ai feature.
                // conversation_id is the primary key (one row per conversation).
                // messages_json holds the full serialised message list as CLOB.
                // updated_at / ttl_timestamp are epoch-millisecond integers (NUMBER).
                connection.createStatement().use { stmt ->
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS $tableName (
                            conversation_id VARCHAR2(255) NOT NULL,
                            messages_json   CLOB          NOT NULL,
                            updated_at      NUMBER        NOT NULL,
                            ttl_timestamp   NUMBER        NULL,
                            CONSTRAINT ${tableName}_pkey PRIMARY KEY (conversation_id)
                        )
                        """.trimIndent()
                    )
                }

                // Note: CREATE INDEX IF NOT EXISTS is not supported, even in Oracle 23ai.
                // We catch ORA-00955 (name already used by an existing object) and
                // treat it as a no-op, which is the standard workaround.
                runCatching {
                    connection.createStatement().use { stmt ->
                        stmt.execute(
                            "CREATE INDEX idx_${tableName}_updated_at ON $tableName (updated_at)"
                        )
                    }
                }.onFailure { e -> if (!isNameAlreadyUsed(e)) throw e }

                // ── Index: ttl_timestamp (expired-row cleanup) ────────────────
                runCatching {
                    connection.createStatement().use { stmt ->
                        stmt.execute(
                            "CREATE INDEX idx_${tableName}_ttl_timestamp ON $tableName (ttl_timestamp)"
                        )
                    }
                }.onFailure { e -> if (!isNameAlreadyUsed(e)) throw e }

                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }
    }

    /**
     * Returns `true` if the exception is Oracle error ORA-00955:
     * "name is already used by an existing object".
     *
     * Used to emulate `CREATE INDEX IF NOT EXISTS`, which Oracle does not
     * support even in 23ai.
     */
    private fun isNameAlreadyUsed(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            if (current is SQLException && current.errorCode == 955) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
