package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of [PersistencyStorageProvider].
 * This provider stores snapshots in a mutable map.
 */
public class InMemoryPersistencyStorageProvider : PersistencyStorageProvider {
    private val mutex = Mutex()
    private val snapshotMap = mutableMapOf<String, List<AgentCheckpointData>>()

    override suspend fun getCheckpoints(agentId: String): List<AgentCheckpointData> {
        mutex.withLock {
            return snapshotMap[agentId] ?: emptyList()
        }
    }

    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
        mutex.withLock {
            val agentId = agentCheckpointData.agentId
            snapshotMap[agentId] = (snapshotMap[agentId] ?: emptyList()) + agentCheckpointData
        }
    }

    override suspend fun getLatestCheckpoint(agentId: String): AgentCheckpointData? {
        mutex.withLock {
            return snapshotMap[agentId]?.maxBy { it.createdAt }
        }
    }
}