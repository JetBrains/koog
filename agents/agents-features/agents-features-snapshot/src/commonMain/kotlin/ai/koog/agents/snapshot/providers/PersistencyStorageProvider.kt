@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData

@Deprecated(
    "`PersistencyStorageProvider` has been renamed to `PersistenceStorageProvider`",
    replaceWith = ReplaceWith(
        expression = "PersistenceStorageProvider",
        "ai.koog.agents.snapshot.feature.PersistenceStorageProvider"
    )
)
public typealias PersistencyStorageProvider = PersistenceStorageProvider

public interface PersistenceStorageProvider {
    public suspend fun getCheckpoints(agentId: String): List<AgentCheckpointData>
    public suspend fun saveCheckpoint(agentId: String, agentCheckpointData: AgentCheckpointData)
    public suspend fun getLatestCheckpoint(agentId: String): AgentCheckpointData?
}
