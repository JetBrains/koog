package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentGraphPipeline
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.utils.RWLock
import ai.koog.prompt.message.Message
import kotlin.reflect.KType

/**
 * The `AIAgentGraphContextBase` interface extends the `AIAgentContextBase` interface
 * to provide a foundational context specifically tailored for AI agents operating
 * within a graph structure.
 *
 * This interface inherits the core capabilities from `AIAgentContextBase`, including
 * environment management, configuration access, session tracking, state management,
 * and custom workflows. By building upon these features, it serves as a base for
 * defining additional constructs and behaviors that facilitate the agent's execution
 * in graph-based workflows or execution pipelines.
 *
 * Implementations of this interface are expected to leverage the provided capabilities
 * to handle graph-specific logic, such as node traversal, input/output management,
 * and handling complex dependencies between graph nodes.
 */
public interface AIAgentGraphContextBase : AIAgentContext {
    /**
     * Represents the execution pipeline associated with the AI agent operating
     * in a graph-based context.
     *
     * This pipeline defines the sequence of steps, transformations, or processes
     * that the agent follows to perform its tasks. It enables structured execution
     * by managing dependencies, node traversal, and other graph-specific operations
     * essential for the agent's workflow.
     */
    public val pipeline: AIAgentGraphPipeline

    /**
     * [KType] representing the type of the [agentInput]
     */
    public val agentInputType: KType

    override val agent: GraphAIAgent<*, *>

    /**
     * Creates a copy of the current [AIAgentGraphContext], allowing for selective overriding of its properties.
     *
     * @param environment The [AIAgentEnvironment] to be used in the new context, or `null` to retain the current one.
     * @param config The [AIAgentConfig] for the new context, or `null` to retain the current configuration.
     * @param llm The [AIAgentLLMContext] to be used, or `null` to retain the current LLM context.
     * @param stateManager The [AIAgentStateManager] to be used, or `null` to retain the current state manager.
     * @param storage The [AIAgentStorage] to be used, or `null` to retain the current storage.
     * @param runId The run identifier, or `null` to retain the current run ID.
     * @param strategyName The strategy name, or `null` to retain the current identifier.
     * @param pipeline The [AIAgentPipeline] to be used, or `null` to retain the current pipeline.
     */
    public fun copy(
        environment: AIAgentEnvironment = this.environment,
        agentInput: Any? = this.agentInput,
        agentInputType: KType = this.agentInputType,
        config: AIAgentConfig = this.config,
        llm: AIAgentLLMContext = this.llm,
        stateManager: AIAgentStateManager = this.stateManager,
        storage: AIAgentStorage = this.storage,
        runId: String = this.runId,
        strategyName: String = this.strategyName,
        pipeline: AIAgentGraphPipeline = this.pipeline,
    ): AIAgentGraphContextBase {
        val clone = AIAgentGraphContext(
            environment = environment,
            agentInput = agentInput,
            agentInputType = agentInputType,
            config = config,
            llm = llm,
            stateManager = stateManager,
            storage = storage,
            runId = runId,
            strategyName = strategyName,
            pipeline = pipeline,
            agent = this.agent,
            parentContext = this
        )

        return clone
    }

    /**
     * Creates a copy of the current [AIAgentGraphContext] with deep copies of all mutable properties.
     *
     * @return A new instance of [AIAgentGraphContext] with copies of all mutable properties.
     */
    public suspend fun fork(): AIAgentGraphContextBase

    /**
     * Replaces the current context with the provided context.
     * This method is used to update the current context with values from another context,
     * particularly useful in scenarios like parallel node execution where contexts need to be merged.
     *
     * @param context The context to replace the current context with.
     */
    public suspend fun replace(context: AIAgentContext)
}

/**
 * Implements the [AIAgentGraphContext] interface, providing the context required for an AI agent's execution.
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
 * @param runId The unique identifier for the agent session.
 * @param strategyName The identifier for the selected strategy in the agent's lifecycle.
 * @param pipeline The AI agent pipeline responsible for coordinating AI agent execution and processing.
 */
