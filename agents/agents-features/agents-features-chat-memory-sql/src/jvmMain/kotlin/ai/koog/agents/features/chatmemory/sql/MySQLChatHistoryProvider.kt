package ai.koog.agents.features.chatmemory.sql

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * MySQL-specific implementation of [ExposedChatHistoryProvider] for managing
 * chat conversation history in MySQL databases.
 *
 * This provider is optimized for MySQL 5.7+ and MariaDB 10.2+, leveraging their
 * native JSON column type for message storage.
 *
 * @param database The MySQL database connection
 * @param tableName Name of the table to store chat history (default: "chat_history")
 * @param ttlSeconds Optional TTL for history entries in seconds (null = no expiration)
 * @param migrator Schema migrator (default: MySQL-specific migrator)
 * @param json JSON serializer instance
 */
public class MySQLChatHistoryProvider(
    database: Database,
    tableName: String = "chat_history",
    ttlSeconds: Long? = null,
    migrator: SQLChatHistorySchemaMigrator = MySQLChatHistorySchemaMigrator(database, tableName),
    json: Json = defaultChatHistoryJson
) : ExposedChatHistoryProvider(database, tableName, ttlSeconds, migrator, json) {

    override suspend fun <T> transaction(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) {
            block()
        }
}

/**
 * MySQL-specific implementation of [SQLChatHistorySchemaMigrator]
 * for chat history tables.
 *
 * Creates the chat history table using MySQL-compatible DDL with native JSON column type
 * for the messages_json field, and InnoDB engine with utf8mb4 charset.
 *
 * @param database The MySQL database connection
 * @param tableName Name of the table to create
 */
public class MySQLChatHistorySchemaMigrator(
    private val database: Database,
    private val tableName: String
) : SQLChatHistorySchemaMigrator {
    override suspend fun migrate() {
        transaction(database) {
            exec(
                """
            CREATE TABLE IF NOT EXISTS $tableName (
                conversation_id VARCHAR(255) NOT NULL COMMENT 'Unique identifier for the conversation',
                messages_json JSON NOT NULL COMMENT 'JSON-serialized list of conversation messages',
                updated_at BIGINT NOT NULL COMMENT 'Timestamp of last update (epoch milliseconds)',
                ttl_timestamp BIGINT NULL COMMENT 'Time-to-live timestamp for automatic expiration (nullable)',

                PRIMARY KEY (conversation_id),
                INDEX idx_${tableName}_updated_at (updated_at),
                INDEX idx_${tableName}_ttl_timestamp (ttl_timestamp)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
              COMMENT='Stores chat conversation history with TTL support'
                """.trimIndent()
            )
        }
    }
}
