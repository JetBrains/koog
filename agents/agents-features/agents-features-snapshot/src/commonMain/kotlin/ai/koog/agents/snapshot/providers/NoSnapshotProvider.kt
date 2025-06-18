package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentSnapshot
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * No-op implementation of [SnapshotProvider].
 */
public object NoSnapshotProvider: SnapshotProvider {
    private val logger = KotlinLogging.logger {  }

    override suspend fun getSnapshot(
        agentId: String,
        snapshotId: String
    ): AgentSnapshot? {
        logger.info { "Snapshot feature is not enabled in the agent. No snapshot will be loaded for agentId: '$agentId', snapshotId: '$snapshotId'" }
        return null
    }

    override suspend fun saveSnapshot(agentSnapshot: AgentSnapshot) {
        logger.info { "Snapshot feature is not enabled in the agent. Snapshot will not be saved: $agentSnapshot" }
    }
}