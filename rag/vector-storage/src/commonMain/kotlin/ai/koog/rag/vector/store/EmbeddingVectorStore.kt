package ai.koog.rag.vector.store

import ai.koog.rag.base.storage.SearchRequest
import ai.koog.rag.base.storage.SearchResult
import ai.koog.rag.vector.embedder.DocumentEmbedder
import ai.koog.rag.vector.storage.VectorStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A [VectorStore] implementation that composes a [DocumentEmbedder] with a [VectorStorage].
 *
 * The embedding step is handled by this class, while the actual persistence is delegated
 * to the provided [VectorStorage]. This separation allows swapping backends (in-memory,
 * file-based, real vector database) independently from the embedding model.
 *
 * @param Document The type of the document being stored and ranked.
 * @property embedder A mechanism to generate vector embeddings for documents and queries.
 * @property storage Underlying storage backend to hold documents and their corresponding vector embeddings.
 */
public open class EmbeddingVectorStore<Document>(
    private val embedder: DocumentEmbedder<Document>,
    private val storage: VectorStorage<Document>
) : VectorStore<Document> {

    @Deprecated("Use search instead", ReplaceWith("search(SimilaritySearchRequest(query))"))
    override fun rankDocuments(query: String): Flow<SearchResult<Document>> = flow {
        val queryVector = embedder.embed(query)
        storage.allDocumentsWithPayload().collect { (document, documentVector) ->
            emit(
                SearchResult(
                    document = document,
                    similarity = 1.0 - embedder.diff(queryVector, documentVector)
                )
            )
        }
    }

    override suspend fun search(
        request: SearchRequest,
        namespace: String?
    ): List<SearchResult<Document>> {
        val results = mutableListOf<SearchResult<Document>>()
        val queryVector = embedder.embed(request.query)

        storage.allDocumentsWithPayload().collect { (document, documentVector) ->
            val similarity = 1.0 - embedder.diff(queryVector, documentVector)
            if (similarity >= request.similarityThreshold) {
                results.add(SearchResult(document = document, similarity = similarity))
            }
        }

        return results
            .sortedByDescending { it.similarity }
            .take(request.limit)
    }

    /**
     * Stores the given document after embedding it into a vector representation.
     *
     * @param document The document to be stored.
     * @return A string representing the unique identifier of the stored document.
     */
    public suspend fun store(document: Document): String {
        val vector = embedder.embed(document)
        return storage.store(document, vector)
    }

    override suspend fun add(documents: List<Document>, namespace: String?): List<String> {
        return documents.map { store(it) }
    }

    override suspend fun update(documents: Map<String, Document>, namespace: String?): List<String> {
        return documents.map { (id, document) ->
            storage.delete(id)
            store(document)
        }
    }

    /**
     * Deletes the document with the specified ID from the storage.
     *
     * @param documentId The unique identifier of the document to be deleted.
     * @return true if the document was successfully deleted, false otherwise.
     */
    public suspend fun delete(documentId: String): Boolean {
        return storage.delete(documentId)
    }

    override suspend fun delete(ids: List<String>, namespace: String?): List<String> {
        return ids.filter { storage.delete(it) }
    }

    /**
     * Reads a document by its unique identifier.
     *
     * @param documentId The unique identifier of the document to be read.
     * @return The document associated with the given identifier, or null if no document is found.
     */
    public suspend fun read(documentId: String): Document? {
        return storage.read(documentId)
    }

    override suspend fun read(ids: List<String>, namespace: String?): Map<String, Document> {
        return ids.mapNotNull { id -> storage.read(id)?.let { id to it } }.toMap()
    }

    override fun readAll(namespace: String?): Flow<Document> = storage.allDocuments()

    /**
     * Retrieves a flow of all documents stored in the system.
     *
     * @return A flow emitting each document individually.
     */
    public fun allDocuments(): Flow<Document> = storage.allDocuments()
}
