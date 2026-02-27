package ai.koog.rag.vector.store

import ai.koog.rag.vector.embedder.DocumentEmbedder
import ai.koog.rag.vector.storage.InMemoryVectorStorage

/**
 * An in-memory [VectorStore] implementation.
 *
 * This class facilitates the storage and retrieval of documents and their corresponding vector embeddings
 * entirely in memory. It utilizes an [InMemoryVectorStorage] for managing the document embeddings and extends
 * [EmbeddingVectorStore], inheriting capabilities such as ranking, storing, and deleting documents
 * based on their embeddings.
 *
 * @param Document The type of the documents being stored.
 * @param embedder A mechanism responsible for embedding the documents into vector representations.
 */
public open class InMemoryDocumentEmbeddingStore<Document>(embedder: DocumentEmbedder<Document>) :
    EmbeddingVectorStore<Document>(
        embedder = embedder,
        storage = InMemoryVectorStorage()
    )
