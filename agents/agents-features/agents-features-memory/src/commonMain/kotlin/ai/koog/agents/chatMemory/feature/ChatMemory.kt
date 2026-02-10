package ai.koog.agents.chatMemory.feature

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentPlannerFeature
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPlannerPipeline
import ai.koog.prompt.message.Message

/**
 * A feature that allows storing and loading conversation history between an agent and a user.
 *
 * ChatMemory enables agents to persist and retrieve past conversations, allowing for
 * continuity across multiple agent sessions.
 *
 * Example usage:
 * ```kotlin
 * val agent = AIAgent(...) {
 *     installChatMemory {
 *         chatHistoryProvider = MyChatHistoryProvider()
 *     }
 * }
 * ```
 *
 * Example with a sliding window to limit the number of stored messages:
 * ```kotlin
 * val agent = AIAgent(...) {
 *     installChatMemory {
 *         chatHistoryProvider = MyChatHistoryProvider()
 *         windowSize(20) // keep only the last 20 messages
 *     }
 * }
 * ```
 */
public class ChatMemory {

    /**
     * Companion object implementing agent feature, handling [ChatMemory] creation and installation.
     */
    public companion object Feature :
        AIAgentGraphFeature<ChatMemoryConfig, ChatMemory>,
        AIAgentFunctionalFeature<ChatMemoryConfig, ChatMemory>,
        AIAgentPlannerFeature<ChatMemoryConfig, ChatMemory> {

        override val key: AIAgentStorageKey<ChatMemory> =
            AIAgentStorageKey("agents-features-chat-memory")

        override fun createInitialConfig(
            agentConfig: AIAgentConfig,
        ): ChatMemoryConfig = ChatMemoryConfig()

        override fun install(
            config: ChatMemoryConfig,
            pipeline: AIAgentGraphPipeline,
        ): ChatMemory {
            val chatMemory = ChatMemory()
            installInternal(config, pipeline)
            return chatMemory
        }

        override fun install(
            config: ChatMemoryConfig,
            pipeline: AIAgentFunctionalPipeline,
        ): ChatMemory {
            val chatMemory = ChatMemory()
            installInternal(config, pipeline)
            return chatMemory
        }

        override fun install(
            config: ChatMemoryConfig,
            pipeline: AIAgentPlannerPipeline
        ): ChatMemory {
            val chatMemory = ChatMemory()
            installInternal(config, pipeline)
            return chatMemory
        }

        private fun applyPreProcessors(
            messages: List<Message>,
            preProcessors: List<ChatMemoryPreProcessor>,
        ): List<Message> {
            return preProcessors.fold(messages) { acc, processor -> processor.preprocess(acc) }
        }

        private fun installInternal(config: ChatMemoryConfig, pipeline: AIAgentPipeline) {
            pipeline.interceptStrategyStarting(this) {
                val history = config.chatHistoryProvider.load(it.context.runId)
                val processed = applyPreProcessors(history, config.preprocessors)

                it.context.llm.writeSession {
                    prompt = prompt.withMessages { processed }
                }
            }

            pipeline.interceptStrategyCompleted(this) {
                val history = it.context.llm.prompt.messages
                val processed = applyPreProcessors(history, config.preprocessors)
                config.chatHistoryProvider.store(it.context.runId, processed)
            }
        }
    }
}
