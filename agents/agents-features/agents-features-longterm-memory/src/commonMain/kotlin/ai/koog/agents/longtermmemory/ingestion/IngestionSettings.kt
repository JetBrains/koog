package ai.koog.agents.longtermmemory.ingestion

import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.longtermmemory.ingestion.extraction.ExtractionStrategy
import ai.koog.agents.longtermmemory.ingestion.extraction.FilteringExtractionStrategy
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.WriteStorage

/**
 * Settings controlling how messages are persisted (ingested) into the memory repository.
 *
 * @param storage The ingestion storage where memory records will be persisted.
 * @param extractionStrategy The extractor that defines how to transform messages into memory records.
 *   Pre-built ingesters are available:
 *   - [ai.koog.agents.longtermmemory.ingestion.extraction.FilteringExtractionStrategy] - Filters messages by role
 *   Custom ingesters can be provided as lambdas via the [ai.koog.agents.longtermmemory.ingestion.extraction.ExtractionStrategy] SAM interface.
 * @param timing When to ingest messages. Defaults to [IngestionTiming.ON_LLM_CALL].
 *   - [IngestionTiming.ON_LLM_CALL]: prompt messages are ingested before each LLM call starts,
 *     and the assistant output is ingested after completion or stream completion.
 *   - [IngestionTiming.ON_AGENT_COMPLETION]: the final accumulated session prompt/history is
 *     ingested once at agent completion.
 * @param enableAutomaticIngestion When `true` (default), ingestion happens automatically after LLM
 *   calls or on agent completion (depending on [timing]). When `false`, the storage is still
 *   accessible for manual use inside graph strategy nodes via [ai.koog.agents.longtermmemory.feature.withLongTermMemory].
 * @param namespace Namespace (table/collection name) for a request
 */
@ExperimentalAgentsApi
public data class IngestionSettings(
    val storage: WriteStorage<TextDocument>,
    val extractionStrategy: ExtractionStrategy = FilteringExtractionStrategy(),
    val timing: IngestionTiming = IngestionTiming.ON_LLM_CALL,
    val enableAutomaticIngestion: Boolean = true,
    val namespace: String? = null
)
