package ai.koog.agents.longtermmemory.retrieval

import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.longtermmemory.retrieval.augmentation.PromptAugmenter
import ai.koog.agents.longtermmemory.retrieval.augmentation.SystemPromptAugmenter
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.search.SimilaritySearchRequest

/**
 * Settings controlling how memory records are retrieved and injected into prompts (RAG).
 *
 * @param storage The retrieval storage to search for relevant memory records.
 * @param queryExtractor The extractor that defines how to derive the search query from the prompt.
 *   Defaults to [LastUserMessageQueryExtractor], which uses the last user message content.
 * @param searchStrategy The strategy that defines how to search the retrieval store.
 * @param promptAugmenter The augmenter that defines how retrieved context is inserted into the prompt.
 * @param enableAutomaticRetrieval When `true` (default), retrieval and prompt augmentation happen
 *   automatically before each LLM call. When `false`, the storage and strategy are still accessible
 *   for manual use inside graph strategy nodes via [ai.koog.agents.longtermmemory.feature.withLongTermMemory].
 * @param namespace Namespace (table/collection name) for a request.
 */
@ExperimentalAgentsApi
public data class RetrievalSettings(
    val storage: SearchStorage<TextDocument, SimilaritySearchRequest>,
    val queryExtractor: QueryExtractor = LastUserMessageQueryExtractor(),
    val searchStrategy: SearchStrategy = SimilaritySearchStrategy(),
    val promptAugmenter: PromptAugmenter = SystemPromptAugmenter(),
    val enableAutomaticRetrieval: Boolean = true,
    val namespace: String? = null
)
