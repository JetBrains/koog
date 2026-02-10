package ai.koog.agents.longtermmemory.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.featureOrThrow
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentPlannerFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPlannerPipeline
import ai.koog.agents.longtermmemory.ingestion.IngestionSettings
import ai.koog.agents.longtermmemory.ingestion.IngestionStorage
import ai.koog.agents.longtermmemory.ingestion.IngestionTiming
import ai.koog.agents.longtermmemory.ingestion.extraction.MemoryRecordExtractor
import ai.koog.agents.longtermmemory.retrieval.RetrievalSettings
import ai.koog.agents.longtermmemory.retrieval.RetrievalStorage
import ai.koog.agents.longtermmemory.retrieval.SearchStrategy
import ai.koog.agents.longtermmemory.retrieval.augmentation.PromptAugmenter
import ai.koog.agents.longtermmemory.retrieval.augmentation.SystemPromptAugmenter
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponses
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
@ExperimentalAgentsApi
public class LongTermMemory(
    private val retrievalSettings: RetrievalSettings? = null,
    private val ingestionSettings: IngestionSettings? = null,
) {
    /**
     * Buffer to accumulate streaming frames by runId.
     * Frames are accumulated during streaming and saved when streaming completes.
     */
    private val streamingFramesBuffer: MutableMap<String, MutableList<StreamFrame>> = mutableMapOf()
    private val streamingFramesBufferMutex = Mutex()

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
         *     extractor = FilteringMemoryRecordExtractor(setOf(Message.Role.User))
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
        public var storage: RetrievalStorage? = null

        /**
         * The search strategy that defines how to search the retrieval storage.
         *
         * @see SearchStrategy
         */
        public var searchStrategy: SearchStrategy? = null

        /**
         * The augmenter that defines how retrieved context is inserted into the prompt.
         * Defaults to [SystemPromptAugmenter].
         *
         * @see SystemPromptAugmenter
         * @see ai.koog.agents.longtermmemory.retrieval.augmentation.UserPromptAugmenter
         */
        public var promptAugmenter: PromptAugmenter = SystemPromptAugmenter()

        /**
         * Fluent setter for [storage].
         */
        public fun withStorage(storage: RetrievalStorage): RetrievalSettingsBuilder = apply { this.storage = storage }

        /**
         * Fluent setter for [searchStrategy].
         */
        public fun withSearchStrategy(searchStrategy: SearchStrategy): RetrievalSettingsBuilder =
            apply { this.searchStrategy = searchStrategy }

        /**
         * Fluent setter for [promptAugmenter].
         */
        public fun withPromptAugmenter(augmenter: PromptAugmenter): RetrievalSettingsBuilder =
            apply { this.promptAugmenter = augmenter }

        /**
         * RetrievalSettings builder.
         */
        public fun build(): RetrievalSettings {
            val retrievalStorage = storage ?: error("storage must be set in retrieval { } block")
            return RetrievalSettings(
                retrievalStorage,
                searchStrategy,
                promptAugmenter
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
        public var storage: IngestionStorage? = null

        /**
         * The extractor that defines how to transform messages into memory records.
         *
         * Pre-built ingesters are available:
         * - [ai.koog.agents.longtermmemory.ingestion.extraction.FilteringMemoryRecordExtractor] - Filters messages by role
         *
         * Example usage:
         * ```kotlin
         * // Use pre-built extractor with parameters
         * extractor = FilteringMemoryRecordExtractor(
         *     messageRolesToExtract = setOf(Message.Role.User)
         * )
         *
         * // Or use lambda for custom logic
         * extractor = MemoryRecordExtractor { messages ->
         *     messages.map { MemoryRecord.Plain(content = it.content) }
         * }
         * ```
         */
        public var extractor: MemoryRecordExtractor? = null

        /**
         * When to mapMessages messages. Defaults to [IngestionTiming.ON_LLM_CALL].
         */
        public var timing: IngestionTiming = IngestionTiming.ON_LLM_CALL

        /**
         * Fluent setter for [storage].
         */
        public fun withStorage(storage: IngestionStorage): IngestionSettingsBuilder = apply { this.storage = storage }

        /**
         * Fluent setter for [extractor].
         */
        public fun withExtractor(extractor: MemoryRecordExtractor): IngestionSettingsBuilder =
            apply { this.extractor = extractor }

        /**
         * Fluent setter for [timing].
         */
        public fun withTiming(timing: IngestionTiming): IngestionSettingsBuilder = apply { this.timing = timing }

        /**
         * IngestionSettings builder.
         */
        public fun build(): IngestionSettings {
            val ingestionStorage = storage ?: error("storage must be set in ingestion { } block")
            return IngestionSettings(ingestionStorage, extractor, timing)
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

        override fun createInitialConfig(): Config = Config()

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

            val ingestionExtractor = config.ingestionSettings?.memoryRecordExtractor
            val searchStrategy = config.retrievalSettings?.searchStrategy

            if (ingestionExtractor == null && searchStrategy == null) {
                return ltmFeature
            }

            // Note: ingestion interceptors on "Starting" events must be registered before
            // retrieval interceptors so that messages are ingested before prompt augmentation.
            if (ingestionExtractor != null) {
                installIngestionInterceptors(ltmFeature, pipeline)
            }
            if (searchStrategy != null) {
                installRetrievalInterceptors(ltmFeature, pipeline)
            }
            installCleanupInterceptors(ltmFeature, pipeline)

            return ltmFeature
        }

        /**
         * Install interceptors for ingesting messages into the memory record repository.
         *
         * Depending on [IngestionTiming], interceptors are installed either on individual LLM
         * calls/streams ([IngestionTiming.ON_LLM_CALL]) or on agent completion
         * ([IngestionTiming.ON_AGENT_COMPLETION]).
         */
        private fun installIngestionInterceptors(
            ltmFeature: LongTermMemory,
            pipeline: AIAgentPipeline,
        ) {
            val ingestion = ltmFeature.ingestionSettings ?: return

            when (ingestion.timing) {
                IngestionTiming.ON_LLM_CALL -> installOnLLMCallIngestion(ltmFeature, ingestion, pipeline)
                IngestionTiming.ON_AGENT_COMPLETION -> installOnAgentCompletionIngestion(ingestion, pipeline)
            }
        }

        /**
         * Install ingestion interceptors for [IngestionTiming.ON_LLM_CALL] mode.
         * Covers both regular and streaming LLM calls.
         */
        private fun installOnLLMCallIngestion(
            ltmFeature: LongTermMemory,
            ingestion: IngestionSettings,
            pipeline: AIAgentPipeline,
        ) {
            // Ingest original (not yet augmented) prompt messages before regular LLM call
            pipeline.interceptLLMCallStarting(this) { ctx ->
                ingestMessages(ingestion, ctx.prompt.messages)
            }

            // Ingest assistant responses after regular LLM call
            pipeline.interceptLLMCallCompleted(this) { ctx ->
                ingestMessages(ingestion, ctx.responses)
            }

            // Ingest original (not yet augmented) prompt messages before streaming LLM call
            pipeline.interceptLLMStreamingStarting(this) { ctx ->
                ingestMessages(ingestion, ctx.prompt.messages)
            }

            // Accumulate streaming frames in buffer
            pipeline.interceptLLMStreamingFrameReceived(this) { ctx ->
                ltmFeature.streamingFramesBufferMutex.withLock {
                    ltmFeature.streamingFramesBuffer.getOrPut(ctx.runId) { mutableListOf() }
                        .add(ctx.streamFrame)
                }
            }

            // Clean up the buffer on streaming failure
            pipeline.interceptLLMStreamingFailed(this) { ctx ->
                ltmFeature.streamingFramesBufferMutex.withLock {
                    ltmFeature.streamingFramesBuffer.remove(ctx.runId)
                }
            }

            // Ingest accumulated streaming response on streaming completion
            pipeline.interceptLLMStreamingCompleted(this) { ctx ->
                val frames = ltmFeature.streamingFramesBufferMutex.withLock {
                    ltmFeature.streamingFramesBuffer.remove(ctx.runId)
                }
                if (!frames.isNullOrEmpty()) {
                    ingestMessages(ingestion, frames.toMessageResponses())
                }
            }
        }

        /**
         * Install ingestion interceptors for [IngestionTiming.ON_AGENT_COMPLETION] mode.
         * All messages are saved at once when the agent completes.
         */
        private fun installOnAgentCompletionIngestion(
            ingestion: IngestionSettings,
            pipeline: AIAgentPipeline,
        ) {
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

        /**
         * Install safety-net interceptors to clean up any leaked streaming buffer entries
         * when the agent completes or fails.
         */
        private fun installCleanupInterceptors(
            ltmFeature: LongTermMemory,
            pipeline: AIAgentPipeline,
        ) {
            pipeline.interceptAgentCompleted(this) { ctx ->
                ltmFeature.streamingFramesBufferMutex.withLock {
                    ltmFeature.streamingFramesBuffer.remove(ctx.runId)
                }
            }

            pipeline.interceptAgentExecutionFailed(this) { ctx ->
                ltmFeature.streamingFramesBufferMutex.withLock {
                    ltmFeature.streamingFramesBuffer.remove(ctx.runId)
                }
            }
        }

        private suspend fun ingestMessages(
            ingestion: IngestionSettings,
            messages: List<Message>,
        ) {
            val records = ingestion.memoryRecordExtractor?.extract(messages) ?: return
            if (records.isEmpty()) {
                return
            }

            try {
                ingestion.storage.add(records, ingestion.namespace)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Failed to ingest ${records.size} memory records." }
            }
        }

        /**
         * Returns an augmented prompt only if there are relevant memory records for the last user's message.
         */
        private suspend fun getAugmentedPromptOrNull(
            prompt: Prompt,
            retrieval: RetrievalSettings,
        ): Prompt? {
            // TODO: the input for retrieval (last user message or custom) must be configurable via QueryExtractor.
            val lastUserMessage = prompt.messages.lastOrNull { it.role == Message.Role.User } ?: return null

            val searchStrategy = retrieval.searchStrategy ?: return null
            val searchResults = try {
                val request = searchStrategy.create(lastUserMessage.content)
                retrieval.storage.search(request, retrieval.namespace)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Failed to search memory records for $searchStrategy." }
                emptyList()
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
     * Property getter for [RetrievalStorage] for usage inside strategy nodes
     */
    public val retrievalStorage: RetrievalStorage?
        get() = retrievalSettings?.storage

    /**
     * Property getter for [IngestionStorage] for usage inside strategy nodes
     */
    public val ingestionStorage: IngestionStorage?
        get() = ingestionSettings?.storage
}

/**
 * Returns the [LongTermMemory] feature instance installed on this agent.
 *
 * @throws IllegalStateException if the [LongTermMemory] feature is not installed.
 * @see withLongTermMemory
 */
@OptIn(ExperimentalAgentsApi::class)
public fun AIAgentContext.longTermMemory(): LongTermMemory = featureOrThrow(LongTermMemory)

/**
 * Executes the given [action] in the context of the [LongTermMemory] feature installed on this agent.
 *
 * This is the primary way to access long-term memory storages from within strategy nodes.
 * Inside the [action] block, you can use [LongTermMemory.retrievalSettings] and
 * [LongTermMemory.ingestionSettings] to search and add memory records.
 *
 * Example usage:
 * ```kotlin
 * val myNode by node<String, Unit> {
 *     withLongTermMemory {
 *         this.getIngestionStorage()?.add(records, namespace)
 *         val results = this.getRetrievalStorage()?.search(request, namespace)
 *     }
 * }
 * ```
 *
 * @param action the block to execute with [LongTermMemory] as the receiver.
 * @return the result of the [action] block.
 * @throws IllegalStateException if the [LongTermMemory] feature is not installed.
 * @see longTermMemory
 */
@OptIn(ExperimentalAgentsApi::class)
public suspend fun <T> AIAgentContext.withLongTermMemory(action: suspend LongTermMemory.() -> T): T =
    longTermMemory().action()
