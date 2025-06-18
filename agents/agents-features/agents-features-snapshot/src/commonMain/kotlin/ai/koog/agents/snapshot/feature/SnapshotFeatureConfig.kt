package ai.koog.agents.snapshot.feature

import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.snapshot.providers.NoSnapshotProvider
import ai.koog.agents.snapshot.providers.SnapshotProvider


/** Configuration class for the Snapshot feature.
 */
public class SnapshotFeatureConfig: FeatureConfig() {

    /** The provider for snapshot operations.
     * This can be a custom implementation of [SnapshotProvider] that handles
     * loading and saving snapshots for agents.
     */
    internal var snapshotProvider: SnapshotProvider = NoSnapshotProvider

    /** Sets the [SnapshotProvider] for this feature.
     * @param snapshotProvider The provider to set.
     */
    public fun snapshotProvider(snapshotProvider: SnapshotProvider) {
        this.snapshotProvider = snapshotProvider
    }
}