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
     * Uses multiple providers simultaneously with configurable read/write strategies.
     *
     * This strategy enables advanced patterns like:
     * - Write-through caching (write to both fast and durable, read fast first)
     * - Backup/redundancy (write to multiple providers for safety)
     * - Performance optimization (read from fastest provider, fallback to others)
     *
     * @property providers Map of provider names to their instances
     * @property writeStrategy Defines which providers to write to and error handling
     * @property readStrategy Defines the order and fallback behavior for reads
     */
    public data class MultiProvider(
        val providers: Map<String, PersistencyStorageProvider>,
        val writeStrategy: WriteStrategy,
        val readStrategy: ReadStrategy
    ) : PersistencyStrategy {
        
        /**
         * Defines how writes are distributed across providers.
         */
        public sealed interface WriteStrategy {
            /**
             * Write to all specified providers. Fails if any provider fails.
             */
            public data class WriteToAll(val providerNames: List<String>) : WriteStrategy
            
            /**
             * Write to all specified providers. Succeeds if at least one provider succeeds.
             */
            public data class WriteToAllBestEffort(val providerNames: List<String>) : WriteStrategy
            
            /**
             * Write to primary provider, then backup providers. Succeeds if primary succeeds.
             */
            public data class WriteWithBackup(
                val primary: String,
                val backups: List<String> = emptyList()
            ) : WriteStrategy
        }
        
        /**
         * Defines how reads are performed across providers.
         */
        public sealed interface ReadStrategy {
            /**
             * Try providers in the specified order, return first successful result.
             */
            public data class Prioritized(val providerNames: List<String>) : ReadStrategy
            
            /**
             * Read from primary provider only.
             */
            public data class PrimaryOnly(val primary: String) : ReadStrategy
            
            /**
             * Try fastest provider first, fallback to others if needed.
             */
            public data class FastestFirst(
                val fast: String,
                val fallbacks: List<String>
            ) : ReadStrategy
        }
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