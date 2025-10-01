package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * No-op implementation of [PersistenceStorageProvider].
 */
public class NoPersistenceStorageProvider : PersistenceStorageProvider {
    private val logger = KotlinLogging.logger { }

    override suspend fun getCheckpoints(agentId: String): List<AgentCheckpointData> {
        return emptyList()
    }

    override suspend fun saveCheckpoint(
        agentId: String,
        agentCheckpointData: AgentCheckpointData
    ) {
        logger.info { "Snapshot feature is not enabled in the agent. Snapshot will not be saved: $agentCheckpointData" }
    }

    override suspend fun getLatestCheckpoint(agentId: String): AgentCheckpointData? {
        return null
    }
}
