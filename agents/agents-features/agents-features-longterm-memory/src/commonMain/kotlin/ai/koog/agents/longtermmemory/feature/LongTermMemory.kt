package ai.koog.agents.longtermmemory.feature

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.featureOrThrow
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentPlannerFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPlannerPipeline
import ai.koog.agents.longtermmemory.ingestion.IngestionSettings
import ai.koog.agents.longtermmemory.ingestion.extraction.DocumentExtractor
import ai.koog.agents.longtermmemory.ingestion.extraction.MessagePassingDocumentExtractor
import ai.koog.agents.longtermmemory.retrieval.RetrievalSettings
import ai.koog.agents.longtermmemory.retrieval.augmentation.PromptAugmenter
import ai.koog.agents.longtermmemory.retrieval.augmentation.SystemPromptAugmenter
import ai.koog.agents.longtermmemory.retrieval.search.LastUserMessageQueryProvider
import ai.koog.agents.longtermmemory.retrieval.search.SearchQueryProvider
import ai.koog.agents.longtermmemory.retrieval.search.SearchStrategy
import ai.koog.agents.longtermmemory.retrieval.search.SimilaritySearchStrategy
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.WriteStorage
import ai.koog.rag.base.storage.search.SearchRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.cancellation.CancellationException

/**
 * Memory feature that incorporates persistent storage of memory records (documents) in vector databases.
 *
 * This feature provides two main capabilities that can be configured independently:
 * 1. **Retrieval (RAG)**: Retrieves relevant context from the memory record repository using a
 *    configured search request builder and inserts it into the prompt before sending to the LLM.
 *    Configured via [retrievalSettings]. When null, no retrieval/augmentation is performed.
 * 2. **Ingestion**: Saves messages to the memory record repository for future retrieval.
 *    Configured via [ingestionSettings]. When null, no messages are persisted.
 *
 * @see RetrievalSettings
 * @see IngestionSettings
 */
