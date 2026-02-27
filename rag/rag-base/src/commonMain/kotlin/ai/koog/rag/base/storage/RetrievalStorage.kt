package ai.koog.rag.base.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

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
    public fun rankDocuments(query: String): Flow<SearchResult<Document>>

    /**
     * Searches for documents matching the given request and returns them ranked by relevance.
     *
     * @param request The search request containing the query, result limit, and similarity threshold.
     * @param namespace An optional namespace to scope the search. If null, the default namespace is used.
     * @return A list of search results, each containing a document and its similarity score.
     */
    public suspend fun search(request: SearchRequest, namespace: String? = null): List<SearchResult<Document>>
}

/**
 * Returns the results of [RetrievalStorage.search] as a [Flow] instead of a list.
 *
 * @param request The search request containing the query, result limit, and similarity threshold.
 * @param namespace An optional namespace to scope the search. If null, the default namespace is used.
 * @return A [Flow] emitting search results, each containing a document and its similarity score.
 */
public fun <Document> RetrievalStorage<Document>.searchAsFlow(
    request: SearchRequest,
    namespace: String? = null
): Flow<SearchResult<Document>> = flow {
    search(request, namespace).forEach { emit(it) }
}

/**
 * Retrieves the most relevant documents matching the provided query, ranked by their similarity scores
 * in descending order. Only documents with a similarity score greater than or equal to the specified
 * similarity threshold are included, and the result set is limited to the specified count.
 *
 * Example:
 *  - `mostRelevantDocuments("tigers in the wild nature", 10)` - returns top-10 most relevant documents about tigers in the wild nature
 *  - `mostRelevantDocuments("tigers in the wild nature", similarityThreshold = 0.7)` - returns all documents about tigers in the wild nature that have at least 70% similarity (relevance) score
 *  - `mostRelevantDocuments("tigers in the wild nature", similarityThreshold = 0.7, count = 10)` - returns no more than 10 most relevant documents about tigers in the wild nature that have at least 70% similarity (relevance) score
 *
 * @param query The search query used to find relevant documents.
 * @param count The maximum number of documents to return. Defaults to Int.MAX_VALUE if not specified.
 * @param similarityThreshold The minimum similarity score a document must have to be considered relevant.
 *        Defaults to 0.0 if not specified.
 * @return An iterable collection of the most relevant documents that satisfy the specified filters and rankings.
 */
@Deprecated("Use search instead", ReplaceWith("search(SimilaritySearchRequest(query, count, similarityThreshold))"))
public suspend fun <Document> RetrievalStorage<Document>.mostRelevantDocuments(
    query: String,
    count: Int = Int.MAX_VALUE,
    similarityThreshold: Double = 0.0
): Iterable<Document> = rankDocuments(query)
    .filter { it.similarity >= similarityThreshold }
    .toList()
    .sortedByDescending { it.similarity }
    .take(count)
    .map { it.document }
    .toList()
