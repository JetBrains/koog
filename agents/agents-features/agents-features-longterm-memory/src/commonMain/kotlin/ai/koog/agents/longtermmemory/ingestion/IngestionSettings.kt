package ai.koog.agents.longtermmemory.ingestion

import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.longtermmemory.ingestion.extraction.MemoryRecordExtractor

/**
 * Settings controlling how messages are persisted (ingested) into the memory repository.
 *
 * @param storage The ingestion storage where memory records will be persisted.
 * @param memoryRecordExtractor The extractor that defines how to transform messages into memory records.
 *   Pre-built ingesters are available:
 *   - [ai.koog.agents.longtermmemory.ingestion.extraction.FilteringMemoryRecordExtractor] - Filters messages by role
 *   Custom ingesters can be provided as lambdas via the [ai.koog.agents.longtermmemory.ingestion.extraction.MemoryRecordExtractor] SAM interface.
 * @param timing When to mapMessages messages. Defaults to [IngestionTiming.ON_LLM_CALL].
 * @param namespace Namespace (table/collection name) for a request
 */
@ExperimentalAgentsApi
public data class IngestionSettings(
    val storage: IngestionStorage,
    val memoryRecordExtractor: MemoryRecordExtractor? = null,
    val timing: IngestionTiming = IngestionTiming.ON_LLM_CALL,
    val namespace: String? = null
)
