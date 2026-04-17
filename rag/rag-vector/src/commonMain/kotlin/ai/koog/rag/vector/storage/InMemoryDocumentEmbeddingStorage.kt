package ai.koog.rag.vector.storage

import ai.koog.rag.vector.backend.InMemoryVectorStorageBackend
import ai.koog.rag.vector.embedder.DocumentEmbedder

/**
 * An in-memory [VectorStorage] implementation.
 *
 * This class facilitates the storage and retrieval of documents and their corresponding vector embeddings
 * entirely in memory. It utilizes an [InMemoryVectorStorageBackend] for managing the document embeddings and extends
 * [EmbeddingStorage], inheriting capabilities such as ranking, storing, and deleting documents
 * based on their embeddings.
 *
 * @param Document The type of the documents being stored.
 * @param embedder A mechanism responsible for embedding the documents into vector representations.
 */
public open class InMemoryDocumentEmbeddingStorage<Document>(embedder: DocumentEmbedder<Document>) :
    EmbeddingStorage<Document>(
        embedder = embedder,
        storage = InMemoryVectorStorageBackend()
    )
