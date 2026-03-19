package ai.koog.spring.ai.vectorstore

import ai.koog.rag.base.storage.FilteringDeletionStorage
import ai.koog.rag.base.storage.IngestionStorage
import ai.koog.rag.base.storage.RetrievalStorage
import ai.koog.rag.base.storage.capability.CapabilityAwareStorage

/**
 * A unified storage interface that combines [IngestionStorage], [RetrievalStorage],
 * [FilteringDeletionStorage], and [CapabilityAwareStorage] for use with Spring AI vector stores.
 *
 * Users can inject this single interface as a Spring Bean to access all storage
 * capabilities (ingestion, retrieval, deletion, and capability querying) through one dependency.
 */
public interface KoogVectorStore :
    IngestionStorage<DocumentWithMetadata>,
    RetrievalStorage<DocumentWithMetadata>,
    FilteringDeletionStorage,
    CapabilityAwareStorage
// TODO: consider a CompletableFuture facade
