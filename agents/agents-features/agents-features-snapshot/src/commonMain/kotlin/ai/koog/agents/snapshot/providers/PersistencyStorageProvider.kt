@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData

@Deprecated(
    "`PersistencyStorageProvider` has been renamed to `PersistenceStorageProvider`",
    replaceWith = ReplaceWith(
        expression = "PersistenceStorageProvider",
        "ai.koog.agents.snapshot.feature.PersistenceStorageProvider"
    )
)
public typealias PersistencyStorageProvider<Filter> = PersistenceStorageProvider<Filter>

/**
 * Storage provider (ex: database, S3, file) to be used in [ai.koog.agents.snapshot.feature.Persistence] feature.
 * */
public interface PersistenceStorageProvider<Filter> {

    /**
     * Retrieves the list of checkpoints of the AI agent with the given [agentId]
     * */
    public suspend fun getCheckpoints(agentId: String): List<AgentCheckpointData> =
        getCheckpoints(agentId, null)

    /**
     * Retrieves the list of checkpoints of the AI agent with the given [agentId] that match the provided [filter]
     * */
    public suspend fun getCheckpoints(agentId: String, filter: Filter?): List<AgentCheckpointData>

    /**
     * Saves provided checkpoint ([agentCheckpointData]) of the agent with [agentId] to the storage (ex: database, S3, file)
     * */
    public suspend fun saveCheckpoint(agentId: String, agentCheckpointData: AgentCheckpointData)

    /**
     * Retrieves the latest checkpoint of the AI agent with [agentId]
     * */
    public suspend fun getLatestCheckpoint(agentId: String): AgentCheckpointData? = getLatestCheckpoint(agentId, null)

    /**
     * Retrieves the latest checkpoint of the AI agent with [agentId] matching the provided [filter]
     * */
    public suspend fun getLatestCheckpoint(agentId: String, filter: Filter?): AgentCheckpointData?
}
