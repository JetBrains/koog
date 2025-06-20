@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData

public interface AgentCheckpointStorageProvider {
    public suspend fun getCheckpoint(agentId: String, snapshotId: String): AgentCheckpointData?
    public suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData)
}