package ai.koog.rag.base.storage

import kotlinx.coroutines.flow.Flow

/**
 * Storage interface that provides the ability to read documents by their identifiers or retrieve all documents.
 *
 * @param Document The type of the documents being stored.
 * @param ID The type of the document identifier.
 */
public interface ReadStorage<Document, ID> {
    /**
     * Reads documents with the specified identifiers from the storage.
     *
     * @param ids The list of document identifiers to read.
     * @param namespace An optional namespace to scope the read operation. If null, the default namespace is used.
     * @return A map of document identifiers to their corresponding documents.
     */
    public suspend fun read(ids: List<ID>, namespace: String? = null): Map<ID, Document>

    /**
     * Returns a flow of all documents in the storage.
     *
     * @param namespace An optional namespace to scope the read operation. If null, the default namespace is used.
     * @return A [Flow] emitting all documents in the storage.
     */
    public fun readAll(namespace: String? = null): Flow<Document>
}
