@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentSnapshot

public interface SnapshotProvider {
    public suspend fun getSnapshot(agentId: String, snapshotId: String): AgentSnapshot?
    public suspend fun saveSnapshot(agentSnapshot: AgentSnapshot)
}