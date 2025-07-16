package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of [PersistencyStorageProvider].
 * This provider stores snapshots in a mutable map.
 */
public class InMemoryPersistencyStorageProvider(private val persistenceId: String) : PersistencyStorageProvider {
    private val mutex = Mutex()
    private val snapshotMap = mutableMapOf<String, List<AgentCheckpointData>>()

    override suspend fun getCheckpoints(): List<AgentCheckpointData> {
        mutex.withLock {
            return snapshotMap[persistenceId] ?: emptyList()
        }
    }

    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
        mutex.withLock {
            val agentId = persistenceId
            snapshotMap[agentId] = (snapshotMap[agentId] ?: emptyList()) + agentCheckpointData
        }
    }

    override suspend fun getLatestCheckpoint(): AgentCheckpointData? {
        mutex.withLock {
            return snapshotMap[persistenceId]?.maxBy { it.createdAt }
        }
    }
}