public class LongTermMemory(
    private val retrievalSettings: RetrievalSettings? = null,
    private val ingestionSettings: IngestionSettings? = null,
) {
    /**
     * Configuration for the LongTermMemory feature.
     *
     * This class allows configuring:
     * - Retrieval settings for RAG (prompt augmentation with context from the repository)
     * - Ingestion settings for persisting messages to the repository
     *
     * Both [retrievalSettings] and [ingestionSettings] are null by default, meaning neither
     * retrieval nor ingestion is active unless explicitly configured.
     */
    public class Config : FeatureConfig() {
        /**
         * Settings for retrieval-augmented generation (RAG).
         * When null (default), no context augmentation from the memory record repository is performed.
         *
         * Can be set directly or configured via the [retrieval] DSL block.
         */
        public var retrievalSettings: RetrievalSettings? = null

        /**
         * Settings for ingesting (persisting) messages into the memory record repository.
         * When null (default), no messages are persisted.
         *
         * Can be set directly or configured via the [ingestion] DSL block.
         */
        public var ingestionSettings: IngestionSettings? = null

        /**
         * Configure retrieval (RAG) settings via DSL block.
         *
         * Example usage:
         * ```kotlin
         * retrieval {
         *     searchStrategy = SimilaritySearchStrategy(topK = 5)
         *     promptAugmenter = UserPromptAugmenter()
         * }
         * ```
         */
        public fun retrieval(block: RetrievalSettingsBuilder.() -> Unit) {
            retrievalSettings = RetrievalSettingsBuilder().apply(block).build()
        }

        /**
         * Set pre-built retrieval settings directly.
         */
        public fun retrieval(settings: RetrievalSettings) {
            retrievalSettings = settings
        }

        /**
         * Configure ingestion settings via DSL block.
         *
         * Example usage:
         * ```kotlin
         * ingestion {
         *     documentExtractor = MessagePassingDocumentExtractor(setOf(Message.Role.User))
         * }
         * ```
         */
        public fun ingestion(block: IngestionSettingsBuilder.() -> Unit) {
            ingestionSettings = IngestionSettingsBuilder().apply(block).build()
        }

        /**
         * Set pre-built ingestion settings directly.
         */
        public fun ingestion(settings: IngestionSettings) {
            ingestionSettings = settings
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

    /**
     * Builder for [IngestionSettings] used in the [Config.ingestion] DSL block.
     */
    public class IngestionSettingsBuilder {
        /**
         * The ingestion storage where memory records will be persisted.
         * Must be set explicitly in the ingestion { } block.
         */
        public var storage: WriteStorage<TextDocument>? = null

        /**
         * The extractor that defines how to transform messages into memory records.
         *
         * Pre-built ingesters are available:
         * - [ai.koog.agents.longtermmemory.ingestion.extraction.MessagePassingDocumentExtractor] - Filters messages by role
         *
         * Example usage:
         * ```kotlin
         * // Use pre-built extractor with parameters
         * documentExtractor = MessagePassingDocumentExtractor(
         *     messageRolesToExtract = setOf(Message.Role.User)
         * )
         *
         * // Or use lambda for custom logic
         * documentExtractor = DocumentExtractor { messages ->
         *     messages.map { TextDocument(content = it.content) }
         * }
         * ```
         */
        public var documentExtractor: DocumentExtractor = MessagePassingDocumentExtractor()

        /**
         * When `true` (default), ingestion happens automatically on agent completion.
         * When `false`, the storage is still accessible for manual use inside graph strategy nodes.
         */
        public var enableAutomaticIngestion: Boolean = true

        /**
         * Namespace (table/collection name) for a request.
         */
        public var namespace: String? = null

        /**
         * How to react to ingestion failures (e.g. storage outage).
         *
         * Defaults to [FailurePolicy.LOG_AND_CONTINUE] so transient ingestion errors do not
         * abort the agent run. Switch to [FailurePolicy.FAIL_FAST] for durable audit/logging
         * use cases where losing memory records is worse than failing the run.
         */
        public var failurePolicy: FailurePolicy = FailurePolicy.LOG_AND_CONTINUE

        /**
         * Fluent setter for [storage].
         */
        public fun withStorage(storage: WriteStorage<TextDocument>): IngestionSettingsBuilder = apply { this.storage = storage }

        /**
         * Fluent setter for [documentExtractor].
         */
        public fun withDocumentExtractor(documentExtractor: DocumentExtractor): IngestionSettingsBuilder =
            apply { this.documentExtractor = documentExtractor }

        /**
         * Fluent setter for [enableAutomaticIngestion].
         */
        public fun withEnableAutomaticIngestion(enable: Boolean): IngestionSettingsBuilder =
            apply { this.enableAutomaticIngestion = enable }

        /**
         * Fluent setter for [namespace].
         */
        public fun withNamespace(namespace: String): IngestionSettingsBuilder =
            apply { this.namespace = namespace }

        /**
         * Fluent setter for [failurePolicy].
         */
        public fun withFailurePolicy(failurePolicy: FailurePolicy): IngestionSettingsBuilder =
            apply { this.failurePolicy = failurePolicy }

        /**
         * IngestionSettings builder.
         */
        public fun build(): IngestionSettings {
            val ingestionStorage = requireNotNull(storage) { "storage must be set in ingestion { } block" }
            return IngestionSettings(
                ingestionStorage,
                documentExtractor,
                enableAutomaticIngestion,
                namespace,
                failurePolicy,
            )
        }
    }

    /**
     * Companion object implementing agent feature, handling [LongTermMemory] creation and installation.
     */
    public companion object Feature :
        AIAgentGraphFeature<Config, LongTermMemory>,
        AIAgentFunctionalFeature<Config, LongTermMemory>,
        AIAgentPlannerFeature<Config, LongTermMemory> {
        private val logger = KotlinLogging.logger { }

        override val key: AIAgentStorageKey<LongTermMemory> =
            createStorageKey("long-term-memory-feature")

        override fun createInitialConfig(
            agentConfig: AIAgentConfig
        ): Config = Config()

        /**
         * Create a feature implementation using the provided configuration.
         */
        private fun createFeature(
            config: Config,
            pipeline: AIAgentPipeline,
        ): LongTermMemory {
            val ltmFeature = LongTermMemory(
                retrievalSettings = config.retrievalSettings,
                ingestionSettings = config.ingestionSettings,
            )

            val enableIngestion = config.ingestionSettings?.enableAutomaticIngestion == true
            val enableRetrieval = config.retrievalSettings?.enableAutomaticRetrieval == true

            if (!enableIngestion && !enableRetrieval) {
                return ltmFeature
            }

            if (enableIngestion) {
                installIngestionInterceptors(ltmFeature, pipeline)
            }
            if (enableRetrieval) {
                installRetrievalInterceptors(ltmFeature, pipeline)
            }

            return ltmFeature
        }

        /**
         * Install interceptors for ingesting messages into the memory record repository.
         *
         * Ingestion happens once at agent completion: the final accumulated session
         * prompt/history is passed to the configured extraction strategy as a single batch.
         */
        private fun installIngestionInterceptors(
            ltmFeature: LongTermMemory,
            pipeline: AIAgentPipeline,
        ) {
            val ingestion = ltmFeature.ingestionSettings ?: return

            pipeline.interceptAgentCompleted(this) { ctx ->
                ctx.context.llm.readSession {
                    ingestMessages(ingestion, prompt.messages)
                }
            }
        }

        /**
         * Install interceptors for retrieval-augmented generation (RAG).
         * Augments prompts with relevant context from the memory record repository
         * before both regular and streaming LLM calls.
         */
        private fun installRetrievalInterceptors(
            ltmFeature: LongTermMemory,
            pipeline: AIAgentPipeline,
        ) {
            val retrieval = ltmFeature.retrievalSettings ?: return

            // Augment prompt before regular LLM call
            pipeline.interceptLLMCallStarting(this) { ctx ->
                val augmentedPrompt = getAugmentedPromptOrNull(ctx.prompt, retrieval)
                if (augmentedPrompt != null) {
                    ctx.context.llm.prompt = augmentedPrompt // TODO: switch to writeSession after the KG-688
                }
            }

            // Augment prompt before streaming LLM call
            pipeline.interceptLLMStreamingStarting(this) { ctx ->
                val augmentedPrompt = getAugmentedPromptOrNull(ctx.prompt, retrieval)
                if (augmentedPrompt != null) {
                    ctx.context.llm.writeSession {
                        prompt = augmentedPrompt
                    }
                }
            }
        }

        private suspend fun ingestMessages(
            ingestion: IngestionSettings,
            messages: List<Message>,
        ) {
            val records = ingestion.documentExtractor.extract(messages)
            if (records.isEmpty()) {
                return
            }

            try {
                ingestion.storage.add(records, ingestion.namespace)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when (ingestion.failurePolicy) {
                    FailurePolicy.FAIL_FAST -> throw LongTermMemoryIngestionException(
                        "Failed to ingest ${records.size} memory records.",
                        e,
                    )
                    FailurePolicy.LOG_AND_CONTINUE ->
                        logger.error(e) { "Failed to ingest ${records.size} memory records." }
                }
            }
        }

        /**
         * Returns an augmented prompt only if there are relevant memory records for the query provided by searchQueryProvider.
         */
        private suspend fun getAugmentedPromptOrNull(
            prompt: Prompt,
            retrieval: RetrievalSettings,
        ): Prompt? {
            val query = retrieval.searchQueryProvider.provide(prompt) ?: return null

            val searchResults = try {
                val request = retrieval.searchStrategy.create(query)
                retrieval.storage.search(request, retrieval.namespace)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when (retrieval.failurePolicy) {
                    FailurePolicy.FAIL_FAST -> throw LongTermMemoryRetrievalException(
                        "Failed to search memory records for ${retrieval.searchStrategy}.",
                        e,
                    )
                    FailurePolicy.LOG_AND_CONTINUE -> {
                        logger.error(e) { "Failed to search memory records for ${retrieval.searchStrategy}." }
                        emptyList()
                    }
                }
            }
            if (searchResults.isEmpty()) {
                return null
            }

            return retrieval.promptAugmenter.augment(
                originalPrompt = prompt,
                relevantContext = searchResults
            )
        }

        override fun install(
            config: Config,
            pipeline: AIAgentGraphPipeline,
        ): LongTermMemory = createFeature(config, pipeline)

        override fun install(
            config: Config,
            pipeline: AIAgentFunctionalPipeline,
        ): LongTermMemory = createFeature(config, pipeline)

        override fun install(
            config: Config,
            pipeline: AIAgentPlannerPipeline,
        ): LongTermMemory = createFeature(config, pipeline)
    }

    /**
     * Property getter for [SearchStorage] for usage inside strategy nodes
     */
    public val retrievalStorage: SearchStorage<TextDocument, SearchRequest>?
        get() = retrievalSettings?.storage

    /**
     * Property getter for [WriteStorage] for usage inside strategy nodes
     */
    public val ingestionStorage: WriteStorage<TextDocument>?
        get() = ingestionSettings?.storage
}

/**
 * Returns the [LongTermMemory] feature instance installed on this agent.
 *
 * @throws IllegalStateException if the [LongTermMemory] feature is not installed.
 * @see withLongTermMemory
 */
public fun AIAgentContext.longTermMemory(): LongTermMemory = featureOrThrow(LongTermMemory)

/**
 * Executes the given [action] in the context of the [LongTermMemory] feature installed on this agent.
 *
 * This is the primary way to access long-term memory storages from within strategy nodes.
 * Inside the [action] block, you can use [LongTermMemory.retrievalStorage] and
 * [LongTermMemory.ingestionStorage] to search and add memory records.
 *
 * Example usage:
 * ```kotlin
 * val myNode by node<String, Unit> {
 *     withLongTermMemory {
 *         ingestionStorage?.add(records, namespace = "my-namespace")
 *         val results = retrievalStorage?.search(request, namespace = "my-namespace")
 *     }
 * }
 * ```
 *
 * @param action the block to execute with [LongTermMemory] as the receiver.
 * @return the result of the [action] block.
 * @throws IllegalStateException if the [LongTermMemory] feature is not installed.
 * @see longTermMemory
 */
public suspend fun <T> AIAgentContext.withLongTermMemory(action: suspend LongTermMemory.() -> T): T =
    longTermMemory().action()
