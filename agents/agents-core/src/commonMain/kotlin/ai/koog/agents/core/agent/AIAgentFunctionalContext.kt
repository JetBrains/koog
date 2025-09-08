package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentNonGraphPipeline
import ai.koog.prompt.message.Message

/**
 * Represents the execution context for an AI agent operating in a loop.
 * It provides access to critical components such as the environment, configuration, large language model (LLM) context,
 * state management, and storage. Additionally, it enables the agent to store, retrieve, and manage context-specific data
 * during its execution lifecycle.
 *
 * @property environment The environment interface allowing the agent to interact with the external world,
 * including executing tools and reporting problems.
 * @property agentId A unique identifier for the agent, differentiating it from other agents in the system.
 * @property runId A unique identifier for the current run or instance of the agent's operation.
 * @property agentInput The input data passed to the agent, which can be of any type, depending on the agent's context.
 * @property config The configuration settings for the agent, including its prompt and model details,
 * as well as operational constraints like iteration limits.
 * @property llm The context for interacting with the large language model used by the agent, enabling message history
 * retrieval and processing.
 * @property stateManager The state management component responsible for tracking and updating the agent's state during its execution.
 * @property storage A storage interface providing persistent storage capabilities for the agent's data.
 * @property strategyName The name of the agent's strategic approach or operational method, determining its behavior
 * during execution.
 */
@Suppress("UNCHECKED_CAST")
public class AIAgentFunctionalContext(
    override val environment: AIAgentEnvironment,
    override val agentId: String,
    override val runId: String,
    override val agentInput: Any?,
    override val config: AIAgentConfigBase,
    override val llm: AIAgentLLMContext,
    override val stateManager: AIAgentStateManager,
    override val storage: AIAgentStorage,
    override val strategyName: String,
    public val pipeline: AIAgentNonGraphPipeline
) : AIAgentContext {

    private val storeMap: MutableMap<AIAgentStorageKey<*>, Any> = mutableMapOf()

    override fun store(key: AIAgentStorageKey<*>, value: Any) {
        storeMap[key] = value
    }

    override fun <T> get(key: AIAgentStorageKey<*>): T? = storeMap[key] as T?

    override fun remove(key: AIAgentStorageKey<*>): Boolean = storeMap.remove(key) != null

    @OptIn(InternalAgentsApi::class)
    private val features: Map<AIAgentStorageKey<*>, Any> =
        pipeline.getAgentFeatures(this)

    override fun <Feature : Any> feature(key: AIAgentStorageKey<Feature>): Feature? {
        @Suppress("UNCHECKED_CAST")
        return features[key] as Feature?
    }

    override fun <Feature : Any> feature(feature: AIAgentFeature<*, Feature>): Feature? = feature(feature.key)

    override suspend fun getHistory(): List<Message> {
        return llm.readSession { prompt.messages }
    }
}
