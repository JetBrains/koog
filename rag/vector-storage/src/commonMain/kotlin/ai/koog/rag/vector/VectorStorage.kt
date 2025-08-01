package ai.koog.rag.vector

import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.DocumentStorageWithPayload
import ai.koog.rag.base.RankedDocument
import ai.koog.rag.base.RankedDocumentStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Interface for managing storage and retrieval of documents along with their vector embeddings.
 * This interface extends the [DocumentStorageWithPayload] interface and specializes it by using
 * vectors (embeddings) as the associated payload for documents.
 *
 * @param Document The type representing the document being stored.
 */
public interface VectorStorage<Document> : DocumentStorageWithPayload<Document, Vector>

/**
 * A document storage implementation that utilizes embeddings for document ranking and retrieval.
 * This class combines a document embedder for generating vector representations and a storage mechanism
 * capable of associating documents with their respective embeddings. It provides ranking capabilities
 * by measuring similarity between query embeddings and stored document embeddings.
 *
 * @param Document The type of the document being stored and ranked.
 * @property embedder A mechanism to generate vector embeddings for documents and queries.
 * @property storage Underlying storage system to hold documents and their corresponding vector embeddings.
 */
public open class EmbeddingBasedDocumentStorage<Document>(
    private val embedder: DocumentEmbedder<Document>,
    private val storage: VectorStorage<Document>
) : RankedDocumentStorage<Document> {
    /**
     * Ranks documents based on their similarity to a given query string.
     *
     * This method computes a similarity score for each document relative to the provided query
     * by embedding both the query and the stored document, and then comparing their vector representations.
     * The result is a flow of ranked documents, each associated with a similarity score.
     *
     * @param query The query string used to rank the documents.
     * @return A flow of `RankedDocument` objects, where each object contains a document
     * and its associated similarity score.
     */
    override fun rankDocuments(query: String): Flow<RankedDocument<Document>> = flow {
        val queryVector = embedder.embed(query)
        storage.allDocumentsWithPayload().collect { (document, documentVector) ->
            emit(
                RankedDocument(
                    document = document,
                    similarity = 1.0 - embedder.diff(queryVector, documentVector)
                )
            )
        }
    }

    /**
     * Stores the given document after embedding it into a vector representation.
     *
     * @param document The document to be stored.
     * @param data A placeholder parameter, not used in the current implementation.
     * @return A string representing the unique identifier of the stored document.
     */
    override suspend fun store(document: Document, data: Unit): String {
        val vector = embedder.embed(document)
        return storage.store(document, vector)
    }

    /**
     * Deletes the document with the specified ID from the storage.
     *
     * @param documentId The unique identifier of the document to be deleted.
     * @return true if the document was successfully deleted, false otherwise.
     */
    override suspend fun delete(documentId: String): Boolean {
        return storage.delete(documentId)
    }

    /**
     * Reads a document by its unique identifier.
     *
     * @param documentId The unique identifier of the document to be read.
     * @return The document associated with the given identifier, or null if no document is found.
     */
    override suspend fun read(documentId: String): Document? {
        return storage.readWithPayload(documentId)?.let { (document, _) -> document }
    }

    /**
     * Retrieves a flow of all documents stored in the system.
     *
     * @return A flow emitting each document individually.
     */
    override fun allDocuments(): Flow<Document> = flow {
        storage.allDocumentsWithPayload().collect {
            emit(it.document)
        }
    }
}
