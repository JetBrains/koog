package ai.koog.rag.base.storage

/**
 * Storage interface that provides the ability to look up documents by their identifiers.
 *
 * @param Document The type of the documents being read.
 */
public interface LookupStorage<Document> {
    /**
     * Gets documents by their identifiers.
     *
     * @param ids The list of document identifiers to get.
     * @param namespace An optional namespace to scope. If null, the default namespace is used.
     * @return The list of documents matching the given identifiers.
     */
    public suspend fun get(ids: List<String>, namespace: String? = null): List<Document>
}
