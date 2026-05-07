package ai.koog.agents.longtermmemory.ingestion

import ai.koog.agents.longtermmemory.feature.FailurePolicy
import ai.koog.agents.longtermmemory.ingestion.extraction.DocumentExtractor
import ai.koog.agents.longtermmemory.ingestion.extraction.MessagePassingDocumentExtractor
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.WriteStorage

/**
 * Settings controlling how messages are persisted (ingested) into the memory repository.
 *
 * Ingestion happens once at agent completion: the final accumulated session prompt/history
 * is passed to the configured [documentExtractor] as a single batch.
 *
 * @param storage The ingestion storage where memory records will be persisted.
 * @param documentExtractor The extractor that defines how to transform messages into memory records.
 *   Pre-built ingesters are available:
 *   - [ai.koog.agents.longtermmemory.ingestion.extraction.MessagePassingDocumentExtractor] - Filters messages by role
 *   Custom ingesters can be provided as lambdas via the [ai.koog.agents.longtermmemory.ingestion.extraction.DocumentExtractor] SAM interface.
 * @param enableAutomaticIngestion When `true` (default), ingestion happens automatically on agent
 *   completion. When `false`, the storage is still accessible for manual use inside graph strategy
 *   nodes via [ai.koog.agents.longtermmemory.feature.withLongTermMemory].
 * @param namespace Namespace (table/collection name) for a request
 * @param failurePolicy How to react to failures from [storage] when persisting records.
 *   Defaults to [FailurePolicy.LOG_AND_CONTINUE] so transient ingestion errors do not abort
 *   the agent run. Set to [FailurePolicy.FAIL_FAST] for durable audit/logging use cases
 *   where losing memory records is worse than failing the run.
 */
public data class IngestionSettings(
    val storage: WriteStorage<TextDocument>,
    val documentExtractor: DocumentExtractor = MessagePassingDocumentExtractor(),
    val enableAutomaticIngestion: Boolean = true,
    val namespace: String? = null,
    val failurePolicy: FailurePolicy = FailurePolicy.LOG_AND_CONTINUE,
)
