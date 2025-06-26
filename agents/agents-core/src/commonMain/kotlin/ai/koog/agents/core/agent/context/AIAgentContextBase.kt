package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.tools.ToolDescriptor
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The [AIAgentContextBase] interface represents the context of an AI agent in the lifecycle.
 * It provides access to the environment, configuration, LLM context, state management, storage, and other
 * metadata necessary for the operation of the agent.
 * Additionally, it supports features for custom workflows and extensibility.
 */
@OptIn(ExperimentalUuidApi::class)
public interface AIAgentContextBase {
    /**
     * Represents the environment in which the agent operates.
     *
     * This variable provides access to essential functionalities for the agent's execution,
     * including interaction with tools, error reporting, and sending termination signals.
     * It is used throughout the agent's lifecycle to facilitate actions and handle outcomes.
     */
    public val environment: AIAgentEnvironment

    /**
     * Represents the input provided to the agent's execution.
     *
     * This variable provides access to the agent's input, which can be used to
     * determine the agent's intent, context, or other relevant information at any stage of agents execution.
     *
     * @see [AIAgentEnvironment.input]
     */
    public val agentInput: String

    /**
     * Represents the configuration for an AI agent.
     *
     * This configuration is utilized during the execution to enforce constraints
     * such as the maximum number of iterations an agent can perform, as well as providing
     * the agent's prompt configuration.
     */
    public val config: AIAgentConfigBase

    /**
     * Represents the AI agent's LLM context, providing mechanisms for managing tools, prompts,
     * and interaction with the execution environment. It ensures thread safety during concurrent read and write
     * operations through the use of sessions.
     *
     * This context plays a foundational role in defining and manipulating tools, prompt execution, and overall
     * behavior the agent's lifecycle.
     */
    public val llm: AIAgentLLMContext

    /**
     * Manages and tracks the state of aт AI agent within the context of its execution.
     *
     * This variable provides synchronized access to the agent's state to ensure thread safety
     * and consistent state transitions during concurrent operations. It acts as a central
     * mechanism for managing state updates and validations across different
     * nodes and subgraphes of the AI agent's execution flow.
     *
     * The [stateManager] is utilized extensively in coordinating state changes, such as
     * tracking the number of iterations made by the agent and enforcing execution limits
     * or conditions. This aids in maintaining predictable and controlled behavior
     * of the agent during execution.
     */
    public val stateManager: AIAgentStateManager

    /**
     * Concurrent-safe key-value storage for an agent, used to manage and persist data within the context of
     * a the AI agent stage execution. The `storage` property provides a thread-safe mechanism for sharing
     * and storing data specific to the agent's operation.
     */
    public val storage: AIAgentStorage

    /**
     * A unique identifier for the current session associated with the AI agent context.
     * Used to track and differentiate sessions within the execution of the agent pipeline.
     */
    public val sessionUuid: Uuid

    /**
     * Represents the unique identifier for the strategy being used in the current AI agent context.
     *
     * This identifier allows the system to specify and reference a particular strategy
     * employed during the execution pipeline of an AI agent. It can be used
     * for logging, debugging, and switching between different strategies dynamically.
     */
    public val strategyId: String

    /**
     * Represents the AI agent pipeline used within an AI agent context.
     *
     * This pipeline organizes and processes the sequence of operations required
     * for the execution of an AI agent's tasks.
     *
     * Note: This is an internal API and should not be used directly outside of the intended
     * implementation context. It is annotated with `@InternalAgentsApi` to indicate that
     * it is subject to changes or alterations in future releases.
     *
     * @suppress
     */
    @InternalAgentsApi
    public val pipeline: AIAgentPipeline


    /**
     * Retrieves a feature from the agent's storage using the specified key.
     *
     * @param key A uniquely identifying key of type `AIAgentStorageKey` used to fetch the corresponding feature.
     * @return The feature associated with the provided key, or null if no matching feature is found.
     */
    public fun <Feature : Any> feature(key: AIAgentStorageKey<Feature>): Feature?

    /**
     * Retrieves a feature of the specified type from the current context.
     *
     * @param feature The [AIAgentFeature] instance representing the feature to retrieve.
     *                This parameter defines the configuration and unique identity of the feature.
     * @return The feature instance of type [Feature], or null if the feature is not available in the context.
     */
    public fun <Feature : Any> feature(feature: AIAgentFeature<*, Feature>): Feature?


    /**
     * Retrieves a feature of the specified type from the context or throws an exception if it is not available.
     *
     * @param feature The [AIAgentFeature] defining the specific feature to be retrieved. This provides
     *                the configuration and unique identification of the feature.
     * @return The instance of the requested feature of type [Feature].
     * @throws IllegalStateException if the requested feature is not installed in the agent.
     */
    public fun <Feature : Any> featureOrThrow(feature: AIAgentFeature<*, Feature>): Feature =
        feature(feature)
            ?: throw IllegalStateException("Feature `${feature::class.simpleName}` is not installed to the agent")

    /**
     * Creates a copy of the current [AIAgentContext] with optional overrides for its properties.
     *
     * @param environment The agent environment to be used
     * @param agentInput The agent input to be used
     * @param config The AI agent configuration
     * @param llm The AI agent LLM context
     * @param stateManager The state manager for the AI agent
     * @param storage The AI agent's key-value storage
     * @param sessionUuid The UUID of the session
     * @param strategyId The strategy ID
     * @param pipeline The AI agent pipeline
     * @return A new instance of [AIAgentContext] with the specified overrides.
     */
    @OptIn(InternalAgentsApi::class)
    public fun copy(
        environment: AIAgentEnvironment = this.environment,
        agentInput: String = this.agentInput,
        config: AIAgentConfigBase = this.config,
        llm: AIAgentLLMContext = this.llm,
        stateManager: AIAgentStateManager = this.stateManager,
        storage: AIAgentStorage = this.storage,
        sessionUuid: Uuid = this.sessionUuid,
        strategyId: String = this.strategyId,
        pipeline: AIAgentPipeline = this.pipeline,
    ): AIAgentContextBase

    /**
     * Creates a copy of the current [AIAgentContext] with deep copies of all mutable properties.
     * This method is particularly useful in scenarios like parallel node execution
     * where contexts need to be sent to different threads and then merged back together using [replace].
     *
     * @return A new instance of [AIAgentContext] with copies of all mutable properties.
     */
    public suspend fun fork(): AIAgentContextBase

    /**
     * Replaces the current context with the provided context.
     * This method is used to update the current context with values from another context,
     * particularly useful in scenarios like parallel node execution where contexts need to be merged.
     *
     * @param context The context to replace the current context with.
     */
    public suspend fun replace(context: AIAgentContextBase)
}
