package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.permissions.*
import ai.koog.agents.core.utils.RWLock
import ai.koog.prompt.message.Message
import kotlin.reflect.KType

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
 * @param runId The unique identifier for the agent session.
 * @param strategyName The identifier for the selected strategy in the agent's lifecycle.
 * @param pipeline The AI agent pipeline responsible for coordinating AI agent execution and processing.
 */
public class AIAgentContext(
    override val environment: AIAgentEnvironment,
    override val agentInputType: KType,
    override val agentInput: Any?,
    override val config: AIAgentConfigBase,
    llm: AIAgentLLMContext,
    stateManager: AIAgentStateManager,
    storage: AIAgentStorage,
    override val runId: String,
    override val strategyName: String,
    @OptIn(InternalAgentsApi::class)
    override val pipeline: AIAgentPipeline,
    override val agentId: String,
    runtimeRoles: Set<Role>? = null,
) : AIAgentContextBase {

    /**
     * Mutable wrapper for AI agent context properties.
     */
    internal class MutableAIAgentContext(
        var llm: AIAgentLLMContext,
        var stateManager: AIAgentStateManager,
        var storage: AIAgentStorage,
        var runtimeRoles: Set<Role>? = null,
    ) {
        private val rwLock = RWLock()

        /**
         * Creates a copy of the current [MutableAIAgentContext].
         * @return A new instance of [MutableAIAgentContext] with copies of all mutable properties.
         */
        suspend fun copy(): MutableAIAgentContext {
            return rwLock.withReadLock {
                MutableAIAgentContext(llm.copy(), stateManager.copy(), storage.copy(), runtimeRoles)
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
                // Note: runtimeRoles is not replaced here to preserve it across context replacements
            }
        }

        /**
         * Sets the runtime roles for this context.
         * @param roles The roles to use at runtime, overriding the configured role
         */
        suspend fun setRuntimeRoles(roles: Set<Role>?) {
            rwLock.withWriteLock {
                this.runtimeRoles = roles
            }
        }

        /**
         * Gets the current runtime roles.
         * @return The runtime roles if set, null otherwise
         */
        suspend fun getRuntimeRoles(): Set<Role>? {
            return rwLock.withReadLock {
                this.runtimeRoles
            }
        }
    }

    private val mutableAIAgentContext = MutableAIAgentContext(llm, stateManager, storage, runtimeRoles)

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
     * Retrieves a feature associated with the given key from the current context.
     *
     * @param key The key of the feature to retrieve.
     * @return The feature associated with the specified key, or null if no such feature exists.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <Feature : Any> feature(key: AIAgentStorageKey<Feature>): Feature? = features[key] as Feature?

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
     * Creates a new instance of [AIAgentContextBase] with an updated list of tools, replacing the current tools
     * in the LLM context with the provided list.
     *
     * @param tools The new list of tools to be used in the LLM context, represented as [ToolDescriptor] objects.
     * @return A new instance of [AIAgentContextBase] with the updated tools configuration.
     */
    @InternalAgentsApi
    override fun copyWithTools(tools: List<ToolDescriptor>): AIAgentContextBase {
        return this.copy(llm = llm.copy(tools = tools))
    }

    /**
     * Creates a copy of the current [AIAgentContext], allowing for selective overriding of its properties.
     *
     * @param environment The [AIAgentEnvironment] to be used in the new context, or `null` to retain the current one.
     * @param config The [AIAgentConfigBase] for the new context, or `null` to retain the current configuration.
     * @param llm The [AIAgentLLMContext] to be used, or `null` to retain the current LLM context.
     * @param stateManager The [AIAgentStateManager] to be used, or `null` to retain the current state manager.
     * @param storage The [AIAgentStorage] to be used, or `null` to retain the current storage.
     * @param runId The run Id, or `null` to retain the current run ID.
     * @param strategyName The strategy identifier, or `null` to retain the current identifier.
     * @param pipeline The [AIAgentPipeline] to be used, or `null` to retain the current pipeline.
     */
    override fun copy(
        environment: AIAgentEnvironment,
        agentInput: Any?,
        agentInputType: KType,
        config: AIAgentConfigBase,
        llm: AIAgentLLMContext,
        stateManager: AIAgentStateManager,
        storage: AIAgentStorage,
        runId: String,
        strategyName: String,
        pipeline: AIAgentPipeline,
    ): AIAgentContextBase = AIAgentContext(
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
        agentId = this.agentId,
        runtimeRoles = this.cachedRuntimeRoles,
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

    private val permissionChecker: PermissionChecker = StandardPermissionChecker.Default

    override fun hasPermission(metadata: PermissionMetadata): Boolean {
        val agentConfig = config as? AIAgentConfig ?: return true // No AIAgentConfig = no restrictions
        val roleHierarchy = agentConfig.roleHierarchy ?: return true
        val effectiveRole = currentRoles?.firstOrNull() ?: return true // Use first role from currentRoles

        // Convert metadata to ToolPolicy for checking
        val minRole = metadata.minimumRole
        val toolPolicy = when {
            minRole != null -> ToolPolicy(
                roleRequirement = RoleRequirement.MinimumRole(minRole)
            )
            metadata.requiredRoles.isNotEmpty() -> ToolPolicy(
                roleRequirement = RoleRequirement.AllowedRoles(metadata.requiredRoles)
            )
            else -> ToolPolicy(
                roleRequirement = RoleRequirement.None
            )
        }

        // Get all roles the current role has (including inherited)
        val allRoles = roleHierarchy.getAllRoles()
        val agentRoles = setOf(effectiveRole) + allRoles.values.filter {
            effectiveRole.hasRole(it)
        }.toSet()

        // Check permission
        return when (permissionChecker.checkPermission(agentRoles, toolPolicy)) {
            is PermissionCheckResult.Granted -> true
            is PermissionCheckResult.Denied -> false
        }
    }

    override fun hasRole(role: Role): Boolean {
        val effectiveRoles = currentRoles ?: return false
        return effectiveRoles.any { it.hasRole(role) }
    }

    // Cache the runtime roles to avoid suspend property
    private var cachedRuntimeRoles: Set<Role>? = runtimeRoles

    override val currentRoles: Set<Role>?
        get() = cachedRuntimeRoles

    override val roleHierarchy: RoleHierarchy?
        get() = (config as? AIAgentConfig)?.roleHierarchy

    /**
     * Sets the runtime roles for this context, overriding the configured role.
     * This allows agents to execute with different roles without creating new instances.
     *
     * @param roles The roles to use for this execution, or null to use the configured role
     */
    public suspend fun setRuntimeRoles(roles: Set<Role>?) {
        mutableAIAgentContext.setRuntimeRoles(roles)
        cachedRuntimeRoles = roles
    }
    override fun getPermissionDenialReason(requiredMetadata: PermissionMetadata): String {
        val roles = currentRoles ?: return "No role assigned."
        val primaryRole = roles.firstOrNull() ?: return "No role assigned."

        return when {
            requiredMetadata.minimumRole != null && !hasRole(requiredMetadata.minimumRole!!) ->
                "This action requires ${requiredMetadata.minimumRole!!.name} role, but you have ${primaryRole.name} role."

            requiredMetadata.requiredRoles.isNotEmpty() ->
                "This action requires one of these roles: ${requiredMetadata.requiredRoles.joinToString { it.name }}, but you have ${primaryRole.name} role."

            else ->
                "You don't have permission to perform this action."
        }
    }
}

/**
 * A storage key utilized for associating and retrieving `AgentContextData` within the AI agent's storage system.
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
public fun AIAgentContextBase.store(data: AgentContextData) {
    this.store(agentContextDataAdditionalKey, data)
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
public fun AIAgentContextBase.getAgentContextData(): AgentContextData? {
    return this.get(agentContextDataAdditionalKey)
}

/**
 * Removes the agent-specific context data associated with the current context.
 *
 * This function attempts to remove the context data identified by the `agentContextDataAdditionalKey`.
 *
 * @return `true` if the agent context data was successfully removed, or `false` if no data was found to remove.
 */
@OptIn(InternalAgentsApi::class)
public fun AIAgentContextBase.removeAgentContextData(): Boolean {
    return this.remove(agentContextDataAdditionalKey)
}
