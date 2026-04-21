package ai.koog.agents.features.chathistory.jdbc

import ai.koog.agents.features.chatmemory.sql.SQLChatHistorySchemaMigrator
import kotlinx.serialization.json.Json
import javax.sql.DataSource

/**
 * PostgreSQL-specific JDBC implementation of [JdbcChatHistoryProvider].
 *
 * Uses `INSERT ... ON CONFLICT (...) DO UPDATE` for upsert operations.
 *
 * @param dataSource The JDBC DataSource for PostgreSQL connections
 * @param tableName Name of the table to store chat history (default: "chat_history")
 * @param ttlSeconds Optional TTL for history entries in seconds (null = no expiration)
 * @param migrator Schema migrator for creating/updating the table
 * @param json JSON serializer instance for message serialization
 */
public class PostgresJdbcChatHistoryProvider @JvmOverloads constructor(
    dataSource: DataSource,
    tableName: String = "chat_history",
    ttlSeconds: Long? = null,
    migrator: SQLChatHistorySchemaMigrator = PostgresJdbcChatHistorySchemaMigrator(dataSource, tableName),
    json: Json = defaultJson
) : JdbcChatHistoryProvider(dataSource, migrator, ttlSeconds, tableName) {

    override val upsertSql: String = """
        INSERT INTO $tableName (conversation_id, messages_json, updated_at, ttl_timestamp)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (conversation_id) DO UPDATE SET
            messages_json = EXCLUDED.messages_json,
            updated_at = EXCLUDED.updated_at,
            ttl_timestamp = EXCLUDED.ttl_timestamp
    """.trimIndent()
}
