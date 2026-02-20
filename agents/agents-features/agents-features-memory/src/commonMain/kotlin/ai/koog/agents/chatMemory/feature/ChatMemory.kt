package ai.koog.agents.chatMemory.feature

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline

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
 *         windowSize = 20 // keep only the last 20 messages
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
        AIAgentFunctionalFeature<ChatMemoryConfig, ChatMemory> {

        override val key: AIAgentStorageKey<ChatMemory> =
            AIAgentStorageKey("agents-features-chat-memory")

        override fun createInitialConfig(): ChatMemoryConfig = ChatMemoryConfig()

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

        private fun installInternal(config: ChatMemoryConfig, pipeline: AIAgentPipeline) {
            pipeline.interceptStrategyStarting(this) {
                val history = config.chatHistoryProvider.load(it.context.runId)
                val windowed = config.windowSize?.let { size -> history.takeLast(size) } ?: history

                it.context.llm.writeSession {
                    prompt = prompt.withMessages { windowed }
                }
            }

            pipeline.interceptStrategyCompleted(this) {
                val history = it.context.llm.prompt.messages
                val windowed = config.windowSize?.let { size -> history.takeLast(size) } ?: history
                config.chatHistoryProvider.store(it.context.runId, windowed)
            }
        }
    }
}

/**
 * Installs the [ChatMemory] feature and configures conversation history storage for an agent.
 *
 * @param configure A lambda with a receiver that configures the [ChatMemoryConfig].
 */
public fun FeatureContext.installChatMemory(configure: ChatMemoryConfig.() -> Unit = {}) {
    install(ChatMemory) {
        configure()
    }
}
