package ai.koog.spring.ai.vectorstore

import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.FilteringDeletionStorage
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.WriteStorage
import ai.koog.rag.base.storage.search.SearchRequest

/**
 * A unified storage interface that combines [WriteStorage], [SearchStorage], and [FilteringDeletionStorage]
 * for use with Spring AI vector stores.
 *
 * Users can inject this single interface as a Spring Bean to access all storage
 * capabilities (ingestion, retrieval, deletion) through one dependency.
 */
public interface KoogVectorStore :
    WriteStorage<TextDocument>,
    SearchStorage<TextDocument, SearchRequest>,
    FilteringDeletionStorage
