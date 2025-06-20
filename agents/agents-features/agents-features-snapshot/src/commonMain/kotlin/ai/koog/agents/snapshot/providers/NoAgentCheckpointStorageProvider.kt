package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * No-op implementation of [AgentCheckpointStorageProvider].
 */
public object NoAgentCheckpointStorageProvider: AgentCheckpointStorageProvider {
    private val logger = KotlinLogging.logger {  }

    override suspend fun getCheckpoint(
        agentId: String,
        snapshotId: String
    ): AgentCheckpointData? {
        logger.info { "Snapshot feature is not enabled in the agent. No snapshot will be loaded for agentId: '$agentId', snapshotId: '$snapshotId'" }
        return null
    }

    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
        logger.info { "Snapshot feature is not enabled in the agent. Snapshot will not be saved: $agentCheckpointData" }
    }
}