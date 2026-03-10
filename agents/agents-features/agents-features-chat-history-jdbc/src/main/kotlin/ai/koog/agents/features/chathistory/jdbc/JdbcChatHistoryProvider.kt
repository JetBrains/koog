package ai.koog.agents.features.chathistory.jdbc

import ai.koog.agents.features.chatmemory.sql.SQLChatHistoryProvider
import ai.koog.agents.features.chatmemory.sql.SQLChatHistorySchemaMigrator
import ai.koog.prompt.message.Message
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.sql.Types
import javax.sql.DataSource
import kotlin.time.Clock

/**
 * Abstract pure JDBC implementation of [SQLChatHistoryProvider] for managing chat conversation
 * history in SQL databases
 *
 * Subclasses provide the database-specific upsert SQL via [upsertSql].
 *
 * @param dataSource The JDBC DataSource for obtaining database connections.
 *   The caller is responsible for managing the DataSource lifecycle
 *   (e.g., closing a connection pool). This provider does not close or otherwise manage the DataSource.
 * @param migrator Schema migrator for creating/updating the table
 * @param tableName Name of the table to store chat history (default: "chat_history")
 * @param ttlSeconds Optional TTL for history entries in seconds (null = no expiration)
 * @param ioDispatcher Coroutine dispatcher for I/O operations (default: [Dispatchers.IO])
 */
public abstract class JdbcChatHistoryProvider @JvmOverloads constructor(
    protected val dataSource: DataSource,
    migrator: SQLChatHistorySchemaMigrator,
    ttlSeconds: Long? = null,
    tableName: String = "chat_history",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SQLChatHistoryProvider(
    tableName = tableName,
    ttlSeconds = ttlSeconds,
    migrator = migrator
) {
    protected open fun serializeMessages(messages: List<Message>): String =
        defaultJson.encodeToString(ListSerializer(Message.serializer()), messages)

    protected open fun deserializeMessages(json: String): List<Message> =
        defaultJson.decodeFromString(ListSerializer(Message.serializer()), json)

    /**
     * Database-specific SQL for upsert operations.
     * The SQL must accept 4 positional parameters: conversation_id, messages_json, updated_at, ttl_timestamp.
     */
    protected abstract val upsertSql: String

    /**
     * SQL for loading messages by conversation ID, filtering out expired entries.
     * Accepts 2 positional parameters: conversation_id, current_timestamp.
     */
    protected open val loadSql: String
        get() = """
            SELECT messages_json FROM $tableName
            WHERE conversation_id = ? AND (ttl_timestamp IS NULL OR ttl_timestamp >= ?)
        """.trimIndent()

    /**
     * SQL for deleting expired entries.
     * Accepts 1 positional parameter: current_timestamp.
     */
    protected open val cleanupExpiredSql: String
        get() = "DELETE FROM $tableName WHERE ttl_timestamp IS NOT NULL AND ttl_timestamp < ?"

    /**
     * SQL for deleting a specific conversation's history.
     * Accepts 1 positional parameter: conversation_id.
     */
    protected open val deleteHistorySql: String
        get() = "DELETE FROM $tableName WHERE conversation_id = ?"

    /**
     * SQL for counting non-expired conversations.
     * Accepts 1 positional parameter: current_timestamp.
     */
    protected open val getConversationCountSql: String
        get() = "SELECT COUNT(*) FROM $tableName WHERE ttl_timestamp IS NULL OR ttl_timestamp >= ?"

    override suspend fun store(conversationId: String, messages: List<Message>) {
        val messagesJson = serializeMessages(messages)
        val now = Clock.System.now()
        val nowMillis = now.toEpochMilliseconds()
        val ttlTimestamp = calculateTtlTimestamp(now)

        withContext(ioDispatcher) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(upsertSql).use { stmt ->
                    stmt.setString(1, conversationId)
                    stmt.setString(2, messagesJson)
                    stmt.setLong(3, nowMillis)
                    if (ttlTimestamp != null) {
                        stmt.setLong(4, ttlTimestamp)
                    } else {
                        stmt.setNull(4, Types.BIGINT)
                    }
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun load(conversationId: String): List<Message> {
        val now = Clock.System.now().toEpochMilliseconds()

        return withContext(ioDispatcher) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(loadSql).use { stmt ->
                    stmt.setString(1, conversationId)
                    stmt.setLong(2, now)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            deserializeMessages(rs.getString("messages_json"))
                        } else {
                            emptyList()
                        }
                    }
                }
            }
        }
    }

    override suspend fun cleanupExpired() {
        if (ttlSeconds == null) return

        val now = Clock.System.now().toEpochMilliseconds()

        withContext(ioDispatcher) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(cleanupExpiredSql).use { stmt ->
                    stmt.setLong(1, now)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun deleteHistory(conversationId: String) {
        withContext(ioDispatcher) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(deleteHistorySql).use { stmt ->
                    stmt.setString(1, conversationId)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun getConversationCount(): Long {
        val now = Clock.System.now().toEpochMilliseconds()

        return withContext(ioDispatcher) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(getConversationCountSql).use { stmt ->
                    stmt.setLong(1, now)
                    stmt.executeQuery().use { rs ->
                        rs.next()
                        rs.getLong(1)
                    }
                }
            }
        }
    }

    @Suppress("MissingKDocForPublicAPI")
    public companion object {
        /**
         * Default JSON configuration for chat history serialization.
         * Uses compact format (no pretty printing) since message lists can be large.
         */
        public val defaultJson: Json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
