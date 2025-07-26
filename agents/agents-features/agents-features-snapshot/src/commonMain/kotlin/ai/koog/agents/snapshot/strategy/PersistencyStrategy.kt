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
     * Hybrid strategy with explicit routing logic for different checkpoint scenarios.
     *
     * Provides three provider types with custom routing logic:
     * - Mid-execution checkpoints → Fast, ephemeral storage (e.g., Redis)
     * - Session persistence → Durable storage (e.g., PostgreSQL)
     * - Critical checkpoints → Most reliable storage available
     *
     * This is a specialized version of [Dynamic] with predefined provider types
     * but requires explicit routing logic for predictable behavior.
     *
     * @property ephemeralProvider Provider for fast, temporary checkpoints
     * @property durableProvider Provider for long-term persistence
     * @property criticalProvider Optional provider for critical checkpoints (defaults to durable)
     * @property selector Function that determines which provider type to use for each operation
     */
    public data class Hybrid(
        val ephemeralProvider: PersistencyStorageProvider,
        val durableProvider: PersistencyStorageProvider,
        val criticalProvider: PersistencyStorageProvider? = null,
        val selector: suspend (Dynamic.OperationContext) -> ProviderType
    ) : PersistencyStrategy {
        public enum class ProviderType {
            EPHEMERAL,
            DURABLE,
            CRITICAL
        }
    }
    
    /**
     * Intelligent hybrid strategy with LLM-driven routing decisions.
     *
     * This strategy combines the simplicity of [Hybrid] with the intelligence of LLM-based
     * routing. It automatically determines whether checkpoints should go to ephemeral or
     * durable storage based on contextual analysis of:
     * - Node execution state (start, middle, end)
     * - Checkpoint characteristics (message history, criticality)
     * - Agent context and task description
     *
     * Unlike hardcoded heuristics, this uses the LLM to make intelligent routing decisions
     * while providing fallback behavior for reliability.
     *
     * @property ephemeralProvider Provider for fast, temporary checkpoints
     * @property durableProvider Provider for long-term persistence
     * @property criticalProvider Optional provider for critical checkpoints
     * @property taskDescription Description of the agent's task for context-aware routing
     * @property maxRetries Maximum LLM retries before falling back to simple logic (default: 2)
     * @property fallbackToSimple Whether to use simple hybrid logic on LLM failure (default: true)
     */
    public data class SmartHybrid(
        val ephemeralProvider: PersistencyStorageProvider,
        val durableProvider: PersistencyStorageProvider,
        val criticalProvider: PersistencyStorageProvider? = null,
        val taskDescription: String = "General agent task",
        val maxRetries: Int = 2,
        val fallbackToSimple: Boolean = true
    ) : PersistencyStrategy

    /**
     * LLM-driven strategy for intelligent provider selection.
     *
     * This strategy uses the LLM
     * to determine the most appropriate persistence provider based on:
     * - The current operation context
     * - Checkpoint characteristics
     * - Provider capabilities
     * - Task description
     *
     * @property providers Map of provider names to their instances with descriptions
     * @property taskDescription Description of the current task/context for the LLM
     * @property maxRetries Maximum number of retries for LLM selection (default: 3)
     */
    public data class AutoSelectForTask(
        val providers: Map<String, ProviderInfo>,
        val taskDescription: String,
        val maxRetries: Int = 3
    ) : PersistencyStrategy {
        /**
         * Information about a persistence provider for LLM decision-making.
         *
         * @property provider The actual persistence provider instance
         * @property description Human-readable description of the provider's characteristics
         * @property capabilities List of capabilities (e.g., "fast", "durable", "queryable")
         */
        public data class ProviderInfo(
            val provider: PersistencyStorageProvider,
            val description: String,
            val capabilities: List<String> = emptyList()
        )
    }
}