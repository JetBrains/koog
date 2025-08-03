package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.snapshot.providers.NoPersistencyStorageProvider
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider

/**
 * Configuration class for the Persistency feature.
 */
public class PersistencyFeatureConfig : FeatureConfig() {

    /**
     * Defines the storage mechanism for persisting snapshots in the feature.
     * This property accepts implementations of [PersistencyStorageProvider],
     * which manage how snapshots are stored and retrieved.
     *
     * By default, the storage is set to [NoPersistencyStorageProvider], a no-op
     * implementation that does not persist any data. To enable actual state
     * persistence, assign a custom implementation of [PersistencyStorageProvider]
     * to this property.
     */
    public var storage: PersistencyStorageProvider = NoPersistencyStorageProvider()

    /**
     * Controls whether the feature's state should be automatically persisted.
     * When enabled, changes to the checkpoint are saved after each node execution through the assigned
     * [PersistencyStorageProvider], ensuring the state can be restored later.
     *
     * Set this property to `true` to turn on automatic state persistency,
     * or `false` to disable it.
     */
    public var enableAutomaticPersistency: Boolean = false

    /**
     * Optional stable identifier for the strategy/graph.
     * Used to identify which strategy created a checkpoint for migration purposes.
     * If not set, checkpoints will have strategyId = null.
     */
    public var strategyId: String? = null

    /**
     * Version number of the current strategy graph.
     * Used to determine when checkpoint migration is needed.
     * Defaults to 1 for backwards compatibility.
     */
    public var graphVersion: Int = 1

    /**
     * Optional hash of the current graph topology.
     * Used to detect unexpected changes in graph structure and emit warnings.
     * If not set, hash validation is skipped.
     */
    public var graphHash: String? = null

    /**
     * List of migrators that can handle checkpoint version upgrades.
     * Migrators are applied in order when loading older checkpoints.
     */
    public val migrators: MutableList<CheckpointMigrator> = mutableListOf()

    /**
     * Policy for trimming message history in checkpoints.
     * Applied during checkpoint creation to limit storage size.
     * If null, no trimming is performed.
     */
    public var historyPolicy: HistoryPolicy? = null

    /**
     * Strategy hasher for computing graph structure hashes.
     * Used to automatically compute graphHash based on strategy structure.
     * If null, no automatic hash computation is performed.
     */
    public var strategyHasher: StrategyHasher? = null

    /**
     * Whether to automatically compute the graph hash during feature installation.
     * When true, the hash will be computed automatically when the strategy becomes available.
     * Defaults to false for backwards compatibility.
     */
    public var autoComputeHash: Boolean = false

    /**
     * Computes and sets the graphHash based on the provided strategy using the configured hasher.
     * This is a convenience method for automatic hash computation.
     * 
     * @param strategy The strategy to compute the hash for
     * @return The computed hash, or null if computation failed or no hasher is configured
     */
    public suspend fun computeAndSetHash(strategy: AIAgentStrategy<*, *>): String? {
        val hasher = strategyHasher ?: return null
        
        return when (val result = hasher.computeHash(strategy)) {
            is HashComputationResult.Success -> {
                graphHash = result.hash
                result.hash
            }
            is HashComputationResult.Failed -> {
                logger.warn { "Hash computation failed for strategy ${strategy.name}: ${result.reason}" }
                null
            }
            is HashComputationResult.Unavailable -> {
                logger.debug { "Hash computation unavailable for strategy ${strategy.name}" }
                null
            }
        }
    }

    /**
     * Validates the configuration and logs warnings for potential issues.
     */
    public fun validate() {
        if (strategyHasher != null && graphVersion == 1) {
            logger.warn { 
                "Hash validation with version 1 may not be meaningful. " +
                "Consider using version 2+ for strategies that use hash validation."
            }
        }
        
        if (autoComputeHash && strategyHasher == null) {
            logger.warn { 
                "autoComputeHash is enabled but no strategyHasher is configured. " +
                "Hash computation will be skipped."
            }
        }
        
        if (migrators.isNotEmpty() && strategyId == null) {
            logger.warn { 
                "Migrators are configured but strategyId is null. " +
                "Migration may not work correctly without a stable strategy identifier."
            }
        }
    }

    private companion object {
        private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger { }
    }
}
