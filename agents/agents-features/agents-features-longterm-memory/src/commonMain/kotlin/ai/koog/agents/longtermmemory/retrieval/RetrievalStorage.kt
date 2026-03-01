package ai.koog.agents.longtermmemory.retrieval

import ai.koog.agents.longtermmemory.model.MemoryRecord
import kotlinx.serialization.Serializable

/**
 * An interface for retrieving (searching) memory records from a storage.
 * An implementation of this interface is responsible for embedding.
 *
 * All methods accept an optional `namespace` parameter that can override the default
 * namespace (table name or collection name) configured in the constructor.
 * When `namespace` is `null`, the implementation uses its default namespace.
 */
public fun interface RetrievalStorage {

    /**
     * Performs a search and returns results with similarity scores.
     *
     * @param request The search request with all parameters
     * @param namespace Optional namespace (table/collection name) to override the default.
     *                  If null, uses the default namespace from constructor.
     * @return List of scored records, sorted by similarity (highest first)
     */
    public suspend fun search(request: SearchRequest, namespace: String?): List<SearchResult>
}

/**
 * Base interface for search requests.
 *
 * @property limit Maximum number of results to return (topK)
 * @property similarityThreshold Minimum similarity score for results (0.0 to 1.0)
 * @property filterExpression Metadata filter expression for pre-filtering
 */
public interface SearchRequest {
    /**
     * Maximum number of results to return (topK)
     */
    public val limit: Int

    /**
     * Minimum similarity score for results (0.0 to 1.0)
     */
    public val similarityThreshold: Double

    /**
     * Metadata filter expression for pre-filtering
     */
    public val filterExpression: String? // TODO: it's unsafe and not portable; add FilterExpressionDsl in the next PR
}

/**
 * Search request for pure vector similarity search using text query.
 * The text will be embedded and used for vector similarity search.
 *
 * @property query Text query to be embedded and searched
 * @property limit Maximum number of results to return (topK)
 * @property similarityThreshold Minimum similarity score for results (0.0 to 1.0)
 * @property filterExpression Metadata filter expression for pre-filtering
 */
@Serializable
public data class SimilaritySearchRequest(
    val query: String,
    override val limit: Int = 10,
    override val similarityThreshold: Double = 0.0,
    override val filterExpression: String? = null
) : SearchRequest

/**
 * Search request for keyword/full-text search.
 * Uses traditional text matching instead of vector similarity.
 *
 * @property query Text query for keyword matching
 * @property limit Maximum number of results to return (topK)
 * @property similarityThreshold Minimum similarity score for results (0.0 to 1.0)
 * @property filterExpression Metadata filter expression for pre-filtering
 */
@Serializable
public data class KeywordSearchRequest(
    val query: String,
    override val limit: Int = 10,
    override val similarityThreshold: Double = 0.0,
    override val filterExpression: String? = null
) : SearchRequest
// TODO: add HybridSearchRequest

/**
 * Represents a result of a [SearchRequest].
 *
 * @property record The actual record data
 * @property similarity The similarity/relevance score (0.0 to 1.0, higher is more relevant)
 */
@Serializable
public data class SearchResult(
    val record: MemoryRecord,
    val similarity: Double
)
