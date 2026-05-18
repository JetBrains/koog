package ai.koog.agents.longtermmemory.retrieval

import ai.koog.agents.longtermmemory.feature.FailurePolicy
import ai.koog.agents.longtermmemory.feature.LongTermMemory.Config
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
) {
    /**
     * Companion object for [RetrievalSettingsBuilder].
     * */
    public companion object {
        /**
         * Creates a new instance of [RetrievalSettingsBuilder] to configure and build [RetrievalSettings].
         *
         * @return A new instance of [RetrievalSettingsBuilder] for building [RetrievalSettings].
         */
        public fun builder(): RetrievalSettingsBuilder = RetrievalSettingsBuilder()
    }
}

/**
 * Builder for [RetrievalSettings] used in the [Config.retrieval] DSL block.
 */
public class RetrievalSettingsBuilder {
    /**
     * The retrieval storage to search for relevant memory records.
     * Must be set explicitly in the retrieval { } block.
     */
    public var storage: SearchStorage<TextDocument, SearchRequest>? = null

    /**
     * The extractor that defines how to derive the search query from the prompt.
     * Defaults to [LastUserMessageQueryProvider].
     *
     * @see SearchQueryProvider
     * @see LastUserMessageQueryProvider
     */
    public var searchQueryProvider: SearchQueryProvider = LastUserMessageQueryProvider()

    /**
     * The search strategy that defines how to search the retrieval storage.
     *
     * @see SearchStrategy
     */
    public var searchStrategy: SearchStrategy = SimilaritySearchStrategy()

    /**
     * When `true` (default), retrieval and prompt augmentation happen automatically
     * before each LLM call. When `false`, the storage and strategy are still accessible
     * for manual use inside graph strategy nodes.
     */
    public var enableAutomaticRetrieval: Boolean = true

    /**
     * The augmenter that defines how retrieved context is inserted into the prompt.
     * Defaults to [SystemPromptAugmenter].
     *
     * @see SystemPromptAugmenter
     * @see ai.koog.agents.longtermmemory.retrieval.augmentation.UserPromptAugmenter
     */
    public var promptAugmenter: PromptAugmenter = SystemPromptAugmenter()

    /**
     * Namespace (table/collection name) for a request.
     */
    public var namespace: String? = null

    /**
     * How to react to retrieval failures (e.g. storage outage, invalid search request).
     *
     * Defaults to [FailurePolicy.FAIL_FAST] so a retrieval error stops the LLM call instead
     * of silently producing an answer without the required memory context. Switch to
     * [FailurePolicy.LOG_AND_CONTINUE] to fall back to a non-augmented LLM call.
     */
    public var failurePolicy: FailurePolicy = FailurePolicy.FAIL_FAST

    /**
     * Fluent setter for [storage].
     */
    public fun withStorage(storage: SearchStorage<TextDocument, SearchRequest>): RetrievalSettingsBuilder = apply { this.storage = storage }

    /**
     * Fluent setter for [searchQueryProvider].
     */
    public fun withSearchQueryProvider(searchQueryProvider: SearchQueryProvider): RetrievalSettingsBuilder =
        apply { this.searchQueryProvider = searchQueryProvider }

    /**
     * Fluent setter for [searchStrategy].
     */
    public fun withSearchStrategy(searchStrategy: SearchStrategy): RetrievalSettingsBuilder =
        apply { this.searchStrategy = searchStrategy }

    /**
     * Fluent setter for [enableAutomaticRetrieval].
     */
    public fun withEnableAutomaticRetrieval(enable: Boolean): RetrievalSettingsBuilder =
        apply { this.enableAutomaticRetrieval = enable }

    /**
     * Fluent setter for [promptAugmenter].
     */
    public fun withPromptAugmenter(augmenter: PromptAugmenter): RetrievalSettingsBuilder =
        apply { this.promptAugmenter = augmenter }

    /**
     * Fluent setter for [namespace].
     */
    public fun withNamespace(namespace: String): RetrievalSettingsBuilder =
        apply { this.namespace = namespace }

    /**
     * Fluent setter for [failurePolicy].
     */
    public fun withFailurePolicy(failurePolicy: FailurePolicy): RetrievalSettingsBuilder =
        apply { this.failurePolicy = failurePolicy }

    /**
     * RetrievalSettings builder.
     */
    public fun build(): RetrievalSettings {
        val retrievalStorage = requireNotNull(storage) { "storage must be set in retrieval { } block" }
        return RetrievalSettings(
            retrievalStorage,
            searchQueryProvider,
            searchStrategy,
            promptAugmenter,
            enableAutomaticRetrieval,
            namespace,
            failurePolicy,
        )
    }
}
