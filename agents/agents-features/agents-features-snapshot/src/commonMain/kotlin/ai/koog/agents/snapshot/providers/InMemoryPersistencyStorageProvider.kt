package ai.koog.agents.snapshot.providers

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of [PersistencyStorageProvider].
 * This provider stores snapshots in a mutable map, keyed by agent context information.
 * 
 * Note: This implementation uses the agent's ID from the context for storage partitioning.
 * In a real multi-user scenario, you might want to use additional context like user ID.
 */
public class InMemoryPersistencyStorageProvider : PersistencyStorageProvider {
    private val mutex = Mutex()
    private val snapshotMap = mutableMapOf<String, List<AgentCheckpointData>>()

    override suspend fun getCheckpoints(context: AIAgentContextBase): List<AgentCheckpointData> {
        mutex.withLock {
            val key = getStorageKey(context)
            return snapshotMap[key] ?: emptyList()
        }
    }

    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData, context: AIAgentContextBase) {
        mutex.withLock {
            val key = getStorageKey(context)
            snapshotMap[key] = (snapshotMap[key] ?: emptyList()) + agentCheckpointData
        }
    }

    override suspend fun getLatestCheckpoint(context: AIAgentContextBase): AgentCheckpointData? {
        mutex.withLock {
            val key = getStorageKey(context)
            return snapshotMap[key]?.maxByOrNull { it.createdAt }
        }
    }

    override suspend fun getCheckpointById(checkpointId: String, context: AIAgentContextBase): AgentCheckpointData? {
        mutex.withLock {
            val key = getStorageKey(context)
            return snapshotMap[key]?.find { it.checkpointId == checkpointId }
        }
    }

    private fun getStorageKey(context: AIAgentContextBase): String {
        // Use agent ID as the storage key. In a real implementation, you might
        // combine agent ID with user ID or session ID for proper multi-tenancy.
        return context.agentId
    }
}
