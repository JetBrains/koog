package ai.koog.agents.features.chatmemory.sql

import ai.koog.prompt.message.Message
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import kotlin.time.Clock

/**
 * Abstract Exposed-based implementation of [SQLChatHistoryProvider] for managing
 * chat conversation history in SQL databases using JetBrains Exposed ORM.
 *
 * This class provides a generic SQL implementation that works with any database supported
 * by Exposed. It handles the common operations while allowing concrete implementations
 * to provide database-specific configurations.
 *
 * ## Key Features:
 * - Uses Exposed's DSL for type-safe SQL operations
 * - Configurable TTL cleanup with interval throttling
 * - TTL based on updated_at so active conversations don't expire prematurely
 *
 * @param database The Exposed Database instance to use
 * @param tableName Name of the table to store chat history (default: "chat_history")
 * @param ttlSeconds Optional TTL for history entries in seconds (null = no expiration)
 * @param migrator Schema migrator for creating/updating the table
 * @param json JSON serializer instance (default: compact JSON with ignoreUnknownKeys)
 */
public abstract class ExposedChatHistoryProvider @JvmOverloads constructor(
    protected val database: Database,
    tableName: String = "chat_history",
    ttlSeconds: Long? = null,
    migrator: SQLChatHistorySchemaMigrator,
    private val json: Json = defaultChatHistoryJson
) : SQLChatHistoryProvider(
    tableName = tableName,
    ttlSeconds = ttlSeconds,
    migrator = migrator
) {
    /**
     * The Exposed table definition for chat history.
     */
    protected open val chatHistoryTable: ChatHistoryTable = ChatHistoryTable(tableName)

    /**
     * Executes a database transaction with the given operations.
     * Implementations should ensure proper transaction isolation and rollback on failure.
     */
    protected abstract suspend fun <T> transaction(block: suspend () -> T): T

    override suspend fun cleanupExpired() {
        if (ttlSeconds == null) {
            return
        }

        val now = Clock.System.now().toEpochMilliseconds()

        transaction {
            chatHistoryTable.deleteWhere {
                (chatHistoryTable.ttlTimestamp less now) and (chatHistoryTable.ttlTimestamp.isNotNull())
            }
        }
    }

    override suspend fun store(conversationId: String, messages: List<Message>) {
        val messagesJson = json.encodeToString(ListSerializer(Message.serializer()), messages)
        val now = Clock.System.now()
        val nowMillis = now.toEpochMilliseconds()
        val ttlTimestamp = calculateTtlTimestamp(now)

        transaction {
            chatHistoryTable.upsert {
                it[chatHistoryTable.conversationId] = conversationId
                it[chatHistoryTable.messagesJson] = messagesJson
                it[chatHistoryTable.updatedAt] = nowMillis
                it[chatHistoryTable.ttlTimestamp] = ttlTimestamp
            }
        }
    }

    override suspend fun load(conversationId: String): List<Message> {
        val now = Clock.System.now().toEpochMilliseconds()

        return transaction {
            chatHistoryTable
                .select(chatHistoryTable.messagesJson)
                .where {
                    (chatHistoryTable.conversationId eq conversationId) and
                        ((chatHistoryTable.ttlTimestamp eq null) or (chatHistoryTable.ttlTimestamp greaterEq now))
                }
                .firstOrNull()
                ?.let { row ->
                    json.decodeFromString(ListSerializer(Message.serializer()), row[chatHistoryTable.messagesJson])
                } ?: emptyList()
        }
    }

    override suspend fun deleteHistory(conversationId: String) {
        transaction {
            chatHistoryTable.deleteWhere {
                chatHistoryTable.conversationId eq conversationId
            }
        }
    }

    override suspend fun getConversationCount(): Long {
        return transaction {
            chatHistoryTable.selectAll().count()
        }
    }

    @Suppress("MissingKDocForPublicAPI")
    public companion object {
        /**
         * Default JSON configuration for chat history serialization.
         * Uses compact format (no pretty printing) since message lists can be large.
         */
        public val defaultChatHistoryJson: Json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
