package ai.koog.agents.features.chatmemory.sql

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for storing chat conversation history.
 *
 * Schema:
 * - Primary key: conversation_id
 * - JSON column for serialized message list
 * - Timestamp for ordering and querying
 * - Optional TTL timestamp for expiration (indexed for efficient cleanup)
 *
 * @param tableName Name of the database table
 */
public open class ChatHistoryTable(tableName: String) : Table(tableName) {

    /**
     * Unique identifier for the conversation. Primary key.
     */
    public val conversationId: Column<String> = varchar("conversation_id", 255)

    /**
     * JSON-serialized list of messages for this conversation.
     */
    public val messagesJson: Column<String> = text("messages_json")

    /**
     * Timestamp of the last update to this conversation, stored as epoch milliseconds.
     * Indexed for efficient ordering and querying.
     */
    public val updatedAt: Column<Long> = long("updated_at").index()

    /**
     * Optional TTL expiration timestamp in epoch milliseconds.
     * When set, the conversation is eligible for cleanup after this time.
     * Indexed for efficient cleanup queries.
     */
    public val ttlTimestamp: Column<Long?> = long("ttl_timestamp").nullable().index()

    override val primaryKey: PrimaryKey = PrimaryKey(conversationId)
}
