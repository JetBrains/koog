package ai.koog.agents.features.chathistory.jdbc

import ai.koog.agents.features.chatmemory.sql.SQLChatHistorySchemaMigrator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import javax.sql.DataSource

/**
 * Oracle JDBC implementation of [JdbcChatHistoryProvider].
 *
 * @param dataSource  The JDBC [DataSource] for Oracle connections.
 * @param tableName   Name of the table to store chat history (default: `"chat_history"`).
 * @param ttlSeconds  Optional TTL for history entries in seconds (`null` = no expiration).
 * @param migrator    Schema migrator for creating/updating the table.
 * @param json        JSON serializer instance for message serialization.
 */
public class OracleJdbcChatHistoryProvider @JvmOverloads constructor(
    dataSource: DataSource,
    tableName: String = "chat_history",
    ttlSeconds: Long? = null,
    migrator: SQLChatHistorySchemaMigrator = OracleJdbcChatHistorySchemaMigrator(dataSource, tableName),
    json: Json = defaultJson
) : JdbcChatHistoryProvider(dataSource, migrator, ttlSeconds, tableName) {

    /**
     * Bind parameter order matches the base class [JdbcChatHistoryProvider.upsert]:
     * 1. `conversation_id`
     * 2. `messages_json`
     * 3. `updated_at`
     * 4. `ttl_timestamp`
     */
    override val upsertSql: String = """
        MERGE INTO $tableName tgt
        USING (
            SELECT ? AS conversation_id,
                   ? AS messages_json,
                   ? AS updated_at,
                   ? AS ttl_timestamp
              FROM dual
        ) src ON (tgt.conversation_id = src.conversation_id)
        WHEN MATCHED THEN
            UPDATE SET
                tgt.messages_json  = src.messages_json,
                tgt.updated_at     = src.updated_at,
                tgt.ttl_timestamp  = src.ttl_timestamp
        WHEN NOT MATCHED THEN
            INSERT (conversation_id, messages_json, updated_at, ttl_timestamp)
            VALUES (src.conversation_id, src.messages_json, src.updated_at, src.ttl_timestamp)
    """.trimIndent()

    /**
     * Blocking variant of [migrate] for Java callers.
     *
     * Kotlin callers should use `migrate()` directly from a coroutine.
     *
     * ```java
     * OracleJdbcChatHistoryProvider provider =
     *     new OracleJdbcChatHistoryProvider(dataSource, "chat_history");
     * provider.migrateBlocking();
     * ```
     */
    public fun migrateBlocking(): Unit = runBlocking { migrate() }
}
