package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of [PersistenceStorageProvider].
 * This provider stores snapshots in a mutable map.
 */
public class InMemoryPersistenceStorageProvider() : PersistenceStorageProvider {
    private val mutex = Mutex()
    private val snapshotMap = mutableMapOf<String, List<AgentCheckpointData>>()

    override suspend fun getCheckpoints(agentId: String): List<AgentCheckpointData> {
        mutex.withLock {
            return snapshotMap[agentId] ?: emptyList()
        }
    }

    override suspend fun saveCheckpoint(agentId: String, agentCheckpointData: AgentCheckpointData) {
        mutex.withLock {
            snapshotMap[agentId] = (snapshotMap[agentId] ?: emptyList()) + agentCheckpointData
        }
    }

    override suspend fun getLatestCheckpoint(agentId: String): AgentCheckpointData? {
        mutex.withLock {
            return snapshotMap[agentId]?.maxBy { it.createdAt }
        }
    }
}
