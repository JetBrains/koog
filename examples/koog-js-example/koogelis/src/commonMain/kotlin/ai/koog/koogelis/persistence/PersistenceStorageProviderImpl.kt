package ai.koog.koogelis.persistence

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.agents.snapshot.providers.filters.AgentCheckpointPredicateFilter
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.serialization.json.Json


class PersistenceStorageProviderImpl(val provider: KoogelisPersistenceStorageProvider, val logger: KLogger): PersistenceStorageProvider<AgentCheckpointPredicateFilter> {

    private val json = Json { prettyPrint = true }

    override suspend fun getCheckpoints(agentId: String, filter: AgentCheckpointPredicateFilter?): List<AgentCheckpointData> {
        val checkPoints = mutableListOf<AgentCheckpointData>()

        provider.getCheckpoints(agentId).forEach { checkPoint: String ->
            try {
                if (checkPoint.isNotBlank()) {
                    val deserialized = json.decodeFromString<AgentCheckpointData>(checkPoint)
                    checkPoints.add(deserialized)
                }
            } catch (ex: Exception) {
                logger.error { "Failed to deserialize a check point: $checkPoint, error: ${ex.message}" }
            }
        }

        if (filter != null) {
            return checkPoints.filter { filter.check(it) }
        }

        return checkPoints
    }

    override suspend fun saveCheckpoint(agentId: String, agentCheckpointData: AgentCheckpointData) {
        try {
            val serialized = json.encodeToString(AgentCheckpointData.serializer(), agentCheckpointData)
            provider.saveCheckpoint(agentId, serialized)
        } catch (ex: Exception) {
            logger.error { "Failed to serialize a check point: $agentCheckpointData, error: ${ex.message}" }
        }
    }

    override suspend fun getLatestCheckpoint(agentId: String, filter: AgentCheckpointPredicateFilter?): AgentCheckpointData? {
        val latestCheckpoint = provider.getLatestCheckpoint(agentId) ?: return null

        if (latestCheckpoint.isBlank()) return null

        val agentCheckpointData = try {
            json.decodeFromString<AgentCheckpointData>(latestCheckpoint)
        } catch (ex: Exception) {
            logger.error { "Failed to deserialize the latest check point: $latestCheckpoint, error: ${ex.message}" }
            null
        }

        if (agentCheckpointData == null) return null
        if (filter == null) return agentCheckpointData

        return if (filter.check(agentCheckpointData)) {
            agentCheckpointData
        } else {
            null
        }
    }
}