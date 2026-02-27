package ai.koog.rag.base.storage

/**
 * A [SearchRequest] implementation for similarity-based document search.
 *
 * @property query The search query used to find relevant documents.
 * @property limit Maximum number of results to return. Defaults to 10.
 * @property similarityThreshold Minimum similarity score for results (0.0 to 1.0). Defaults to 0.0.
 */
public data class SimilaritySearchRequest(
    override val query: String,
    override val limit: Int = 10,
    override val similarityThreshold: Double = 0.0,
) : SearchRequest
