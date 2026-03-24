package ai.koog.rag.vector.backend

import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.DocumentWithPayload
import kotlinx.coroutines.flow.Flow

/**
 * Low-level storage interface for documents and their pre-computed vector embeddings.
 *
 * Implementations handle only persistence — no embedding logic. The embedding step
 * is handled by [ai.koog.rag.vector.storage.EmbeddingStorage] which composes an embedder with a [VectorStorageBackend].
 *
 * @param Document The type of the document being stored.
 */
public interface VectorStorageBackend<Document> {
    /**
     * Stores a document along with its pre-computed vector embedding.
     *
     * @param document The document to store.
     * @param vector The pre-computed vector embedding for the document.
     * @return A unique string identifier for the stored document.
     */
    public suspend fun store(document: Document, vector: Vector): String

    /**
     * Updates a document along with its pre-computed vector embedding under the specified ID.
     * Only updates if a document with the given ID already exists.
     *
     * @param id The unique identifier of the document to update.
     * @param document The updated document.
     * @param vector The pre-computed vector embedding for the document.
     * @return `true` if the document was successfully updated, `false` if no document with the given [id] exists.
     */
    public suspend fun store(id: String, document: Document, vector: Vector): Boolean

    /**
     * Deletes the document with the specified ID.
     *
     * @param documentId The unique identifier of the document to delete.
     * @return `true` if the document was successfully deleted, `false` otherwise.
     */
    public suspend fun delete(documentId: String): Boolean

    /**
     * Reads a document by its unique identifier.
     *
     * @param documentId The unique identifier of the document to read.
     * @return The document, or `null` if not found.
     */
    public suspend fun read(documentId: String): Document?

    /**
     * Reads a document along with its vector embedding by document ID.
     *
     * @param documentId The unique identifier of the document to read.
     * @return A [DocumentWithPayload] containing the document and its vector, or `null` if not found.
     */
    public suspend fun readWithPayload(documentId: String): DocumentWithPayload<Document, Vector>?

    /**
     * Retrieves a flow of all documents with their vector embeddings.
     *
     * @return A flow of [DocumentWithPayload] items.
     */
    public fun allDocumentsWithPayload(): Flow<DocumentWithPayload<Document, Vector>>

    /**
     * Retrieves a flow of all documents.
     *
     * @return A flow of documents.
     */
    public fun allDocuments(): Flow<Document>
}
