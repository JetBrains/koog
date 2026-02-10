package ai.koog.agents.longtermmemory.retrieval

import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.longtermmemory.retrieval.augmentation.PromptAugmenter
import ai.koog.agents.longtermmemory.retrieval.augmentation.SystemPromptAugmenter

/**
 * Settings controlling how memory records are retrieved and injected into prompts (RAG).
 *
 * @param storage The retrieval storage to search for relevant memory records.
 * @param searchStrategy The strategy that defines how to search the retrieval store.
 * @param promptAugmenter The augmenter that defines how retrieved context is inserted into the prompt.
 * @param namespace Namespace (table/collection name) for a request.
 */
@ExperimentalAgentsApi
public data class RetrievalSettings(
    val storage: RetrievalStorage,
    val searchStrategy: SearchStrategy? = null,
    val promptAugmenter: PromptAugmenter = SystemPromptAugmenter(),
    val namespace: String? = null
)