@OptIn(InternalAgentsApi::class)
public class AIAgentGraphContext(
    override val environment: AIAgentEnvironment,
    override val agentInputType: KType,
    override val agentInput: Any?,
    override val config: AIAgentConfig,
    llm: AIAgentLLMContext,
    stateManager: AIAgentStateManager,
    storage: AIAgentStorage,
    override val runId: String,
    override val strategyName: String,
    override val pipeline: AIAgentGraphPipeline,
    override val agent: GraphAIAgent<*, *>,
    override val parentContext: AIAgentGraphContextBase? = null
) : AIAgentGraphContextBase {

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

    /**
     * Retrieves an instance of the specified feature from the current context.
     *
     * @param feature The feature representation, including its key and configuration details,
     *                for identifying and accessing the associated implementation.
     * @return The feature implementation of the specified type if available, or null if it is not present.
     */
    override fun <Feature : Any> feature(feature: AIAgentFeature<*, Feature>): Feature? = feature(feature.key)

    override suspend fun getHistory(): List<Message> {
        return llm.readSession {
            prompt.messages
        }
    }

    /**
     * Creates a new instance of [AIAgentContext] with an updated list of tools, replacing the current tools
     * in the LLM context with the provided list.
     *
     * @param tools The new list of tools to be used in the LLM context, represented as [ToolDescriptor] objects.
     * @return A new instance of [AIAgentContext] with the updated tools configuration.
     */
    @InternalAgentsApi
    public fun copyWithTools(tools: List<ToolDescriptor>): AIAgentContext {
        return this.copy(llm = llm.copy(tools = tools))
    }

    override suspend fun fork(): AIAgentGraphContextBase = copy(
        llm = this.llm.copy(),
        storage = this.storage.copy(),
        stateManager = this.stateManager.copy(),
    )

    override suspend fun replace(context: AIAgentContext) {
        mutableAIAgentContext.replace(
            context.llm,
            context.stateManager,
            context.storage
        )
    }
}

/**
 * A storage key used for associating and retrieving `AgentContextData` within the AI agent's storage system.
 *
 * This key is intended for internal use within the AI agents' infrastructure to securely store and access
 * data related to an agent's context. The associated data includes details such as message history, node identifiers,
 * and the last input processed by the agent, allowing seamless tracking and management of an agent's state.
 *
 * The storage key is marked with the `@InternalAgentsApi` annotation, indicating that it is part of the internal
 * mechanism and not meant for public or general-purpose development use. It may be subject to changes or removal
 * without notice.
 */
@OptIn(InternalAgentsApi::class)
public val agentContextDataAdditionalKey: AIAgentStorageKey<AgentContextData> =
    AIAgentStorageKey("agent-context-data-key")

/**
 * Stores the given agent context data within the current AI agent context.
 *
 * @param data The context-specific data to be stored for later retrieval or use within the agent context.
 */
@InternalAgentsApi
public fun AIAgentContext.store(data: AgentContextData) {
    this.rootContext().store(agentContextDataAdditionalKey, data)
}

/**
 * Retrieves the agent-specific context data associated with the current instance.
 *
 * This function accesses and returns the contextual information stored as part of the agent's context,
 * or null if no such data is present.
 *
 * Note: This is part of the internal agents API and should be used cautiously, understanding that
 * it is subject to changes or removal in future updates.
 *
 * @return The agent context data, or null if no context data is associated.
 */
@InternalAgentsApi
public fun AIAgentContext.getAgentContextData(): AgentContextData? {
    return this.rootContext().get(agentContextDataAdditionalKey)
}

/**
 * Removes the agent-specific context data associated with the current context.
 *
 * This function attempts to remove the context data identified by the `agentContextDataAdditionalKey`.
 *
 * @return `true` if the agent context data was successfully removed, or `false` if no data was found to remove.
 */
@OptIn(InternalAgentsApi::class)
public fun AIAgentContext.removeAgentContextData(): Boolean {
    return this.rootContext().remove(agentContextDataAdditionalKey)
}
