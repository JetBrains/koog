package ai.koog.rag.base

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Represents a document accompanied by its associated payload.
 *
 * This data class ties a document of type [Document] to its corresponding payload of type [Payload].
 * It is commonly used in contexts where both the document and its additional metadata or associated data
 * (the payload) need to be stored, retrieved, or manipulated together.
 *
 * @param Document The type of the document being stored or processed.
 * @param Payload The type of the payload associated with the document.
 * @property document The document instance.
 * @property payload The payload associated with the document.
 */
public data class DocumentWithPayload<Document, Payload>(val document: Document, val payload: Payload)

/**
 * Defines a generic interface for document storage systems, supporting storing, retrieving,
 * updating, and deleting documents. Each document can be associated with metadata or additional payload.
 *
 * @param Document The type representing the document being stored.
 * @param Payload The type representing additional metadata or payload associated with the document.
 */
public interface DocumentStorageWithPayload<Document, Payload> {

    /**
     * Stores a document and its associated payload in the storage.
     *
     * @param document The document to be stored.
     * @param data The payload associated with the document.
     * @return A string representing the unique identifier of the stored document.
     */
    public suspend fun store(document: Document, data: Payload): String

    /**
     * Deletes the document with the specified ID from the storage.
     *
     * @param documentId The unique identifier of the document to delete.
     * @return `true` if the document was successfully deleted, `false` otherwise.
     */
    public suspend fun delete(documentId: String): Boolean

    /**
     * Reads a document associated with the given document ID and returns it.
     *
     * @param documentId The unique identifier for the document to be read.
     * @return The document associated with the provided document ID, or null if no document is found.
     */
    public suspend fun read(documentId: String): Document?

    /**
     * Retrieves the payload associated with the document identified by the given document ID.
     *
     * @param documentId The unique identifier of the document whose payload is being retrieved.
     * @return The payload associated with the document, or null if no such document exists or it has no payload.
     */
    public suspend fun getPayload(documentId: String): Payload?

    /**
     * Reads a document along with its associated payload based on the given document ID.
     *
     * @param documentId The unique identifier of the document to be read.
     * @return A pair containing the document and its associated payload if found, or null if no document is associated with the given ID.
     */
    public suspend fun readWithPayload(documentId: String): DocumentWithPayload<Document, Payload>?


    /**
     * Iterates over the documents
     *
     * @return An iterable collection of documents associated with the specified document ID.
     */
    public fun allDocuments(): Flow<Document>

    /**
     * Iterates through documents and their corresponding payloads
     *
     * @return An iterable collection of `DocumentWithPayload`, where each item contains a document and its associated payload.
     */
    public fun allDocumentsWithPayload(): Flow<DocumentWithPayload<Document, Payload>>
}

/**
 * Represents a generic storage system for documents, without the need for additional
 * metadata or payloads. This interface provides basic document storage operations such as storing,
 * retrieving, and deleting documents.
 *
 * @param Document The type of document being stored or processed.
 *
 * By inheriting from `DocumentStorageWithPayload` with a fixed payload type of `Unit`,
 * `DocumentStorage` simplifies the use case for scenarios where no additional payload
 * is required alongside the document.
 */
public interface DocumentStorage<Document> : DocumentStorageWithPayload<Document, Unit> {
    override suspend fun readWithPayload(documentId: String): DocumentWithPayload<Document, Unit>? =
        read(documentId)?.let { DocumentWithPayload(it, Unit) }

    override suspend fun getPayload(documentId: String): Unit = Unit

    override fun allDocumentsWithPayload(): Flow<DocumentWithPayload<Document, Unit>> = flow {
        allDocuments().collect { emit(DocumentWithPayload(it, Unit)) }
    }

    /**
     * Stores a document in the storage system without any additional payload.
     *
     * @param document The document to be stored.
     * @return A string representing the unique identifier of the stored document.
     */
    public suspend fun store(document: Document): String = store(document, Unit)
}