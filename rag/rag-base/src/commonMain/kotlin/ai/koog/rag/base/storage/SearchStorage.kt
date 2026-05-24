package ai.koog.rag.base.storage

import ai.koog.rag.base.storage.search.SearchRequest
import ai.koog.rag.base.storage.search.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Represents a specialization of the DocumentStorage interface that handles ranking documents
 * based on their relevance to a given query. The ranking process returns documents along with
 * a similarity score, enabling the filtering and sorting of documents by relevance.
 *
 * @param Document The type of the documents being processed and stored.
 * @param Request The type of search requests accepted by this storage.
 */
public interface SearchStorage<Document, in Request : SearchRequest> {
    /**
     * Searches for documents matching the given request and returns them ranked by relevance.
     *
     * @param request The search request containing the query, result limit, and other search parameters.
     * @param namespace An optional namespace to scope the search. If null, the default namespace is used.
     * @return A list of search results, each containing a document and its score.
     */
    public suspend fun search(request: Request, namespace: String? = null): List<SearchResult<Document>>
}

/**
 * Returns the results of [SearchStorage.search] as a [Flow] instead of a list.
 *
 * @param request The search request containing the query, result limit, and other search parameters.
 * @param namespace An optional namespace to scope the search. If null, the default namespace is used.
 * @return A [Flow] emitting search results, each containing a document and its score.
 */
public fun <Document, Request : SearchRequest> SearchStorage<Document, Request>.searchAsFlow(
    request: Request,
    namespace: String? = null
): Flow<SearchResult<Document>> = flow {
    search(request, namespace).forEach { emit(it) }
}
