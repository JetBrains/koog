package ai.koog.rag.base.storage

/**
 * Base interface for search requests.
 * It's intentionally minimal and not sealed so it's possible to create implementations for keyword or hybrid search.
 *
 * @property query The search query used to find relevant documents.
 * @property limit Maximum number of results to return (topK)
 * @property similarityThreshold Minimum similarity score for results (0.0 to 1.0)
 */
public interface SearchRequest {
    /**
     * The search query used to find relevant documents.
     */
    public val query: String

    /**
     * Maximum number of results to return (topK)
     */
    public val limit: Int

    /**
     * Minimum similarity score for results (0.0 to 1.0)
     */
    public val similarityThreshold: Double
}
