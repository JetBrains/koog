package ai.koog.agents.snapshot.feature

import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.snapshot.providers.NoAgentCheckpointProvider
import ai.koog.agents.snapshot.providers.AgentCheckpointProvider


/** Configuration class for the Snapshot feature.
 */
public class AgentCheckpointFeatureConfig: FeatureConfig() {

    /** The provider for snapshot operations.
     * This can be a custom implementation of [AgentCheckpointProvider] that handles
     * loading and saving snapshots for agents.
     */
    internal var agentCheckpointProvider: AgentCheckpointProvider = NoAgentCheckpointProvider

    /** Sets the [AgentCheckpointProvider] for this feature.
     * @param agentCheckpointProvider The provider to set.
     */
    public fun snapshotProvider(agentCheckpointProvider: AgentCheckpointProvider) {
        this.agentCheckpointProvider = agentCheckpointProvider
    }
}