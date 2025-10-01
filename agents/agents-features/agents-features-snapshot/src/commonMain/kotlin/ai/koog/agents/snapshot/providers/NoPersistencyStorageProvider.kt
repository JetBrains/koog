package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.filters.AgentCheckpointFilter
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * No-op implementation of [PersistenceStorageProvider].
 */
public class NoPersistencyStorageProvider : PersistenceStorageProvider<AgentCheckpointFilter> {
    private val logger = KotlinLogging.logger { }

    override suspend fun getCheckpoints(agentId: String, filter: AgentCheckpointFilter?): List<AgentCheckpointData> {
        return emptyList()
    }

    override suspend fun saveCheckpoint(
        agentId: String,
        agentCheckpointData: AgentCheckpointData
    ) {
        logger.info { "Snapshot feature is not enabled in the agent. Snapshot will not be saved: $agentCheckpointData" }
    }

    override suspend fun getLatestCheckpoint(agentId: String, filter: AgentCheckpointFilter?): AgentCheckpointData? {
        return null
    }
}
