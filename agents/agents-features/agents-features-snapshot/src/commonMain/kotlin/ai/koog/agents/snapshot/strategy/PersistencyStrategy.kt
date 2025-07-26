package ai.koog.agents.snapshot.strategy

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider

/**
 * Represents a strategy for determining which persistence provider to use for agent checkpoints.
 *
 * This interface provides different configurations for persistence selection, from using a single
 * provider to dynamically selecting providers based on context or custom logic.
 *
 * This pattern allows flexible persistence configurations
 * that can adapt to different use cases:
 * - High-frequency checkpoints during execution → Redis
 * - Long-term session persistence → PostgreSQL/SQL
 * - Testing environments → In-memory providers
 * - Hybrid approaches → Context-aware selection
 */
public sealed interface PersistencyStrategy {
    /**
     * Uses a single persistence provider for all checkpoint operations.
     *
     * This is the simplest strategy, equivalent to the current behavior where one
     * provider handles all persistence needs.
     *
     * @property provider The single persistence provider to use for all operations
     */
    public data class Single(
        val provider: PersistencyStorageProvider
    ) : PersistencyStrategy

    /**
     * Provides no persistence functionality.
     *
     * This strategy is equivalent to using [NoPersistencyStorageProvider] and is useful
     * for scenarios where persistence should be explicitly disabled.
     */
    public data object None : PersistencyStrategy


    /**
     * Dynamically selects a provider based on the agent context.
     *
     * This strategy allows for intelligent provider selection based on:
     * - Agent context (strategy, task, criticality) 
     * - Checkpoint characteristics (agent type, expected duration)
     * - Custom business logic (environment, tenant, load balancing)
     *
     * IMPORTANT: The selector function should return the same provider for all operations
     * of a given agent to ensure data consistency. The provider is selected once per agent
     * session and used for all subsequent checkpoint operations.
     *
     * @property providers Map of provider names to their instances
     * @property selector Function that determines which provider to use for this agent
     */
    public data class Dynamic(
        val providers: Map<String, PersistencyStorageProvider>,
        val selector: suspend (AgentContext) -> String
    ) : PersistencyStrategy {
        /**
         * Context provided to the selector function for making provider decisions.
         *
         * This context focuses on agent-level characteristics rather than individual operations
         * to ensure all operations for an agent use the same provider consistently.
         *
         * @property agentContext The current agent execution context
         * @property metadata Additional metadata that might influence provider selection
         */
        public data class AgentContext(
            val agentContext: AIAgentContextBase,
            val metadata: Map<String, Any> = emptyMap()
        )

    }

    

    /**
     * LLM-driven strategy for intelligent provider selection.
     *
     * This strategy uses the LLM to determine the most appropriate persistence provider based on:
     * - The current operation context
     * - Checkpoint characteristics
     * - Provider descriptions (from @LLMDescription annotation)
     * - Task description
     *
     * Providers should be annotated with @LLMDescription for optimal selection:
     * ```kotlin
     * @LLMDescription("Fast in-memory cache with TTL support for ephemeral data")
     * class RedisProvider : PersistencyStorageProvider { ... }
     * 
     * @LLMDescription("Durable SQL database with ACID compliance for persistent storage")
     * class PostgresProvider : PersistencyStorageProvider { ... }
     * ```
     *
     * @property providers Map of provider names to their instances (descriptions via annotations)
     * @property taskDescription Description of the current task/context for the LLM
     * @property maxRetries Maximum number of retries for LLM selection (default: 3)
     */
    public data class AutoSelectForTask(
        val providers: Map<String, PersistencyStorageProvider>,
        val taskDescription: String,
        val maxRetries: Int = 3
    ) : PersistencyStrategy
}