package ai.koog.rag.base.storage

/**
 * Storage interface that provides the ability to delete documents by filter expression.
 *
 */
public interface FilteringDeletionStorage : DeletionStorage {
    /**
     * Deletes documents by filter expression from the storage.
     *
     * @param filterExpression The filter expression to use for deletion.
     * @param namespace An optional namespace to scope the deletion. If null, the default namespace is used.
     * @return The list of identifiers that were successfully deleted.
     */
    public suspend fun delete(
        filterExpression: String,
        namespace: String? = null
    ): List<String> // TODO: it's unsafe, switch to FilterExpressionBuilder in the next PR
}
