package ai.koog.agents.longtermmemory.ingestion

import ai.koog.agents.longtermmemory.model.MemoryRecord

/**
 * An interface for ingesting (adding) memory records into a store.
 *
 * All methods accept an optional `namespace` parameter that can override the default
 * namespace (table name or collection name) configured in the constructor.
 * When `namespace` is `null`, the implementation uses its default namespace.
 */
public fun interface IngestionStorage {

    /**
     * Adds memory records to the store.
     * If a record with the same ID exists, behavior depends on implementation (insert or upsert).
     *
     * @param records The records to add
     * @param namespace Optional namespace (table/collection name) to override the default.
     *                  If null, uses the default namespace from constructor.
     */
    public suspend fun add(records: List<MemoryRecord>, namespace: String?)
}
