package ai.koog.rag.base.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Deprecated(
    "`RankedDocumentStorage` has been renamed to `RetrievalStorage`",
    replaceWith = ReplaceWith(
        expression = "RetrievalStorage",
        "ai.koog.rag.base.storage.RetrievalStorage"
    )
)
public typealias RankedDocumentStorage<Document> = RetrievalStorage<Document>

/**
 * Represents a specialization of the DocumentStorage interface that handles ranking documents
 * based on their relevance to a given query. The ranking process returns documents along with
 * a similarity score, enabling the filtering and sorting of documents by relevance.
 *
 * @param Document The type of the documents being processed and stored.
 */
public interface RetrievalStorage<Document> {
    /**
     * Ranks documents in the storage based on their relevance to the given query.
     * Each document is assigned a similarity score that represents how closely it matches the query.
     *
     * @param query The query string used to rank the documents.
     * @return A flow emitting ranked documents, where each document is paired with its similarity score.
     */
    @Deprecated("Use search instead", ReplaceWith("search(SimilaritySearchRequest(query))"))
    public fun rankDocuments(query: String): Flow<SearchResult<Document>> = flow { }

    /**
     * Searches for documents matching the given request and returns them ranked by relevance.
     *
     * @param request The search request containing the query, result limit, and other search parameters.
     * @param namespace An optional namespace to scope the search. If null, the default namespace is used.
     * @return A list of search results, each containing a document and its score.
     */
    public suspend fun search(request: SearchRequest, namespace: String? = null): List<SearchResult<Document>>
}

/**
 * Returns the results of [RetrievalStorage.search] as a [Flow] instead of a list.
 *
 * @param request The search request containing the query, result limit, and other search parameters.
 * @param namespace An optional namespace to scope the search. If null, the default namespace is used.
 * @return A [Flow] emitting search results, each containing a document and its score.
 */
public fun <Document> RetrievalStorage<Document>.searchAsFlow(
    request: SearchRequest,
    namespace: String? = null
): Flow<SearchResult<Document>> = flow {
    search(request, namespace).forEach { emit(it) }
}
