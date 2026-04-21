package ai.koog.agents.features.chatmemory.sql

/**
 * Interface for handling database schema migrations for chat history tables.
 *
 * Implementations should create or update the chat history table schema
 * as needed for the target database engine.
 */
public interface SQLChatHistorySchemaMigrator {
    /**
     * Performs a database schema migration asynchronously.
     */
    public suspend fun migrate()
}

/**
 * A no-operation implementation of [SQLChatHistorySchemaMigrator].
 *
 * Use this when schema migrations are managed externally or not required.
 */
public object NoOpSQLChatHistorySchemaMigrator : SQLChatHistorySchemaMigrator {
    override suspend fun migrate() { }
}
