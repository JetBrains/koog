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
 * Similar to [ToolSelectionStrategy], this pattern allows flexible persistence configurations
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
     * Uses multiple providers with failover capability.
     *
     * Attempts to use providers in order, falling back to the next if an operation fails.
     * This provides resilience against individual provider failures.
     *
     * @property providers Ordered list of providers to attempt, from highest to lowest priority
     */
    public data class Failover(
        val providers: List<PersistencyStorageProvider>
    ) : PersistencyStrategy

    /**
     * Dynamically selects a provider based on the operation context.
     *
     * This strategy allows for intelligent provider selection based on:
     * - Operation type (save vs retrieve)
     * - Checkpoint characteristics (size, frequency)
     * - Agent context (strategy, node, criticality)
     * - Custom business logic
     *
     * @property providers Map of provider names to their instances
     * @property selector Function that determines which provider to use for each operation
     */
    public data class Dynamic(
        val providers: Map<String, PersistencyStorageProvider>,
        val selector: suspend (OperationContext) -> String
    ) : PersistencyStrategy {
        /**
         * Context provided to the selector function for making provider decisions.
         *
         * @property operation The type of persistence operation being performed
         * @property agentContext The current agent execution context
         * @property checkpoint Optional checkpoint data (for save operations)
         * @property metadata Additional metadata that might influence provider selection
         */
        public data class OperationContext(
            val operation: Operation,
            val agentContext: AIAgentContextBase,
            val checkpoint: AgentCheckpointData? = null,
            val metadata: Map<String, Any> = emptyMap()
        )

        /**
         * Types of persistence operations that can be performed.
         */
        public sealed interface Operation {
            public data object SaveCheckpoint : Operation
            public data object GetLatestCheckpoint : Operation
            public data object GetCheckpoints : Operation
            public data class GetCheckpointById(val id: String) : Operation
            public data class DeleteCheckpoint(val id: String) : Operation
            public data object DeleteAllCheckpoints : Operation
            public data object GetCheckpointCount : Operation
        }
    }

    /**
     * Hybrid strategy optimized for different checkpoint scenarios.
     *
     * Provides pre-configured logic for common use cases:
     * - Mid-execution checkpoints → Fast, ephemeral storage (e.g., Redis)
     * - Session persistence → Durable storage (e.g., PostgreSQL)
     * - Critical checkpoints → Most reliable storage available
     *
     * This is a specialized version of [Dynamic] with built-in logic for
     * typical persistence patterns.
     *
     * @property ephemeralProvider Provider for fast, temporary checkpoints
     * @property durableProvider Provider for long-term persistence
     * @property criticalProvider Optional provider for critical checkpoints (defaults to durable)
     * @property selector Optional custom selector to override default behavior
     */
    public data class Hybrid(
        val ephemeralProvider: PersistencyStorageProvider,
        val durableProvider: PersistencyStorageProvider,
        val criticalProvider: PersistencyStorageProvider? = null,
        val selector: (suspend (Dynamic.OperationContext) -> ProviderType)? = null
    ) : PersistencyStrategy {
        public enum class ProviderType {
            EPHEMERAL,
            DURABLE,
            CRITICAL
        }
    }
}