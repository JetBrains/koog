package ai.koog.rag.vector.storage

import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.storage.capability.CapabilityAwareStorage
import ai.koog.rag.base.storage.capability.StorageCapability
import ai.koog.rag.base.storage.search.Score
import ai.koog.rag.base.storage.search.ScoreMetric
import ai.koog.rag.base.storage.search.SearchResult
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import ai.koog.rag.vector.backend.VectorStorageBackend
import ai.koog.rag.vector.embedder.DocumentEmbedder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A [VectorStorage] naive implementation that composes a [DocumentEmbedder] with a [VectorStorageBackend].
 *
 * The embedding step is handled by this class, while the actual persistence is delegated
 * to the provided [VectorStorageBackend]. This separation allows swapping backends (in-memory,
 * file-based, real vector database) independently from the embedding model.
 *
 * Subclasses can customize embedding, scoring, or request handling by overriding the
 * protected template methods [embedDocument], [embedQuery], and [score], without
 * rewriting entire storage operations.
 *
 * @param Document The type of the document being stored and ranked.
 * @property embedder A mechanism to generate vector embeddings for documents and queries.
 * @property storage Underlying storage backend to hold documents and their corresponding vector embeddings.
 */
public open class EmbeddingStorage<Document>(
    protected val embedder: DocumentEmbedder<Document>,
    protected val storage: VectorStorageBackend<Document>
) : VectorStorage<Document, SimilaritySearchRequest>, CapabilityAwareStorage {

    open override val capabilities: Set<StorageCapability> = setOf(StorageCapability.SIMILARITY_SEARCH)

    /**
     * Embeds a document into its vector representation.
     * Subclasses can override this to customize how documents are embedded.
     *
     * @param document The document to embed.
     * @return The vector representation of the document.
     */
    protected open suspend fun embedDocument(document: Document): Vector = embedder.embed(document)

    /**
     * Embeds a query text into its vector representation.
     * Subclasses can override this to customize how queries are embedded.
     *
     * @param queryText The query text to embed.
     * @return The vector representation of the query.
     */
    protected open suspend fun embedQuery(queryText: String): Vector = embedder.embed(queryText)

    /**
     * Computes a similarity score between a query vector and a document vector.
     * Subclasses can override this to use a different similarity metric.
     *
     * @param queryVector The vector representation of the query.
     * @param documentVector The vector representation of the document.
     * @return The similarity score.
     */
    protected open fun score(queryVector: Vector, documentVector: Vector): Score =
        Score(1.0 - embedder.diff(queryVector, documentVector), ScoreMetric.COSINE_SIMILARITY)

    /**
     * Validates and converts a [SimilaritySearchRequest] before executing a search.
     * Subclasses can override this to support additional request parameters or apply custom validation.
     *
     * @param request The search request to validate.
     * @return The validated search request.
     */
    protected open fun validateSearchRequest(request: SimilaritySearchRequest): SimilaritySearchRequest {
        requireNoFilterExpression(request)
        return request
    }

    @Deprecated("Use search instead", ReplaceWith("search(SimilaritySearchRequest(query))"))
    open override fun rankDocuments(query: String): Flow<SearchResult<Document>> = flow {
        val queryVector = embedQuery(query)
        storage.allDocumentsWithPayload().collect { (document, documentVector) ->
            emit(SearchResult(document = document, score = score(queryVector, documentVector)))
        }
    }

    /**
     * Retrieves all documents from an underlying VectorStorageBackend and does similarity search in memory.
     */
    protected fun requireNoNamespace(namespace: String?) {
        require(namespace == null) {
            "EmbeddingStorage does not support namespaces, but namespace='$namespace' was provided"
        }
    }

    protected fun requireNoFilterExpression(request: SimilaritySearchRequest) {
        require(request.filterExpression == null) {
            "EmbeddingStorage does not support filter expressions, but filterExpression='${request.filterExpression}' was provided"
        }
    }

    open override suspend fun search(
        request: SimilaritySearchRequest,
        namespace: String?
    ): List<SearchResult<Document>> {
        requireNoNamespace(namespace)
        val validatedRequest = validateSearchRequest(request)
        val queryText = validatedRequest.queryText
        val minScore = validatedRequest.minScore ?: 0.0

        val results = mutableListOf<SearchResult<Document>>()
        val queryVector = embedQuery(queryText)

        storage.allDocumentsWithPayload().collect { (document, documentVector) ->
            val resultScore = score(queryVector, documentVector)
            if (resultScore.value >= minScore) {
                results.add(SearchResult(document = document, score = resultScore))
            }
        }

        return results
            .sortedByDescending { it.score.value }
            .drop(validatedRequest.offset)
            .take(validatedRequest.limit)
    }

    open override suspend fun add(documents: List<Document>, namespace: String?): List<String> {
        requireNoNamespace(namespace)
        return documents.map { doc ->
            val vector = embedDocument(doc)
            storage.store(doc, vector)
        }
    }

    open override suspend fun update(documents: Map<String, Document>, namespace: String?): List<String> {
        requireNoNamespace(namespace)
        return documents.mapNotNull { (id, document) ->
            val vector = embedDocument(document)
            if (storage.store(id, document, vector)) id else null
        }
    }

    open override suspend fun delete(
        ids: List<String>,
        namespace: String?
    ): List<String> {
        requireNoNamespace(namespace)
        return ids.filter { storage.delete(it) }
    }

    open override suspend fun get(ids: List<String>, namespace: String?): List<Document> {
        requireNoNamespace(namespace)
        return ids.mapNotNull { storage.read(it) }
    }

    /**
     * Retrieves a flow of all documents stored in the system.
     *
     * @return A flow emitting each document individually.
     */
    public open fun allDocuments(): Flow<Document> = storage.allDocuments()
}
