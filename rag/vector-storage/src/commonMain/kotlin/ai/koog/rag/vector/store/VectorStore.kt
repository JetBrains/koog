package ai.koog.rag.vector.store

import ai.koog.rag.base.storage.DeletionStorage
import ai.koog.rag.base.storage.IngestionStorage
import ai.koog.rag.base.storage.ReadStorage
import ai.koog.rag.base.storage.RetrievalStorage

/**
 * Interface for a vector store that combines document ingestion and retrieval.
 *
 * This is the primary user-facing abstraction for working with vector-based document storage.
 * Implementations handle embedding documents into vectors and storing them for similarity-based retrieval.
 *
 * @param Document The type representing the document being stored.
 */
public interface VectorStore<Document> :
    IngestionStorage<Document>,
    ReadStorage<Document>,
    RetrievalStorage<Document>,
    DeletionStorage
