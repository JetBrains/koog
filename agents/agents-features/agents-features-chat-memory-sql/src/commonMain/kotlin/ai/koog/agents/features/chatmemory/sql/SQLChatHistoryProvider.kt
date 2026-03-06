package ai.koog.agents.features.chatmemory.sql

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant

/**
 * Abstract base class for SQL-based implementations of [ChatHistoryProvider].
 *
 * This provider offers a generic SQL abstraction for persisting conversation history
 * to relational databases. Concrete implementations should handle specific SQL
 * dialects and connection management.
 *
 * ## Storage Schema:
 * Implementations should create a table with the following structure:
 * - conversation_id: String (primary key)
 * - messages_json: String (JSON-serialized message list)
 * - updated_at: Long (epoch milliseconds)
 * - ttl_timestamp: Long? (optional expiration timestamp)
 *
 * ## Design Decisions:
 * - One row per conversation (store replaces entire message list as JSON)
 * - TTL is based on updated_at so active conversations don't expire prematurely
 * - Compact JSON (prettyPrint=false) since message lists can be large
 *
 * @param tableName Name of the table to store chat history (default: "chat_history")
 * @param ttlSeconds Optional TTL for history entries in seconds (null = no expiration)
 * @param migrator Schema migrator responsible for creating/updating the table
 */
public abstract class SQLChatHistoryProvider @JvmOverloads constructor(
    protected val tableName: String = "chat_history",
    protected val ttlSeconds: Long? = null,
    protected val migrator: SQLChatHistorySchemaMigrator
) : ChatHistoryProvider {

    /**
     * Initializes the database schema if it doesn't exist.
     * This should be called once during provider initialization.
     */
    public open suspend fun migrate() {
        migrator.migrate()
    }

    /**
     * Cleans up expired history entries based on TTL.
     * This should be called periodically or before operations to maintain database hygiene.
     */
    public abstract suspend fun cleanupExpired()

    /**
     * Calculates the TTL timestamp for a history entry if TTL is configured.
     *
     * @param timestamp The base timestamp (typically the update time)
     * @return The expiration timestamp in epoch milliseconds, or null if TTL is not configured
     */
    public fun calculateTtlTimestamp(timestamp: Instant): Long? {
        return ttlSeconds?.let {
            timestamp.toEpochMilliseconds() + (it * 1000)
        }
    }

    /**
     * Deletes the conversation history for the given conversation ID.
     *
     * @param conversationId Unique identifier for the conversation to delete
     */
    public abstract suspend fun deleteHistory(conversationId: String)

    /**
     * Gets the total number of stored conversations.
     *
     * @return The number of conversation entries in the table
     */
    public abstract suspend fun getConversationCount(): Long
}
