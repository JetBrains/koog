package ai.koog.rag.base.storage.search

/**
 * Base interface for all search requests against a storage backend.
 *
 * @property limit maximum number of results to return
 * @property offset number of results to skip for pagination
 */
public interface SearchRequest {
    /**
     * maximum number of results to return
     */
    public val limit: Int

    /**
     * number of results to skip for pagination
     */
    public val offset: Int
}

/**
 * Mixin interface indicating that a search request contains a text query.
 *
 * @property queryText the text query string to search for
 */
public interface HasTextQuery {
    /**
     * the text query string to search for
     */
    public val queryText: String
}

/**
 * Mixin interface indicating that a search request supports metadata filtering.
 *
 * @property filter optional filter expression to narrow down search results based on metadata
 */
public interface HasFilterExpression {
    /**
     * optional filter expression to narrow down search results based on metadata
     */
    public val filterExpression: String? // TODO: it's unsafe, switch to FilterExpressionBuilder in the next PR
}

/**
 * Mixin interface indicating that a search request supports a minimum score threshold.
 *
 * @property minScore optional minimum relevance score; results below this threshold are excluded
 */
public interface HasScoreThreshold {
    /**
     * optional minimum relevance score; results below this threshold are excluded
     */
    public val minScore: Double?
}

/**
 * A search request that performs similarity (vector) search using the provided query text.
 *
 * The query text is typically converted into an embedding vector by the storage backend
 * and compared against stored document vectors.
 *
 * @property queryText the text query to find similar documents for
 * @property limit maximum number of results to return (default: 10)
 * @property offset number of results to skip for pagination (default: 0)
 * @property minScore optional minimum similarity score threshold
 * @property filterExpression optional filter expression to narrow down results
 */
public data class SimilaritySearchRequest(
    override val queryText: String,
    override val limit: Int = 10,
    override val offset: Int = 0,
    override val minScore: Double? = null,
    override val filterExpression: String? = null,
) : SearchRequest, HasTextQuery, HasScoreThreshold, HasFilterExpression

/**
 * A search request that performs keyword-based (lexical) search using the provided query text.
 *
 * @property queryText the text query to match against document content
 * @property limit maximum number of results to return (default: 10)
 * @property offset number of results to skip for pagination (default: 0)
 * @property minScore optional minimum relevance score threshold
 * @property filterExpression optional filter expression to narrow down results
 */
public data class KeywordSearchRequest(
    override val queryText: String,
    override val limit: Int = 10,
    override val offset: Int = 0,
    override val minScore: Double? = null,
    override val filterExpression: String? = null,
) : SearchRequest, HasTextQuery, HasScoreThreshold, HasFilterExpression

/**
 * A search request that combines similarity (vector) and keyword (lexical) search strategies.
 *
 * The [alpha] parameter controls the balance between the two strategies:
 * - `0.0` uses vector search only
 * - `1.0` uses keyword search only
 * - `0.5` (default) gives equal weight to both strategies
 *
 * @property queryText the text query used for both vector and keyword search
 * @property alpha blending factor between vector and keyword search, must be in `[0.0, 1.0]`
 * @property limit maximum number of results to return (default: 10)
 * @property offset number of results to skip for pagination (default: 0)
 * @property minScore optional minimum relevance score threshold
 * @property filterExpression optional filter expression to narrow down results
 * @throws IllegalArgumentException if [alpha] is not in `[0.0, 1.0]`
 */
public data class HybridSearchRequest(
    override val queryText: String,
    val alpha: Double = 0.5,
    override val limit: Int = 10,
    override val offset: Int = 0,
    override val minScore: Double? = null,
    override val filterExpression: String? = null,
) : SearchRequest, HasTextQuery, HasScoreThreshold, HasFilterExpression {
    init {
        require(alpha in 0.0..1.0) { "alpha must be in [0.0, 1.0]" }
    }
}
