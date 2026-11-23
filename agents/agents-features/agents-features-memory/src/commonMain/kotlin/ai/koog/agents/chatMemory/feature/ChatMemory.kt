package ai.koog.agents.chatMemory.feature

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
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
 *
 * The system prompt is owned by the live agent: when history is loaded it is restored after the
 * current agent's system messages and any system messages present in the loaded history are
 * ignored, so the agent's configured system prompt is never lost across turns. Use
 * [ChatMemoryConfig.dropSystemMessages] if you also want to keep system messages out of stored
 * history.
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
            createStorageKey<ChatMemory>("agents-features-chat-memory")

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
            pipeline.interceptStrategyStarting(this) { ctx ->
                val history = config.chatHistoryProvider.load(ctx.context.runId)
                val historyMessages = applyPreProcessors(history, config.preprocessors)

                ctx.context.llm.writeSession {
                    prompt = prompt.withMessages { currentMessages ->
                        if (historyMessages.isEmpty()) {
                            // First run (no stored history): keep the initial prompt intact,
                            // including the system prompt and any setup messages.
                            currentMessages
                        } else {
                            // Subsequent runs: the system prompt is owned by the live agent and
                            // always takes precedence. Keep the current system messages and drop
                            // any system messages carried in stored history, then append the rest
                            // of the conversation. Without this, replacing the prompt with history
                            // drops the agent's system prompt after the first turn.
                            val currentSystemMessages = currentMessages.filterIsInstance<Message.System>()
                            val historyWithoutSystem = historyMessages.filterNot { it is Message.System }
                            currentSystemMessages + historyWithoutSystem
                        }
                    }
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
