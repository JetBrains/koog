package ai.koog.rag.vector.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * Represents a record that can be stored in a vector database.
 *
 * @property id Unique identifier for the record (optional for new records, required for updates)
 * @property content The main textual content to be embedded and searched
 * @property metadata Flexible key-value metadata for filtering and custom fields
 */
public sealed interface Record {
    /**
     * Unique identifier for the record (optional for new records, required for updates)
     */
    public val id: String?

    /**
     * The main textual content to be embedded and searched
     */
    public val content: String

    /**
     * Flexible key-value metadata for filtering and custom fields
     */
    public val metadata: Map<String, JsonPrimitive>

    /**
     * Represents a record without embeddings.
     */
    @Serializable
    public data class Plain(
        override val id: String? = null,
        override val content: String,
        override val metadata: Map<String, JsonPrimitive> = emptyMap()
    ) : Record

    /**
     * Represents a record that can be stored in a vector database.
     *
     * @property id Unique identifier for the record (optional for new records, required for updates)
     * @property content The main textual content to be embedded and searched
     * @property metadata Flexible key-value metadata for filtering and custom fields
     * @property embedding Pre-computed embedding vector (optional)
     */
    @Serializable
    public data class Embedded(
        override val id: String? = null,
        override val content: String,
        override val metadata: Map<String, JsonPrimitive> = emptyMap(),
        val embedding: List<Float>
    ) : Record
}

/**
 * Base sealed interface for search requests.
 * Using a sealed interface eliminates invalid states and allows search-type-specific parameters.
 *
 * @property limit Maximum number of results to return (topK)
 * @property similarityThreshold Minimum similarity score for results (0.0 to 1.0)
 * @property filterExpression Metadata filter expression for pre-filtering (built using [filterExpression] DSL)
 */
public sealed interface SearchRequest {
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
    public val filterExpression: Expression?
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
    override val filterExpression: Expression? = null
) : SearchRequest

/**
 * Search request for direct vector similarity search using pre-computed embedding.
 *
 * @property queryVector Pre-computed query embedding vector
 * @property limit Maximum number of results to return (topK)
 * @property similarityThreshold Minimum similarity score for results (0.0 to 1.0)
 * @property filterExpression Metadata filter expression for pre-filtering
 */
@Serializable
public data class VectorSearchRequest(
    val queryVector: List<Float>,
    override val limit: Int = 10,
    override val similarityThreshold: Double = 0.0,
    override val filterExpression: Expression? = null
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
    override val filterExpression: Expression? = null
) : SearchRequest

/**
 * Search request for hybrid search combining vector similarity and keyword search.
 * Allows balancing between semantic (vector) and lexical (keyword) matching.
 *
 * @property query Text query for both embedding and keyword matching
 * @property queryVector Optional pre-computed query vector (will be computed from query if not provided)
 * @property alpha Balance between vector (0.0) and keyword (1.0) search. Default 0.5 for equal weight.
 * @property limit Maximum number of results to return (topK)
 * @property similarityThreshold Minimum similarity score for results (0.0 to 1.0)
 * @property filterExpression Metadata filter expression for pre-filtering
 */
@Serializable
public data class HybridSearchRequest(
    val query: String,
    val queryVector: List<Float>? = null,
    val alpha: Double = 0.5,
    override val limit: Int = 10,
    override val similarityThreshold: Double = 0.0,
    override val filterExpression: Expression? = null
) : SearchRequest {
    init {
        require(alpha in 0.0..1.0) {
            "Alpha must be between 0.0 and 1.0, got $alpha"
        }
    }
}

/**
 * Represents a result of a [SearchRequest].
 *
 * @property record The actual record data
 * @property similarity The similarity/relevance score (0.0 to 1.0, higher is more relevant)
 */
@Serializable
public data class SearchResult(
    val record: Record,
    val similarity: Double
)

/**
 * Result of a batch operation, providing detailed success/failure information.
 *
 * @property successIds IDs of records that were successfully processed
 * @property failedIds Map of failed record IDs to their error messages
 */
