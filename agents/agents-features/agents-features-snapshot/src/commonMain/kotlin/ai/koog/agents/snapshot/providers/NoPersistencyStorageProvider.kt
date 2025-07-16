package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * No-op implementation of [PersistencyStorageProvider].
 */
public class NoPersistencyStorageProvider: PersistencyStorageProvider {
    private val logger = KotlinLogging.logger {  }

    override suspend fun getCheckpoints(): List<AgentCheckpointData> {
        return emptyList()
    }

    override suspend fun saveCheckpoint(
        agentCheckpointData: AgentCheckpointData
    ) {
        logger.info { "Snapshot feature is not enabled in the agent. Snapshot will not be saved: $agentCheckpointData" }
    }

    override suspend fun getLatestCheckpoint(): AgentCheckpointData? {
        return null
    }
}