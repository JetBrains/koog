package ai.koog.agents.snapshot.feature

import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.snapshot.providers.NoAgentCheckpointStorageProvider
import ai.koog.agents.snapshot.providers.AgentCheckpointStorageProvider


/** Configuration class for the Snapshot feature.
 */
public class AgentCheckpointFeatureConfig: FeatureConfig() {

    /** The provider for snapshot operations.
     * This can be a custom implementation of [AgentCheckpointStorageProvider] that handles
     * loading and saving snapshots for agents.
     */
    internal var agentCheckpointStorageProvider: AgentCheckpointStorageProvider = NoAgentCheckpointStorageProvider

    /** Sets the [AgentCheckpointStorageProvider] for this feature.
     * @param agentCheckpointStorageProvider The provider to set.
     */
    public fun snapshotProvider(agentCheckpointStorageProvider: AgentCheckpointStorageProvider) {
        this.agentCheckpointStorageProvider = agentCheckpointStorageProvider
    }
}