@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData

public interface PersistencyStorageProvider {
    public suspend fun getCheckpoints(): List<AgentCheckpointData>
    public suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData)
    public suspend fun getLatestCheckpoint(): AgentCheckpointData?
}