@Serializable
public data class BatchOperationResult(
    val successIds: List<String>,
    val failedIds: Map<String, String> = emptyMap()
) {
    val isFullySuccessful: Boolean get() = failedIds.isEmpty()
    val totalProcessed: Int get() = successIds.size + failedIds.size
}

/**
 * An interface for vector database storage.
 *
 * This interface is designed to:
 * - Support reactive/coroutine-based operations
 * - Return similarity scores with results
 * - Support streaming via Flow
 * - Provide comprehensive CRUD operations
 * - Support direct vector search
 * - Support multiple search types
 * - Support namespace isolation (table/collection name override)
 *
 * All methods accept an optional `namespace` parameter that can override the default
 * namespace (table name or collection name) configured in the constructor.
 * When `namespace` is `null`, the implementation uses its default namespace.
 */
public interface VectorDatabase {

    // ==================== CREATE/UPDATE OPERATIONS ====================

    /**
     * Adds records to the store.
     * If a record with the same ID exists, behavior depends on implementation (insert or upsert).
     *
     * @param records The records to add
     * @param namespace Optional namespace (table/collection name) to override the default.
     *                  If null, uses the default namespace from constructor.
     * @return Result containing successful and failed record IDs
     */
    public suspend fun add(records: List<Record>, namespace: String? = null): BatchOperationResult

    /**
     * Updates existing records in the store.
     * Records must have non-null IDs.
     *
     * @param records The records to update
     * @param namespace Optional namespace (table/collection name) to override the default.
     *                  If null, uses the default namespace from constructor.
     * @return Result containing successful and failed record IDs
     */
    public suspend fun update(records: List<Record>, namespace: String? = null): BatchOperationResult

    // ==================== READ OPERATIONS ====================

    /**
     * Retrieves multiple records by their IDs.
     *
     * @param ids The record IDs
     * @param namespace Optional namespace (table/collection name) to override the default.
     *                  If null, uses the default namespace from constructor.
     * @return List of found records
     */
    public suspend fun getAll(ids: List<String>, namespace: String? = null): List<Record>

    // ==================== SEARCH OPERATIONS ====================

    /**
     * Performs a search and returns results with similarity scores.
     *
     * @param request The search request with all parameters
     * @param namespace Optional namespace (table/collection name) to override the default.
     *                  If null, uses the default namespace from constructor.
     * @return List of scored records, sorted by similarity (highest first)
     */
    public suspend fun search(request: SearchRequest, namespace: String? = null): List<SearchResult>

    /**
     * Streams search results for large result sets.
     *
     * Default implementation wraps the search() method result as a Flow.
     * Override for true streaming implementations.
     *
     * @param request The search request
     * @param namespace Optional namespace (table/collection name) to override the default.
     *                  If null, uses the default namespace from constructor.
     * @return Flow of scored records
     */
    public fun searchAsFlow(request: SearchRequest, namespace: String? = null): Flow<SearchResult> =
        flow {
            search(request, namespace).forEach { emit(it) }
        }

    // ==================== DELETE OPERATIONS ====================

    /**
     * Deletes multiple records by their IDs.
     *
     * @param ids The record IDs to delete
     * @param namespace Optional namespace (table/collection name) to override the default.
     *                  If null, uses the default namespace from constructor.
     * @return Result containing successful and failed deletions
     */
    public suspend fun deleteAll(ids: List<String>, namespace: String? = null): BatchOperationResult

    /**
     * Deletes records matching a filter expression.
     *
     * @param filterExpression The filter expression
     * @param namespace Optional namespace (table/collection name) to override the default.
     *                  If null, uses the default namespace from constructor.
     * @return Number of records deleted
     */
    public suspend fun deleteByFilter(filterExpression: Expression, namespace: String? = null): Int
}

/**
 * Exception thrown when a vector database operation fails.
 */
public class VectorDatabaseException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
