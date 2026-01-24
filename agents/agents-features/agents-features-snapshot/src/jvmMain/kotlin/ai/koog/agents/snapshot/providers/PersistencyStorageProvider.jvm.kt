@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture

/**
 * Storage provider (ex: database, S3, file) to be used in [ai.koog.agents.snapshot.feature.Persistence] feature.
 * */
public abstract class AsyncPersistenceStorageProvider<Filter> : PersistenceStorageProvider<Filter> {

    /**
     * Retrieves the list of checkpoints of the AI agent with the given [agentId] that match the provided [filter].
     * */
    public abstract fun getCheckpointsAsync(
        agentId: String,
        filter: Filter?
    ): CompletableFuture<List<AgentCheckpointData>>

    /**
     * Saves provided checkpoint ([agentCheckpointData]) of the agent with [agentId] to the storage (ex: database, S3, file)
     * */
    public abstract fun saveCheckpointAsync(
        agentId: String,
        agentCheckpointData: AgentCheckpointData
    ): CompletableFuture<Boolean>

    /**
     * Retrieves the latest checkpoint of the AI agent with [agentId] matching the provided [filter]
     * */
    public abstract fun getLatestCheckpointAsync(
        agentId: String,
        filter: Filter?
    ): CompletableFuture<AgentCheckpointData?>

    // ------- implementations ----------

    /**
     * Retrieves the list of checkpoints of the AI agent with the given [agentId] that match the provided [filter]
     * */
    public final override suspend fun getCheckpoints(agentId: String, filter: Filter?): List<AgentCheckpointData> =
        getCheckpointsAsync(agentId, filter).await()

    /**
     * Saves provided checkpoint ([agentCheckpointData]) of the agent with [agentId] to the storage (ex: database, S3, file)
     * */
    public final override suspend fun saveCheckpoint(agentId: String, agentCheckpointData: AgentCheckpointData) {
        saveCheckpointAsync(agentId, agentCheckpointData).await()
    }

    /**
     * Retrieves the latest checkpoint of the AI agent with [agentId] matching the provided [filter]
     * */
    public final override suspend fun getLatestCheckpoint(agentId: String, filter: Filter?): AgentCheckpointData? =
        getLatestCheckpointAsync(agentId, filter).await()
}
