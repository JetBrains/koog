package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.prompt.message.Message

/**
 */
public class EssentialAIAgentContext(
    override val environment: AIAgentEnvironment,
    override val agentId: String,
    override val runId: String,
    override val agentInput: Any?,
    override val config: AIAgentConfigBase,
    override val llm: AIAgentLLMContext,
    override val stateManager: AIAgentStateManager,
    override val storage: AIAgentStorage,
    override val strategyName: String
) : AIAgentContext {

    private val storeMap: MutableMap<AIAgentStorageKey<*>, Any> = mutableMapOf()

    override fun store(key: AIAgentStorageKey<*>, value: Any) {
        storeMap[key] = value
    }

    override fun <T> get(key: AIAgentStorageKey<*>): T? {
        @Suppress("UNCHECKED_CAST")
        return storeMap[key] as T?
    }

    override fun remove(key: AIAgentStorageKey<*>): Boolean {
        return storeMap.remove(key) != null
    }

    override fun <Feature : Any> feature(key: AIAgentStorageKey<Feature>): Feature? {
        // Essential context has no pipeline-installed features by default.
        // Users may store arbitrary values using store/get if needed.
        return null
    }

    override fun <Feature : Any> feature(feature: AIAgentFeature<*, Feature>): Feature? = feature(feature.key)

    override suspend fun getHistory(): List<Message> {
        return llm.readSession { prompt.messages }
    }
}
