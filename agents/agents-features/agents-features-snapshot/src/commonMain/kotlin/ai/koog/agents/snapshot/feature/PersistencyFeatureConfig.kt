package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.snapshot.feature.RollbackToolRegistry
import ai.koog.agents.snapshot.providers.NoPersistenceStorageProvider
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider

@Deprecated(
    "`PersistencyFeatureConfig` has been renamed to `PersistenceFeatureConfig`",
    replaceWith = ReplaceWith(
        expression = "PersistenceFeatureConfig",
        "ai.koog.agents.snapshot.feature.PersistenceFeatureConfig"
    )
)
public typealias PersistencyFeatureConfig = PersistenceFeatureConfig

/**
 * Configuration class for the Snapshot feature.
 */
public class PersistenceFeatureConfig : FeatureConfig() {

    /**
     * Defines the storage mechanism for persisting snapshots in the feature.
     * This property accepts implementations of [PersistenceStorageProvider],
     * which manage how snapshots are stored and retrieved.
     *
     * By default, the storage is set to [NoPersistenceStorageProvider], a no-op
     * implementation that does not persist any data. To enable actual state
     * persistence, assign a custom implementation of [PersistenceStorageProvider]
     * to this property.
     */
    public var storage: PersistenceStorageProvider = NoPersistenceStorageProvider()

    /**
     * Controls whether the feature's state should be automatically persisted.
     * When enabled, changes to the checkpoint are saved after each node execution through the assigned
     * [PersistenceStorageProvider], ensuring the state can be restored later.
     *
     * Set this property to `true` to turn on automatic state persistence,
     * or `false` to disable it.
     */
    public var enableAutomaticPersistence: Boolean = false

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
     * Configure it during Persistence installation. Do not mutate later in withPersistence.
     */
    public var rollbackToolRegistry: RollbackToolRegistry = RollbackToolRegistry.EMPTY
}
