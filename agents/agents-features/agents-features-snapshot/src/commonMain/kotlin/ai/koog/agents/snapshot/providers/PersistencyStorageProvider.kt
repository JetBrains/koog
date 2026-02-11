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
     * Retrieves the list of checkpoints of the AI agent with the given [sessionId]
     * */
    public suspend fun getCheckpoints(sessionId: String): List<AgentCheckpointData> =
        getCheckpoints(sessionId, null)

    /**
     * Retrieves the list of checkpoints of the AI agent with the given [sessionId] that match the provided [filter]
     * */
    public suspend fun getCheckpoints(sessionId: String, filter: Filter?): List<AgentCheckpointData>

    /**
     * Saves provided checkpoint ([agentCheckpointData]) of the agent with [sessionId] to the storage (ex: database, S3, file)
     * */
    public suspend fun saveCheckpoint(sessionId: String, agentCheckpointData: AgentCheckpointData)

    /**
     * Retrieves the latest checkpoint of the AI agent with [sessionId]
     * */
    public suspend fun getLatestCheckpoint(sessionId: String): AgentCheckpointData? = getLatestCheckpoint(sessionId, null)

    /**
     * Retrieves the latest checkpoint of the AI agent with [sessionId] matching the provided [filter]
     * */
    public suspend fun getLatestCheckpoint(sessionId: String, filter: Filter?): AgentCheckpointData?
}
