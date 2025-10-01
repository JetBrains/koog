package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.snapshot.feature.RollbackToolRegistry
import ai.koog.agents.snapshot.providers.NoPersistencyStorageProvider
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider

/**
 * Configuration class for the Snapshot feature.
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
     * Determines the strategy to be used for rolling back the agent's state to a previously saved checkpoint.
     *
     * This property uses the [RollbackStrategy] enum to specify the extent of data restoration during a rollback:
     * - [RollbackStrategy.Default]: Restores the entire state, including message history and other context data.
     * - [RollbackStrategy.MessageHistoryOnly]: Restores only the message history while retaining other parts of the context.
     *
     * Defaults to [RollbackStrategy.Default], ensuring complete rollback functionality unless explicitly configured otherwise.
     */
    public var rollbackStrategy: RollbackStrategy = RollbackStrategy.Default

    /**
     * Registry for rollback tools used when rolling back to checkpoints.
     * Configure it during Persistency installation. Do not mutate later in withPersistency.
     */
    public var rollbackToolRegistry: RollbackToolRegistry = RollbackToolRegistry.EMPTY
}
