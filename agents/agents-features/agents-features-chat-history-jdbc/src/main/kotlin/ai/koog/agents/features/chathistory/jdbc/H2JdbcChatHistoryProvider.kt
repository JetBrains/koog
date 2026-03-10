package ai.koog.agents.features.chathistory.jdbc

import ai.koog.agents.features.chatmemory.sql.SQLChatHistorySchemaMigrator
import kotlinx.serialization.json.Json
import javax.sql.DataSource

/**
 * H2-specific JDBC implementation of [JdbcChatHistoryProvider].
 *
 * Uses `MERGE INTO ... KEY (...) VALUES (...)` for upsert operations.
 *
 * @param dataSource The JDBC DataSource for H2 connections
 * @param tableName Name of the table to store chat history (default: "chat_history")
 * @param ttlSeconds Optional TTL for history entries in seconds (null = no expiration)
 * @param migrator Schema migrator for creating/updating the table
 * @param json JSON serializer instance for message serialization
 */
public class H2JdbcChatHistoryProvider @JvmOverloads constructor(
    dataSource: DataSource,
    tableName: String = "chat_history",
    ttlSeconds: Long? = null,
    migrator: SQLChatHistorySchemaMigrator = H2JdbcChatHistorySchemaMigrator(dataSource, tableName),
    json: Json = defaultJson
) : JdbcChatHistoryProvider(dataSource, migrator, ttlSeconds, tableName) {

    override val upsertSql: String = """
        MERGE INTO $tableName (conversation_id, messages_json, updated_at, ttl_timestamp)
        KEY (conversation_id)
        VALUES (?, ?, ?, ?)
    """.trimIndent()
}
