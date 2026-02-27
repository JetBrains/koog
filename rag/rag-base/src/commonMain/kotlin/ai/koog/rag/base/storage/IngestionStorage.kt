package ai.koog.rag.base.storage

/**
 * Storage interface that provides the ability to ingest (add and update) documents.
 *
 * @param Document The type of the documents being stored.
 * @param ID The type of the document identifier.
 */
public interface IngestionStorage<Document, ID> {
    /**
     * Adds new documents to the storage.
     *
     * @param documents The list of documents to add.
     * @param namespace An optional namespace to scope the storage. If null, the default namespace is used.
     * @return The list of identifiers assigned to the newly added documents.
     */
    public suspend fun add(documents: List<Document>, namespace: String? = null): List<ID>

    /**
     * Updates existing documents in the storage.
     *
     * @param documents A map of document identifiers to their updated document content.
     * @param namespace An optional namespace to scope the storage. If null, the default namespace is used.
     * @return The list of identifiers of the successfully updated documents.
     */
    public suspend fun update(documents: Map<ID, Document>, namespace: String? = null): List<ID>
}
