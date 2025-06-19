package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of [AgentCheckpointStorageProvider].
 * This provider stores snapshots in a mutable map.
 */
public class InMemoryAgentCheckpointStorageProvider : AgentCheckpointStorageProvider {
    private val mutex = Mutex()
    private val snapshots = mutableMapOf<String, AgentCheckpointData>()

    override suspend fun getCheckpoint(agentId: String, snapshotId: String): AgentCheckpointData? {
        val snapshotKey = AgentCheckpointData.SNAPSHOT_ID_PREFIX + snapshotId
        return mutex.withLock {
            snapshots[snapshotKey]
        }
    }

    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
        mutex.withLock {
            snapshots[agentCheckpointData.getSnapshotKey()] = agentCheckpointData
        }
    }
}