package ai.koog.rag.base.storage

/**
 * Storage interface that provides the ability to delete documents by their identifiers.
 *
 */
public interface DeletionStorage {
    /**
     * Deletes documents with the specified identifiers from the storage.
     *
     * @param ids The list of document identifiers to delete.
     * @param namespace An optional namespace to scope the deletion. If null, the default namespace is used.
     * @return The list of identifiers that were successfully deleted.
     */
    public suspend fun delete(ids: List<String>, namespace: String? = null): List<String>
}
