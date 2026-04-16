package ai.koog.agents.features.chatmemory.sql

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * PostgreSQL-specific implementation of [ExposedChatHistoryProvider] for managing
 * chat conversation history in PostgreSQL databases.
 *
 * @param database The PostgreSQL database connection
 * @param tableName Name of the table to store chat history (default: "chat_history")
 * @param ttlSeconds Optional TTL for history entries in seconds (null = no expiration)
 * @param migrator Schema migrator (default: PostgreSQL-specific migrator)
 * @param json JSON serializer instance
 */
public class PostgresChatHistoryProvider @JvmOverloads constructor(
    database: Database,
    tableName: String = "chat_history",
    ttlSeconds: Long? = null,
    migrator: SQLChatHistorySchemaMigrator = PostgresChatHistorySchemaMigrator(database, tableName),
    json: Json = defaultChatHistoryJson
) : ExposedChatHistoryProvider(database, tableName, ttlSeconds, migrator, json) {

    override suspend fun <T> transaction(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) { block() }

    /**
     * Companion object providing a builder for [PostgresChatHistoryProvider].
     */
    public companion object {
        /**
         * Gets an instance of the [Builder].
         */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }

    /**
     * Builder for constructing [PostgresChatHistoryProvider] instances.
     */
    public class Builder internal constructor() {
        internal var database: Database? = null
        internal var tableName: String = "chat_history"
        internal var ttlSeconds: Long? = null
        internal var migrator: SQLChatHistorySchemaMigrator? = null
        internal var json: Json = defaultChatHistoryJson

        /**
         * Set the database connection.
         */
        public fun database(database: Database): Builder = apply { this.database = database }

        /**
         * Specify the table name for storing chat history.
         */
        public fun tableName(tableName: String): Builder = apply { this.tableName = tableName }

        /**
         * Set TTL (in seconds) for chat history entries.
         */
        public fun ttlSeconds(ttlSeconds: Long): Builder = apply { this.ttlSeconds = ttlSeconds }

        /**
         * Configure the [SQLChatHistorySchemaMigrator].
         */
        public fun migrator(migrator: SQLChatHistorySchemaMigrator): Builder = apply { this.migrator = migrator }

        /**
         * Set custom [Json] instance for message serialization.
         */
        public fun json(json: Json): Builder = apply { this.json = json }

        /**
         * Create an instance of [PostgresChatHistoryProvider].
         * [database] must be set before calling this method.
         */
        public fun build(): PostgresChatHistoryProvider {
            val db = requireNotNull(database) { "Database is required but was not set" }

            return PostgresChatHistoryProvider(
                db,
                tableName,
                ttlSeconds,
                migrator ?: PostgresChatHistorySchemaMigrator(db, tableName),
                json
            )
        }
    }
}

/**
 * PostgreSQL-specific implementation of [SQLChatHistorySchemaMigrator]
 * for chat history tables.
 *
 * Creates the chat history table with appropriate columns and indexes
 * using PostgreSQL-compatible SQL DDL.
 *
 * @param database The PostgreSQL database connection
 * @param tableName Name of the table to create
 */
public class PostgresChatHistorySchemaMigrator(
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
