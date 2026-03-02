package ai.koog.agents.features.sql.providers.chathistory

import ai.koog.agents.features.sql.providers.SQLPersistenceSchemaMigrator

/**
 * Schema migrator specific to chat history tables.
 *
 * This interface narrows [SQLPersistenceSchemaMigrator] to chat history concerns,
 * making it clear at the type level whether a migrator is intended for
 * chat history or for persistence/snapshot storage.
 */
public interface SQLChatHistorySchemaMigrator : SQLPersistenceSchemaMigrator
