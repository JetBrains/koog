package ai.koog.rag.base.storage.capability

/**
 * Capabilities that a storage implementation may support.
 */
public enum class StorageCapability {
    NAMESPACE_ISOLATION,
    FILTER_EXPRESSION,
    KEYWORD_SEARCH,
    SIMILARITY_SEARCH,
    HYBRID_SEARCH,
    VECTOR_QUERY
}
