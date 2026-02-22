package ai.koog.koogelis.persistence

@JsExport
actual external interface KoogelisPersistenceStorageProvider {
    actual public fun getCheckpoints(agentId: String): Array<String>
    actual public fun saveCheckpoint(agentId: String, agentCheckpointData: String)
    actual public fun getLatestCheckpoint(agentId: String): String?
}