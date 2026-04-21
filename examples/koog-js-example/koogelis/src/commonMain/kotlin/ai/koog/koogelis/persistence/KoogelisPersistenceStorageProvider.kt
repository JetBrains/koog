package ai.koog.koogelis.persistence

expect interface KoogelisPersistenceStorageProvider {
    public fun getCheckpoints(agentId: String): Array<String>
    public fun saveCheckpoint(agentId: String, agentCheckpointData: String)
    public fun getLatestCheckpoint(agentId: String): String?
}