package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.utils.RWLock

/**
 * Implements the [AIAgentContext] interface, providing the context required for an AI agent's execution.
 * This class encapsulates configurations, the execution pipeline,
 * agent environment, and tools for handling agent lifecycles and interactions.
 *
 * @constructor Creates an instance of the context with the given parameters.
 *
 * @param environment The AI agent environment responsible for tool execution and problem reporting.
 * @param agentInput The input message to be used for the agent's interaction with the environment.
 * @param config The configuration settings of the AI agent.
 * @param llm The contextual data and execution utilities for the AI agent's interaction with LLMs.
 * @param stateManager Manages the internal state of the AI agent.
 * @param storage Concurrent-safe storage for managing key-value data across the agent's lifecycle.
 * @param sessionId The unique identifier for the agent session.
 * @param strategyId The identifier for the selected strategy in the agent's lifecycle.
 * @param pipeline The AI agent pipeline responsible for coordinating AI agent execution and processing.
 */
public class AIAgentContext(
    override val environment: AIAgentEnvironment,
    override val agentInput: Any?,
    override val config: AIAgentConfigBase,
    llm: AIAgentLLMContext,
    stateManager: AIAgentStateManager,
    storage: AIAgentStorage,
    override val sessionId: String,
    override val strategyId: String,
    @OptIn(InternalAgentsApi::class)
    override val pipeline: AIAgentPipeline,
) : AIAgentContextBase {

    /**
     * Mutable wrapper for AI agent context properties.
     */
    internal class MutableAIAgentContext(
        var llm: AIAgentLLMContext,
        var stateManager: AIAgentStateManager,
        var storage: AIAgentStorage,
    ) {
        private val rwLock = RWLock()

        /**
         * Creates a copy of the current [MutableAIAgentContext].
         * @return A new instance of [MutableAIAgentContext] with copies of all mutable properties.
         */
        suspend fun copy(): MutableAIAgentContext {
            return rwLock.withReadLock {
                MutableAIAgentContext(llm.copy(), stateManager.copy(), storage.copy())
            }
        }

        /**
         * Replaces the current context with the provided context.
         * @param llm The LLM context to replace the current context with.
         * @param stateManager The state manager to replace the current context with.
         * @param storage The storage to replace the current context with.
         */
        suspend fun replace(llm: AIAgentLLMContext?, stateManager: AIAgentStateManager?, storage: AIAgentStorage?) {
            rwLock.withWriteLock {
                llm?.let { this.llm = llm }
                stateManager?.let { this.stateManager = stateManager }
                storage?.let { this.storage = storage }
            }
        }
    }

    private val mutableAIAgentContext = MutableAIAgentContext(llm, stateManager, storage)

    override val llm: AIAgentLLMContext
        get() = mutableAIAgentContext.llm

    override val storage: AIAgentStorage
        get() = mutableAIAgentContext.storage

    override val stateManager: AIAgentStateManager
        get() = mutableAIAgentContext.stateManager

    /**
     * A map storing features associated with the current AI agent context.
     * The keys represent unique identifiers for specific features, defined as [AIAgentStorageKey].
     * The values are the features themselves, which can be of any type.
     *
     * This map is populated by invoking the [AIAgentPipeline.getAgentFeatures] method, retrieving features
     * based on the handlers registered for the AI agent's execution context.
     *
     * Used internally to manage and access features during the execution of the AI agent pipeline.
     */
    @OptIn(InternalAgentsApi::class)
    private val features: Map<AIAgentStorageKey<*>, Any> =
        pipeline.getAgentFeatures(this)

    /**
     * Retrieves a feature associated with the given key from the AI agent storage.
     *
     * @param key The key of the feature to retrieve.
     * @return The feature associated with the specified key, or null if no such feature exists.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <Feature : Any> feature(key: AIAgentStorageKey<Feature>): Feature? = features[key] as Feature?

    /**
     * Retrieves an instance of the specified feature from the AI agent's storage.
     *
     * @param feature The feature representation, including its key and configuration details,
     *                for identifying and accessing the associated implementation.
     * @return The feature implementation of the specified type if available, or null if it is not present.
     */
    override fun <Feature : Any> feature(feature: AIAgentFeature<*, Feature>): Feature? = feature(feature.key)

    /**
     * Creates a copy of the current [AIAgentContext], allowing for selective overriding of its properties.
     *
     * @param environment The [AIAgentEnvironment] to be used in the new context, or `null` to retain the current one.
     * @param config The [AIAgentConfigBase] for the new context, or `null` to retain the current configuration.
     * @param llm The [AIAgentLLMContext] to be used, or `null` to retain the current LLM context.
     * @param stateManager The [AIAgentStateManager] to be used, or `null` to retain the current state manager.
     * @param storage The [AIAgentStorage] to be used, or `null` to retain the current storage.
     * @param sessionId The session UUID, or `null` to retain the current session ID.
     * @param strategyId The strategy identifier, or `null` to retain the current identifier.
     * @param pipeline The [AIAgentPipeline] to be used, or `null` to retain the current pipeline.
     */
    override fun copy(
        environment: AIAgentEnvironment,
        agentInput: Any?,
        config: AIAgentConfigBase,
        llm: AIAgentLLMContext,
        stateManager: AIAgentStateManager,
        storage: AIAgentStorage,
        sessionId: String,
        strategyId: String,
        pipeline: AIAgentPipeline,
    ): AIAgentContextBase = AIAgentContext(
        environment = environment,
        agentInput = agentInput,
        config = config,
        llm = llm,
        stateManager = stateManager,
        storage = storage,
        sessionId = sessionId,
        strategyId = strategyId,
        pipeline = pipeline,
    )

    /**
     * Creates a copy of the current [AIAgentContext] with deep copies of all mutable properties.
     *
     * @return A new instance of [AIAgentContext] with copies of all mutable properties.
     */
    override suspend fun fork(): AIAgentContextBase = copy(
        llm = this.llm.copy(),
        storage = this.storage.copy(),
        stateManager = this.stateManager.copy(),
    )

    /**
     * Replaces the current context with the provided context.
     * This method is used to update the current context with values from another context,
     * particularly useful in scenarios like parallel node execution where contexts need to be merged.
     *
     * @param context The context to replace the current context with.
     */
    override suspend fun replace(context: AIAgentContextBase) {
        mutableAIAgentContext.replace(
            context.llm,
            context.stateManager,
            context.storage
        )
    }
}
