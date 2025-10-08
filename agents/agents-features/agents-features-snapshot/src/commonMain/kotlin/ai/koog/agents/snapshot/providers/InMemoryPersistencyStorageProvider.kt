package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.filters.AgentCheckpointPredicateFilter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of [PersistenceStorageProvider].
 * This provider stores snapshots in a mutable map.
 */
public class InMemoryPersistenceStorageProvider() : PersistenceStorageProvider<AgentCheckpointPredicateFilter> {
    private val mutex = Mutex()
    private val snapshotMap = mutableMapOf<String, List<AgentCheckpointData>>()

    override suspend fun getCheckpoints(agentId: String, filter: AgentCheckpointPredicateFilter?): List<AgentCheckpointData> {
        mutex.withLock {
            val allCheckpoints = snapshotMap[agentId] ?: emptyList()
            if (filter != null) {
                return allCheckpoints.filter { filter.check(it) }
            }
            return allCheckpoints
        }
    }

    override suspend fun saveCheckpoint(agentId: String, agentCheckpointData: AgentCheckpointData) {
        mutex.withLock {
            snapshotMap[agentId] = (snapshotMap[agentId] ?: emptyList()) + agentCheckpointData
        }
    }

    override suspend fun getLatestCheckpoint(agentId: String, filter: AgentCheckpointPredicateFilter?): AgentCheckpointData? {
        mutex.withLock {
            if (filter != null) {
                return snapshotMap[agentId]?.filter { filter.check(it) }?.maxByOrNull { it.createdAt }
            }
            return snapshotMap[agentId]?.maxBy { it.createdAt }
        }
    }
}
