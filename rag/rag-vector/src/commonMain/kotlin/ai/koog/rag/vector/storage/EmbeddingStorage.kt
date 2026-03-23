package ai.koog.rag.vector.storage

import ai.koog.rag.base.storage.capability.CapabilityAwareStorage
import ai.koog.rag.base.storage.capability.StorageCapability
import ai.koog.rag.base.storage.search.Score
import ai.koog.rag.base.storage.search.ScoreMetric
import ai.koog.rag.base.storage.search.SearchRequest
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
 * @param Document The type of the document being stored and ranked.
 * @property embedder A mechanism to generate vector embeddings for documents and queries.
 * @property storage Underlying storage backend to hold documents and their corresponding vector embeddings.
 */
public open class EmbeddingStorage<Document>(
    private val embedder: DocumentEmbedder<Document>,
    private val storage: VectorStorageBackend<Document>
) : VectorStorage<Document>, CapabilityAwareStorage {

    override val capabilities: Set<StorageCapability> = setOf(StorageCapability.SIMILARITY_SEARCH)

    @Deprecated("Use search instead", ReplaceWith("search(SimilaritySearchRequest(query))"))
    override fun rankDocuments(query: String): Flow<SearchResult<Document>> = flow {
        val queryVector = embedder.embed(query)
        storage.allDocumentsWithPayload().collect { (document, documentVector) ->
            emit(
                SearchResult(
                    document = document,
                    score = Score(1.0 - embedder.diff(queryVector, documentVector), ScoreMetric.COSINE_SIMILARITY),
                )
            )
        }
    }

    /**
     * Retrieves all documents from an underlying VectorStorageBackend and does similarity search in memory.
     */
    override suspend fun search(
        request: SearchRequest,
        namespace: String?
    ): List<SearchResult<Document>> {
        require(request is SimilaritySearchRequest) {
            "EmbeddingStorage requires a SimilaritySearchRequest"
        }
        val queryText = request.queryText
        val minScore = request.minScore ?: 0.0

        val results = mutableListOf<SearchResult<Document>>()
        val queryVector = embedder.embed(queryText)

        storage.allDocumentsWithPayload().collect { (document, documentVector) ->
            val similarity = 1.0 - embedder.diff(queryVector, documentVector)
            if (similarity >= minScore) {
                results.add(
                    SearchResult(
                        document = document,
                        score = Score(value = similarity, metric = ScoreMetric.COSINE_SIMILARITY)
                    )
                )
            }
        }

        return results
            .sortedByDescending { it.score.value }
            .drop(request.offset)
            .take(request.limit)
    }

    override suspend fun add(documents: List<Document>, namespace: String?): List<String> {
        return documents.map { doc ->
            val vector = embedder.embed(doc)
            storage.store(doc, vector)
        }
    }

    override suspend fun update(documents: Map<String, Document>, namespace: String?): List<String> {
        return documents.mapNotNull { (id, document) ->
            val vector = embedder.embed(document)
            if (storage.store(id, document, vector)) id else null
        }
    }

    override suspend fun delete(
        ids: List<String>,
        namespace: String?
    ): List<String> {
        return ids.filter { storage.delete(it) }
    }

    override suspend fun get(ids: List<String>, namespace: String?): List<Document> {
        return ids.mapNotNull { storage.read(it) }
    }

    /**
     * Retrieves a flow of all documents stored in the system.
     *
     * @return A flow emitting each document individually.
     */
    public fun allDocuments(): Flow<Document> = storage.allDocuments()
}
