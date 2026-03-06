package ai.koog.agents.features.chatmemory.sql

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * H2 Database-specific implementation of [ExposedChatHistoryProvider] for managing
 * chat conversation history in H2 databases.
 *
 * @param database The H2 database connection
 * @param tableName Name of the table to store chat history (default: "chat_history")
 * @param ttlSeconds Optional TTL for history entries in seconds (null = no expiration)
 * @param migrator Schema migrator (default: H2-specific migrator)
 * @param json JSON serializer instance
 */
public class H2ChatHistoryProvider(
    database: Database,
    tableName: String = "chat_history",
    ttlSeconds: Long? = null,
    migrator: SQLChatHistorySchemaMigrator = H2ChatHistorySchemaMigrator(database, tableName),
    json: Json = defaultChatHistoryJson
) : ExposedChatHistoryProvider(database, tableName, ttlSeconds, migrator, json) {

    public override suspend fun <T> transaction(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) {
            block()
        }

    /**
     * Companion object providing factory methods for common H2 configurations.
     */
    public companion object {
        /**
         * Creates an in-memory H2 chat history provider.
         * Data is lost when the JVM shuts down.
         * Perfect for testing and temporary caching.
         *
         * @param databaseName Name of the in-memory database
         * @param options Additional H2 options (e.g., "DB_CLOSE_DELAY=-1")
         * @param tableName Name of the table to store chat history
         * @param ttlSeconds Optional TTL for history entries in seconds
         */
        public fun inMemory(
            databaseName: String = "test",
            options: String = "DB_CLOSE_DELAY=-1",
            tableName: String = "chat_history",
            ttlSeconds: Long? = null
        ): H2ChatHistoryProvider = H2ChatHistoryProvider(
            database = Database.connect("jdbc:h2:mem:$databaseName;$options"),
            tableName = tableName,
            ttlSeconds = ttlSeconds
        )

        /**
         * Creates a file-based H2 chat history provider.
         * Data is persisted to a file on disk.
         * Good balance between performance and persistence.
         *
         * @param filePath Path to the database file (without .mv.db extension)
         * @param options Additional H2 options
         * @param tableName Name of the table to store chat history
         * @param ttlSeconds Optional TTL for history entries in seconds
         */
        public fun fileBased(
            filePath: String,
            options: String = "",
            tableName: String = "chat_history",
            ttlSeconds: Long? = null
        ): H2ChatHistoryProvider = H2ChatHistoryProvider(
            database = Database.connect(
                if (options.isNotEmpty()) {
                    "jdbc:h2:file:$filePath;$options"
                } else {
                    "jdbc:h2:file:$filePath"
                }
            ),
            tableName = tableName,
            ttlSeconds = ttlSeconds
        )
    }
}

/**
 * H2-specific implementation of the [SQLChatHistorySchemaMigrator] interface
 * for chat history tables.
 *
 * Creates the chat history table with appropriate columns and indexes
 * using H2-compatible SQL DDL.
 *
 * @param database The H2 database connection
 * @param tableName Name of the table to create
 */
public class H2ChatHistorySchemaMigrator(
    private val database: Database,
    private val tableName: String
) : SQLChatHistorySchemaMigrator {
    override suspend fun migrate() {
        transaction(database) {
            exec(
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

            exec(
                """
            CREATE INDEX IF NOT EXISTS idx_${tableName}_updated_at ON $tableName (updated_at)
                """.trimIndent()
            )

            exec(
                """
            CREATE INDEX IF NOT EXISTS idx_${tableName}_ttl_timestamp ON $tableName (ttl_timestamp)
                """.trimIndent()
            )
        }
    }
}
