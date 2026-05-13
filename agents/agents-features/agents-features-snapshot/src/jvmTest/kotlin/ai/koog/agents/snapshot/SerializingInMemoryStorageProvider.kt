package ai.koog.agents.snapshot

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.agents.snapshot.providers.PersistenceUtils
import ai.koog.agents.snapshot.providers.filters.AgentCheckpointPredicateFilter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Storage provider that serializes checkpoints to JSON on save and deserializes on read.
 * Used in tests to simulate real persistence (e.g., file or database) where data must survive serialization round-trips.
 */
internal class SerializingInMemoryStorageProvider : PersistenceStorageProvider<AgentCheckpointPredicateFilter> {
    private val mutex = Mutex()
    private val storageMap = mutableMapOf<String, MutableList<String>>()
    private val json = PersistenceUtils.defaultCheckpointJson

    override suspend fun saveCheckpoint(sessionId: String, agentCheckpointData: AgentCheckpointData) {
        val serialized = json.encodeToString(agentCheckpointData)
        mutex.withLock {
            storageMap.getOrPut(sessionId) { mutableListOf() }.add(serialized)
        }
    }

    override suspend fun getCheckpoints(sessionId: String, filter: AgentCheckpointPredicateFilter?): List<AgentCheckpointData> =
        mutex.withLock {
            val jsons = storageMap[sessionId] ?: return@withLock emptyList()
            val checkpoints = jsons.map { json.decodeFromString<AgentCheckpointData>(it) }
            if (filter != null) checkpoints.filter { filter.check(it) } else checkpoints
        }

    override suspend fun getLatestCheckpoint(sessionId: String, filter: AgentCheckpointPredicateFilter?): AgentCheckpointData? =
        mutex.withLock {
            val jsons = storageMap[sessionId] ?: return@withLock null
            val checkpoints = jsons.map { json.decodeFromString<AgentCheckpointData>(it) }
            val filtered = if (filter != null) checkpoints.filter { filter.check(it) } else checkpoints
            filtered.maxByOrNull { it.version }
        }
}
