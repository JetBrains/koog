package ai.koog.rag.vector.storage

import ai.koog.rag.base.storage.DeletionStorage
import ai.koog.rag.base.storage.LookupStorage
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.WriteStorage
import ai.koog.rag.base.storage.search.SearchRequest

/**
 * Interface for a vector storage that combines document ingestion and retrieval.
 *
 * This is the primary user-facing abstraction for working with vector-based document storage.
 * Implementations handle embedding documents into vectors and storing them for similarity-based retrieval.
 *
 * @param Document The type representing the document being stored.
 * @param Request The type of search requests accepted by this storage.
 */
public interface VectorStorage<Document, in Request : SearchRequest> :
    WriteStorage<Document>,
    LookupStorage<Document>,
    SearchStorage<Document, Request>,
    DeletionStorage
