package ai.koog.agents.memory.feature

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.rag.vector.database.EphemeralMemoryRecordRepository
import ai.koog.rag.vector.database.MemoryRecord
import ai.koog.rag.vector.database.MemoryRecordRepository
import ai.koog.rag.vector.database.records
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Memory feature that incorporates persistent storage of memory records (documents) in vector databases.
 *
 * This feature provides two main capabilities:
 * 1. **Storing assistant messages**: Optionally saves assistant messages as MemoryRecords
 *    for future retrieval (when [persistAssistantResponses] is enabled).
 * 2. **Augmenting prompts**: Retrieves relevant context from the memory record repository using a
 *    [MemoryRecordRetriever] and inserts it into the prompt before sending to the LLM.
 *
 * @see MemoryRecordRetriever
 * @see SimilarityRecordRetriever
 * @see KeywordRecordRetriever
 * @see HybridRecordRetriever
 */
@ExperimentalAgentsApi
public class Memory2(
    private val memoryRecordRepository: MemoryRecordRepository,
    private val persistAssistantResponses: Boolean = false,
    private val retriever: MemoryRecordRetriever? = null,
//    public val queryTransformer: (suspend (String) -> String)? = null, //TODO: support user query transformation
//    public val resultProcessor: (suspend (List<ScoredMemoryRecord<MemoryRecord>>) -> List<String>)? = null, //TODO: support result post-processing
    private val contextInsertionMode: ContextInsertionMode = ContextInsertionMode.SYSTEM_MESSAGE,
    private val systemPromptTemplate: String = Config.DEFAULT_SYSTEM_PROMPT_TEMPLATE,
    private val userContextTemplate: String = Config.DEFAULT_USER_CONTEXT_TEMPLATE,
) {
    /**
     * Buffer to accumulate streaming frames by runId.
     * Frames are accumulated during streaming and saved when streaming completes.
     */
    private val streamingFramesBuffer: MutableMap<String, MutableList<String>> = mutableMapOf()
    private val streamingFramesBufferMutex = Mutex()

    /**
     * Configuration for the Memory2 feature.
     *
     * This class allows configuring:
     * - The record repository
     * - Whether to persist assistant messages as memory records
     * - A retriever for retrieving relevant context (similarity, keyword, or hybrid)
     * - How context should be inserted into prompts
     */
    public class Config : FeatureConfig() {
        /**
         * The provider that handles the actual storage and retrieval of memory records.
         * Defaults to [EphemeralMemoryRecordRepository], an in-memory implementation useful for testing and development.
         */
        public var memoryRecordRepository: MemoryRecordRepository = EphemeralMemoryRecordRepository()

        /**
         * Whether to persist assistant messages as MemoryRecords in the repository.
         * When enabled, assistant responses are stored in [memoryRecordRepository] after each LLM call.
         * Defaults to false.
         */
        public var persistAssistantResponses: Boolean = false

        /**
         * The retriever that defines how to search the [memoryRecordRepository].
         *
         * This is called in `interceptLLMCallStarting` with the last user message content
         * as a parameter. If null, no context augmentation from memory record repository is performed.
         *
         * Pre-built retrievers are available:
         * - [SimilarityRecordRetriever] - Vector similarity (semantic) search
         * - [KeywordRecordRetriever] - Full-text/keyword search
         * - [HybridRecordRetriever] - Combined vector and keyword search
         *
         * Example usage:
         * ```kotlin
         * // Use pre-built retriever with parameters
         * retriever = SimilarityRecordRetriever(topK = 5, similarityThreshold = 0.7)
         *
         * // Or use factory method
         * retriever = MemoryRecordRetriever.similarity(topK = 5)
         *
         * // Or use lambda for custom logic
         * retriever = MemoryRecordRetriever { repo, query ->
         *     repo.search(SimilaritySearchRequest(query, limit = 10))
         * }
         * ```
         *
         * Defaults to null (no search/augmentation).
         */
        public var retriever: MemoryRecordRetriever? = null

        /**
         * Defines how retrieved context should be inserted into the prompt.
         * Defaults to [ContextInsertionMode.SYSTEM_MESSAGE].
         */
        public var contextInsertionMode: ContextInsertionMode = ContextInsertionMode.SYSTEM_MESSAGE

        /**
         * Template for the system message when using [ContextInsertionMode.SYSTEM_MESSAGE].
         * Use {relevant_context} placeholder.
         * Defaults to a standard RAG prompt template.
         */
        public var systemPromptTemplate: String = DEFAULT_SYSTEM_PROMPT_TEMPLATE

        /**
         * Template for user context when using [ContextInsertionMode.USER_MESSAGE_BEFORE_LAST]
         * or [ContextInsertionMode.AUGMENT_LAST_USER_MESSAGE].
         * Use {relevant_context} placeholder.
         * Defaults to a standard context template.
         */
        public var userContextTemplate: String = DEFAULT_USER_CONTEXT_TEMPLATE

        /**
         * Default templates for prompt augmentation.
         */
        public companion object {
            /**
             * Default template for the system message when using [ContextInsertionMode.SYSTEM_MESSAGE].
             * Use {relevant_context} placeholder.
             */
            public const val DEFAULT_SYSTEM_PROMPT_TEMPLATE: String = PromptAugmenter.DEFAULT_SYSTEM_PROMPT_TEMPLATE

            /**
             * Default template for user context when using [ContextInsertionMode.USER_MESSAGE_BEFORE_LAST]
             * or [ContextInsertionMode.AUGMENT_LAST_USER_MESSAGE].
             * Use {relevant_context} placeholder.
             */
            public const val DEFAULT_USER_CONTEXT_TEMPLATE: String = PromptAugmenter.DEFAULT_USER_CONTEXT_TEMPLATE
        }
    }

    /**
     * Companion object implementing agent feature, handling [Memory2] creation and installation.
     */
    public companion object Feature : AIAgentGraphFeature<Config, Memory2>, AIAgentFunctionalFeature<Config, Memory2> {
        private val logger = KotlinLogging.logger { }

        override val key: AIAgentStorageKey<Memory2> = createStorageKey<Memory2>("ai-agent-memory-feature")

        override fun createInitialConfig(): Config = Config()

        /**
         * Create a feature implementation using the provided configuration.
         */
        private fun createFeature(
            config: Config,
            pipeline: AIAgentPipeline,
        ): Memory2 {
            val memory2Feature = Memory2(
                memoryRecordRepository = config.memoryRecordRepository,
                persistAssistantResponses = config.persistAssistantResponses,
                retriever = config.retriever,
                contextInsertionMode = config.contextInsertionMode,
                systemPromptTemplate = config.systemPromptTemplate,
                userContextTemplate = config.userContextTemplate,
            )

            // Intercept before LLM call to augment prompt with context from a repository
            pipeline.interceptLLMCallStarting(this) { ctx ->
                val augmentedPrompt = getAugmentedPromptOrNull(ctx.prompt, memory2Feature)
                if (augmentedPrompt != null) {
                    ctx.context.llm.prompt = augmentedPrompt // Why not ctx.context.llm.writeSession? See below:
                    // Let's suppose a nodeLLMRequest() is called with this feature:
                    // nodeLLMRequest()
                    //  → llm.writeSession { ... }           // Acquires writeMutex
                    //    → requestLLM()
                    //      → executeMultiple()
                    //        → executor.executeProcessed()
                    //          → ContextualPromptExecutor.execute()
                    //            → context.pipeline.onLLMCallStarting()
                    //              → Memory2 interceptor
                    //                → ctx.context.llm.writeSession { ... }  // DEADLOCK! writeMutex already held
                    // The RWLock implementation uses kotlinx.coroutines.sync.Mutex which is not reentrant.
                    // When the same coroutine tries to acquire the mutex it already holds, it blocks forever waiting for itself to release it.
                    // CAVEAT: if someone calls the executor outside a write session, it could cause race conditions
                }
            }

            // Intercept after LLM call to save assistant response as memory record
            pipeline.interceptLLMCallCompleted(this) { ctx ->
                if (memory2Feature.persistAssistantResponses) {
                    val assistantMessagesAsMemoryRecords = ctx.responses
                        .filter { it.role == Message.Role.Assistant }
                        .map { MemoryRecord(content = it.content) }
                    if (assistantMessagesAsMemoryRecords.isNotEmpty()) {
                        // TODO: introduce configurable document chunking before saving to the repository
                        memory2Feature.memoryRecordRepository.add(assistantMessagesAsMemoryRecords)
                    }
                }
            }

            // Intercept before LLM streaming call to augment prompt with context from a repository
            pipeline.interceptLLMStreamingStarting(this) { ctx ->
                val augmentedPrompt = getAugmentedPromptOrNull(ctx.prompt, memory2Feature)
                if (augmentedPrompt != null) {
                    ctx.context.llm.prompt = augmentedPrompt
                }
            }

            // Intercept a stream frame received from an LLM to accumulate in buffer
            pipeline.interceptLLMStreamingFrameReceived(this) { ctx ->
                val frame = ctx.streamFrame

                if (memory2Feature.persistAssistantResponses && frame is StreamFrame.Append) {
                    memory2Feature.streamingFramesBufferMutex.withLock {
                        memory2Feature.streamingFramesBuffer
                            .getOrPut(ctx.runId) { mutableListOf() }
                            .add(frame.text)
                    }
                }
            }

            // Intercept streaming failure to clean up the buffer
            pipeline.interceptLLMStreamingFailed(this) { ctx ->
                if (memory2Feature.persistAssistantResponses) {
                    memory2Feature.streamingFramesBufferMutex.withLock {
                        memory2Feature.streamingFramesBuffer.remove(ctx.runId)
                    }
                }
            }

            // Intercept streaming completion to save all accumulated frames as memory records
            pipeline.interceptLLMStreamingCompleted(this) { ctx ->
                if (memory2Feature.persistAssistantResponses) {
                    val frames = memory2Feature.streamingFramesBufferMutex.withLock {
                        memory2Feature.streamingFramesBuffer.remove(ctx.runId)
                    }
                    if (!frames.isNullOrEmpty()) {
                        val fullResponse = frames.joinToString("")
                        // TODO: introduce configurable document chunking before saving to the repository
                        memory2Feature.memoryRecordRepository.add(listOf(MemoryRecord(content = fullResponse)))
                    }
                }
            }

            return memory2Feature
        }

        /**
         * Returns an augmented prompt only if there are relevant memory records for the last user's message.
         */
        private suspend fun getAugmentedPromptOrNull(prompt: Prompt, memory2Feature: Memory2): Prompt? {
            val retriever = memory2Feature.retriever ?: return null
            val lastUserMessage = prompt.messages.lastOrNull { it.role == Message.Role.User } ?: return null

            val searchResults = try {
                retriever.retrieve(memory2Feature.memoryRecordRepository, lastUserMessage.content)
            } catch (e: Exception) {
                logger.error(e) { "Failed to search memory records for $retriever." }
                emptyList()
            }
            val relevantContext = searchResults.records().map { it.content }

            if (relevantContext.isEmpty()) {
                return null
            }

            return PromptAugmenter.augmentPrompt(
                originalPrompt = prompt,
                relevantContext = relevantContext,
                contextInsertionMode = memory2Feature.contextInsertionMode,
                systemPromptTemplate = memory2Feature.systemPromptTemplate,
                userContextTemplate = memory2Feature.userContextTemplate
            )
        }

        override fun install(
            config: Config,
            pipeline: AIAgentGraphPipeline,
        ): Memory2 = createFeature(config, pipeline)

        override fun install(
            config: Config,
            pipeline: AIAgentFunctionalPipeline,
        ): Memory2 = createFeature(config, pipeline)
    }
}
