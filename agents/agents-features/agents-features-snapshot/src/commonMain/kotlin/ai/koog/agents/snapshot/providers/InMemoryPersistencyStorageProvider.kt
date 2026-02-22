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

    override suspend fun getCheckpoints(sessionId: String, filter: AgentCheckpointPredicateFilter?): List<AgentCheckpointData> {
        mutex.withLock {
            val allCheckpoints = snapshotMap[sessionId] ?: emptyList()
            if (filter != null) {
                return allCheckpoints.filter { filter.check(it) }
            }
            return allCheckpoints
        }
    }

    override suspend fun saveCheckpoint(sessionId: String, agentCheckpointData: AgentCheckpointData) {
        mutex.withLock {
            snapshotMap[sessionId] = (snapshotMap[sessionId] ?: emptyList()) + agentCheckpointData
        }
    }

    override suspend fun getLatestCheckpoint(sessionId: String, filter: AgentCheckpointPredicateFilter?): AgentCheckpointData? {
        mutex.withLock {
            if (filter != null) {
                return snapshotMap[sessionId]?.filter { filter.check(it) }?.maxByOrNull { it.createdAt }
            }

            return snapshotMap[sessionId]?.maxBy { it.version }
        }
    }

    /**
     * Removes all stored checkpoints from the in-memory storage. If a filter is provided,
     * this method will determine which checkpoints to remove based on the filter's logic.
     *
     * @param filter Optional filter to determine whether any checkpoints should be removed
     *               conditionally. If `null`, all checkpoints will be removed unconditionally.
     */
    public suspend fun removeCheckpoints(filter: AgentCheckpointPredicateFilter? = null) {
        mutex.withLock {
            if (filter != null) {
                snapshotMap.keys.forEach { agentId -> snapshotMap[agentId] = snapshotMap[agentId]!!.filterNot { filter.check(it) } }
            } else {
                snapshotMap.clear()
            }
        }
    }
}
