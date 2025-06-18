package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of [SnapshotProvider].
 * This provider stores snapshots in a mutable map.
 */
public class InMemorySnapshotProvider : SnapshotProvider {
    private val mutex = Mutex()
    private val snapshots = mutableMapOf<String, AgentSnapshot>()

    override suspend fun getSnapshot(agentId: String, snapshotId: String): AgentSnapshot? {
        val snapshotKey = AgentSnapshot.SNAPSHOT_ID_PREFIX + snapshotId
        return mutex.withLock {
            snapshots[snapshotKey]?.takeIf { it.agentId == agentId }
        }
    }

    override suspend fun saveSnapshot(agentSnapshot: AgentSnapshot) {
        mutex.withLock {
            // Ensure the snapshot key is correctly prefixed
            val snapshotKey = agentSnapshot.getSnapshotKey()
            // Store the snapshot in the map
            snapshots[snapshotKey] = agentSnapshot
        }
    }
}