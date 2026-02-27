package ai.koog.rag.base.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

public interface RetrievalStorage<Document> {
    public suspend fun search(request: SearchRequest, namespace: String? = null): List<SearchResult<Document>>
}

public fun <Document> RetrievalStorage<Document>.searchAsFlow(
    request: SearchRequest,
    namespace: String? = null
): Flow<SearchResult<Document>> = flow {
    search(request, namespace).forEach { emit(it) }
}

/**
 * Base interface for search requests.
 * It's intentionally minimal and not sealed so it's possible to create implementations for keyword or hybrid search.
 *
 * @property limit Maximum number of results to return (topK)
 * @property similarityThreshold Minimum similarity score for results (0.0 to 1.0)
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
}

public data class SearchResult<Document>(
    val document: Document,
    val similarity: Double
)
