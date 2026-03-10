package ai.koog.agents.features.chathistory.jdbc

import ai.koog.agents.features.chatmemory.sql.SQLChatHistorySchemaMigrator
import kotlinx.serialization.json.Json
import javax.sql.DataSource

/**
 * MySQL-specific JDBC implementation of [JdbcChatHistoryProvider].
 *
 * Uses `INSERT ... ON DUPLICATE KEY UPDATE` for upsert operations.
 * Compatible with MySQL 5.7+ and MariaDB 10.2+.
 *
 * @param dataSource The JDBC DataSource for MySQL connections
 * @param tableName Name of the table to store chat history (default: "chat_history")
 * @param ttlSeconds Optional TTL for history entries in seconds (null = no expiration)
 * @param migrator Schema migrator for creating/updating the table
 * @param json JSON serializer instance for message serialization
 */
public class MySQLJdbcChatHistoryProvider @JvmOverloads constructor(
    dataSource: DataSource,
    tableName: String = "chat_history",
    ttlSeconds: Long? = null,
    migrator: SQLChatHistorySchemaMigrator = MySQLJdbcChatHistorySchemaMigrator(dataSource, tableName),
    json: Json = defaultJson
) : JdbcChatHistoryProvider(dataSource, migrator, ttlSeconds, tableName) {

    override val upsertSql: String = """
        INSERT INTO $tableName (conversation_id, messages_json, updated_at, ttl_timestamp)
        VALUES (?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            messages_json = VALUES(messages_json),
            updated_at = VALUES(updated_at),
            ttl_timestamp = VALUES(ttl_timestamp)
    """.trimIndent()
}
