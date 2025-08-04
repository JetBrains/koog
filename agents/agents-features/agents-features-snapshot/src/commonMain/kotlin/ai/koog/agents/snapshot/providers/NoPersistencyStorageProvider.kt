package ai.koog.agents.snapshot.providers

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * No-op implementation of [PersistencyStorageProvider].
 */
public class NoPersistencyStorageProvider : PersistencyStorageProvider {
    private val logger = KotlinLogging.logger { }

    override suspend fun getCheckpoints(context: AIAgentContextBase): List<AgentCheckpointData> {
        return emptyList()
    }

    override suspend fun saveCheckpoint(
        agentCheckpointData: AgentCheckpointData,
        context: AIAgentContextBase
    ) {
        logger.info { "Snapshot feature is not enabled in the agent. Snapshot will not be saved: $agentCheckpointData" }
    }

    override suspend fun getLatestCheckpoint(context: AIAgentContextBase): AgentCheckpointData? {
        return null
    }

    override suspend fun getCheckpointById(checkpointId: String, context: AIAgentContextBase): AgentCheckpointData? {
        return null
    }
}
