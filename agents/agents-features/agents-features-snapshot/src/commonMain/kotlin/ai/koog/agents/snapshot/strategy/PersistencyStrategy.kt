package ai.koog.agents.snapshot.strategy

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import kotlin.jvm.JvmInline

/**
 * Type-safe identifier for persistence providers.
 * Prevents runtime errors from string-based provider references.
 */
@JvmInline
public value class ProviderId(public val value: String) {
    override fun toString(): String = value
}

/**
 * Type-safe registry for managing persistence providers.
 * Provides discoverability and prevents runtime errors from invalid provider references.
 */
public class ProviderRegistry {
    private val providers = mutableMapOf<ProviderId, PersistencyStorageProvider>()
    
    /**
     * Registers a provider with the given ID.
     * @param provider The provider instance to register
     * @param id Custom ID for the provider, defaults to class simple name
     * @return The provider ID for type-safe references
     */
    public fun register(provider: PersistencyStorageProvider, id: String = provider::class.simpleName!!): ProviderId {
        val providerId = ProviderId(id)
        providers[providerId] = provider
        return providerId
    }
    
    /**
     * Retrieves a provider by its ID.
     * @param id The provider ID
     * @return The provider instance
     * @throws IllegalStateException if provider not found
     */
    public fun get(id: ProviderId): PersistencyStorageProvider = 
        providers[id] ?: error("Provider ${id.value} not found")
        
    /**
     * Gets all registered provider IDs.
     */
    public fun getProviderIds(): Set<ProviderId> = providers.keys.toSet()
    
    /**
     * Checks if a provider is registered.
     */
    public fun contains(id: ProviderId): Boolean = providers.containsKey(id)
}

/**
 * Defines HOW to coordinate operations across multiple providers.
 * 
 * This open interface allows for completely custom coordination logic,
 * enabling users to implement any coordination pattern they need.
 */
public interface CoordinationStrategy {
    /**
     * Saves a checkpoint using this coordination strategy.
     * 
     * @param checkpoint The checkpoint data to save
     * @param registry Registry for accessing providers
     */
    public suspend fun saveCheckpoint(checkpoint: AgentCheckpointData, registry: ProviderRegistry)
    
    /**
     * Retrieves all checkpoints using this coordination strategy.
     * 
     * @param registry Registry for accessing providers
     * @return List of all checkpoints
     */
    public suspend fun getCheckpoints(registry: ProviderRegistry): List<AgentCheckpointData>
    
    /**
     * Retrieves the latest checkpoint using this coordination strategy.
     * 
     * @param registry Registry for accessing providers
     * @return The latest checkpoint, or null if none exists
     */
    public suspend fun getLatestCheckpoint(registry: ProviderRegistry): AgentCheckpointData?
}

/**
 * Represents a strategy for determining WHICH coordination approach to use for agent checkpoints.
 * 
 * This enables flexible persistence configurations that can adapt to different use cases:
 * - Agent-specific routing based on characteristics
 * - Task-aware selection using LLM
 * - Fixed coordination patterns
 * - No persistence for testing
 */
public sealed interface PersistencyStrategy {
    /**
     * Provides no persistence functionality.
     * Useful for testing scenarios where persistence should be explicitly disabled.
     */
    public data object None : PersistencyStrategy

    /**
     * Uses a fixed coordination strategy for all agents.
     * This is the simplest approach when all agents have the same persistence needs.
     */
    public data class Fixed(val coordination: CoordinationStrategy) : PersistencyStrategy

    /**
     * Dynamically selects a coordination strategy based on the agent context.
     * 
     * This strategy allows for intelligent coordination selection based on:
     * - Agent context (ID, strategy, task characteristics)
     * - Runtime conditions (load, availability)
     * - Custom business logic
     * 
     * IMPORTANT: The selector function should return consistent coordination strategies
     * for the same agent to ensure data consistency.
     * 
     * @property selector Function that determines which coordination strategy to use
     */
    public data class Dynamic(
        val selector: suspend (AgentContext, ProviderRegistry) -> CoordinationStrategy
    ) : PersistencyStrategy {
        /**
         * Context provided to the selector function for making coordination decisions.
         * 
         * @property agentContext The current agent execution context
         * @property metadata Additional metadata that might influence coordination selection
         */
        public data class AgentContext(
            val agentContext: AIAgentContextBase,
            val metadata: Map<String, Any> = emptyMap()
        )
    }

    /**
     * LLM-driven strategy for intelligent coordination selection.
     * 
     * This strategy uses the LLM to determine the most appropriate coordination strategy based on:
     * - The current agent task description
     * - Available coordination options
     * - Provider descriptions (from @LLMDescription annotations)
     * 
     * The LLM chooses from a predefined set of coordination options rather than
     * arbitrary provider combinations, ensuring type safety and validation.
     * 
     * @property taskDescription Description of the current task/context for the LLM
     * @property options Available coordination strategies for the LLM to choose from
     * @property registry Provider registry for resolving provider references
     * @property maxRetries Maximum number of retries for LLM selection (default: 3)
     */
    public data class AutoSelectForTask(
        val taskDescription: String,
        val options: List<CoordinationStrategy>,
        val registry: ProviderRegistry,
        val maxRetries: Int = 3
    ) : PersistencyStrategy
}