package ai.koog.agents.snapshot.feature

import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.snapshot.providers.NoPersistencyStorageProvider
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider


/**
 * Configuration class for the Snapshot feature.
 */
public class PersistencyFeatureConfig: FeatureConfig() {

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
}