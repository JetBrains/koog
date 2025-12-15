package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.prompt.message.Message
import kotlin.reflect.KClass

/**
 * The [AIAgentContext] interface represents the context of an AI agent in the lifecycle.
 * It provides access to the environment, configuration, LLM context, state management, storage, and other
 * metadata necessary for the operation of the agent.
 * Additionally, it supports features for custom workflows and extensibility.
 */
public interface AIAgentContext {

    /**
     * Represents the environment in which the agent operates.
     *
     * This variable provides access to essential functionalities for the agent's execution,
     * including interaction with tools, error reporting, and sending termination signals.
     * It is used throughout the agent's lifecycle to facilitate actions and handle outcomes.
     */
    public val environment: AIAgentEnvironment

    /**
     * A unique identifier representing the current agent instance within the context.
     */
    public val agentId: String

    /**
     * Represents the pipeline associated with the AI agent.
     */
    public val pipeline: AIAgentPipeline

    /**
     * A unique identifier for the current session associated with the AI agent context.
     * Used to track and differentiate sessions within the execution of the agent pipeline.
     */
    public val runId: String

    /**
     * Represents the input provided to the agent's execution.
     *
     * This variable provides access to the agent's input, which can be used to
     * determine the agent's intent, context, or other relevant information at any stage of agents execution.
     */
    public val agentInput: Any?

    /**
     * Represents the configuration for an AI agent.
     *
     * This configuration is used during the execution to enforce constraints
     * such as the maximum number of iterations an agent can perform, as well as providing
     * the agent's prompt configuration.
     */
    public val config: AIAgentConfig

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
     * nodes and subgraphs of the AI agent's execution flow.
     *
     * The [stateManager] is utilized extensively in coordinating state changes, such as
     * tracking the number of iterations made by the agent and enforcing execution limits
     * or conditions. This aids in maintaining predictable and controlled behavior
     * of the agent during execution.
     */
    public val stateManager: AIAgentStateManager

    /**
     * Concurrent-safe key-value storage for an agent, used to manage and persist data within the context of
     *  the AI agent stage execution. The `storage` property provides a thread-safe mechanism for sharing
     * and storing data specific to the agent's operation.
     */
    public val storage: AIAgentStorage

    /**
     * Represents the name of the strategy being used in the current AI agent context.
     */
    public val strategyName: String

    /**
     * Represents the parent context of the AI Agent.
     */
    @InternalAgentsApi
    public val parentContext: AIAgentContext?

    /**
     * Represents the observability data associated with the AI Agent context.
     */
    public var executionInfo: AgentExecutionInfo

    /**
     * Stores a feature in the agent's storage using the specified key.
     *
     * @param key A uniquely identifying key of type `AIAgentStorageKey` used to store the feature.
     * @param value The feature to be stored, which can be of any type.
     */
    public fun store(key: AIAgentStorageKey<*>, value: Any)

    /**
     * Retrieves data from the agent's storage using the specified key.
     *
     * @param key A uniquely identifying key of type `AIAgentStorageKey` used to fetch the corresponding data.
     * @return The data associated with the provided key, or null if no matching data is found.
     */
    public fun <T> get(key: AIAgentStorageKey<*>): T?

    /**
     * Removes a feature or data associated with the specified key from the agent's storage.
     *
     * @param key A uniquely identifying key of type `AIAgentStorageKey` used to locate the data to be removed.
     * @return `true` if the data was successfully removed, or `false` if no data was associated with the provided key.
     */
    public fun remove(key: AIAgentStorageKey<*>): Boolean

    /**
     * Retrieves the history of messages exchanged during the agent's execution.
     */
    public suspend fun getHistory(): List<Message>
}

/**
 * Utility function to get [AIAgentContext.agentInput] and try to cast it to some expected type.
 *
 * @throws ClassCastException If agent input can't be cast to [T]
 */
public inline fun <reified T> AIAgentContext.agentInput(): T =
    agentInput as? T ?: throw ClassCastException("Can't cast agent input to ${T::class}. Agent input: $agentInput")

/**
 * Provides the root context of the current agent.
 * If the root context is not defined, this function defaults to returning the current instance.
 *
 * @return The root context of type [AIAgentContext], or the current instance if the root context is null.
 */
@OptIn(InternalAgentsApi::class)
public fun AIAgentContext.rootContext(): AIAgentContext = this.parentContext?.rootContext() ?: this

/**
 * Retrieves a feature from the [AIAgentContext.pipeline] associated with this context using the specified key.
 *
 * @param TFeature A feature implementation type.
 * @param feature A feature to fetch.
 * @param featureClass The [KClass] of the feature to be retrieved.
 * @return The feature associated with the provided key, or null if no matching feature is found.
 * @throws IllegalArgumentException if the specified [featureClass] does not correspond to a registered feature.
 */
public fun <TFeature : Any> AIAgentContext.feature(
    featureClass: KClass<TFeature>,
    feature: AIAgentFeature<*, TFeature>
): TFeature? = pipeline.feature(featureClass, feature)

/**
 * Retrieves a feature from the [AIAgentContext.pipeline] associated with this context using the specified key.
 *
 * @param feature A feature to fetch.
 * @return The feature associated with the provided key, or null if no matching feature is found.
 * @throws IllegalArgumentException if the specified [feature] does not correspond to a registered feature.
 */
public inline fun <reified TFeature : Any> AIAgentContext.feature(feature: AIAgentFeature<*, TFeature>): TFeature? =
    feature(TFeature::class, feature)

/**
 * Retrieves a feature from the [AIAgentContext.pipeline] associated with this context using the specified key or throws
 * an exception if it is not available.
 *
 * @param feature A feature to fetch.
 * @return The feature associated with the provided key
 * @throws IllegalStateException if the [TFeature] feature does not correspond to a registered feature.
 * @throws NoSuchElementException if the feature is not found.
 */
public inline fun <reified TFeature : Any> AIAgentContext.featureOrThrow(feature: AIAgentFeature<*, TFeature>): TFeature =
    feature(feature) ?: throw NoSuchElementException("Feature ${feature.key} is not found.")

/**
 * Executes a block of code with a modified execution context.
 *
 * @param T The return type of the block being executed.
 * @param executionInfo The execution info to be set for the context.
 * @param block The suspend function to execute with the modified execution context.
 * @return The result of executing the provided block.
 */
public inline fun <T> AIAgentContext.with(executionInfo: AgentExecutionInfo, block: (executionInfo: AgentExecutionInfo) -> T): T {
    val originalExecutionInfo = this.executionInfo
    return try {
        this.executionInfo = executionInfo
        block(executionInfo)
    } finally {
        this.executionInfo = originalExecutionInfo
    }
}

/**
 * Executes a block of code with a modified execution context, creating a parent-child relationship
 * between execution contexts for tracing purposes.
 *
 * @param T The return type of the block being executed.
 * @param partName The name of the execution part to append to the execution path.
 * @param block The suspend function to execute with the modified execution context.
 * @return The result of executing the provided block.
 */
public inline fun <T> AIAgentContext.with(partName: String, block: (executionInfo: AgentExecutionInfo) -> T): T {
    val executionInfo = AgentExecutionInfo(parent = this.executionInfo, partName = partName)
    return with(executionInfo = executionInfo, block = block)
}
