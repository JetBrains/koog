package ai.koog.agents.longtermmemory.retrieval

import ai.koog.agents.longtermmemory.feature.FailurePolicy
import ai.koog.agents.longtermmemory.retrieval.augmentation.PromptAugmenter
import ai.koog.agents.longtermmemory.retrieval.augmentation.SystemPromptAugmenter
import ai.koog.agents.longtermmemory.retrieval.search.LastUserMessageQueryProvider
import ai.koog.agents.longtermmemory.retrieval.search.SearchQueryProvider
import ai.koog.agents.longtermmemory.retrieval.search.SearchStrategy
import ai.koog.agents.longtermmemory.retrieval.search.SimilaritySearchStrategy
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.search.SearchRequest

/**
 * Settings controlling how memory records are retrieved and injected into prompts (RAG).
 *
 * @param storage The retrieval storage to search for relevant memory records.
 * @param searchQueryProvider The provider that defines how to derive the search query from the prompt.
 *   Defaults to [ai.koog.agents.longtermmemory.retrieval.search.LastUserMessageQueryProvider], which uses the last user message content.
 * @param searchStrategy The strategy that defines how to search the retrieval store.
 * @param promptAugmenter The augmenter that defines how retrieved context is inserted into the prompt.
 * @param enableAutomaticRetrieval When `true` (default), retrieval and prompt augmentation happen
 *   automatically before each LLM call. When `false`, the storage and strategy are still accessible
 *   for manual use inside graph strategy nodes via [ai.koog.agents.longtermmemory.feature.withLongTermMemory].
 * @param namespace Namespace (table/collection name) for a request.
 * @param failurePolicy How to react to failures from [storage] or [searchStrategy].
 *   Defaults to [FailurePolicy.FAIL_FAST] so that retrieval errors stop the LLM call instead
 *   of silently producing an answer without the required memory context.
 */
public data class RetrievalSettings(
    val storage: SearchStorage<TextDocument, SearchRequest>,
    val searchQueryProvider: SearchQueryProvider = LastUserMessageQueryProvider(),
    val searchStrategy: SearchStrategy = SimilaritySearchStrategy(),
    val promptAugmenter: PromptAugmenter = SystemPromptAugmenter(),
    val enableAutomaticRetrieval: Boolean = true,
    val namespace: String? = null,
    val failurePolicy: FailurePolicy = FailurePolicy.FAIL_FAST,
)
