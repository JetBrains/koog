package ai.koog.agents.features.redis.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * An abstract Redis-based implementation of [PersistencyStorageProvider] that stores agent checkpoints in Redis.
 *
 * This implementation organizes checkpoints by persistence ID and uses JSON serialization for storing and retrieving
 * checkpoint data. Each checkpoint is stored in its own Redis key (for TTL support) and a sorted set maintains
 * ordering by creation time.
 *
 * Concrete implementations must provide platform-specific Redis operations.
 *
 * @property persistenceId Unique identifier for this agent's persistence data
 * @property keyPrefix Optional prefix for all Redis keys (default: "agent:checkpoint")
 * @property ttlSeconds Optional TTL for checkpoint entries in seconds (null = no expiration)
 */
public abstract class RedisPersistencyStorageProvider(
    protected val persistenceId: String,
    protected val keyPrefix: String = "agent:checkpoint",
    protected val ttlSeconds: Long? = null
) : PersistencyStorageProvider {
    
    protected val json: Json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Constructs the Redis key for a specific checkpoint
     */
    protected fun checkpointKey(checkpointId: String): String =
        "$keyPrefix:$persistenceId:checkpoint:$checkpointId"
    
    /**
     * Constructs the Redis key for the checkpoint metadata sorted set (for ordering by creation time)
     */
    protected val checkpointsMetaKey: String
        get() = "$keyPrefix:$persistenceId:meta"
    
    /**
     * Platform-specific implementation to get all members of a sorted set in range
     * @return List of members in the specified range
     */
    protected abstract suspend fun zrange(key: String, start: Long, stop: Long): List<String>
    
    /**
     * Platform-specific implementation to get a string value
     * @return The value associated with key, or null if key doesn't exist
     */
    protected abstract suspend fun get(key: String): String?
    
    /**
     * Platform-specific implementation to set a string value
     * @return OK if successful
     */
    protected abstract suspend fun set(key: String, value: String): String?
    
    /**
     * Platform-specific implementation to add a member to a sorted set with score
     * @return Number of elements added (not including elements already existing)
     */
    protected abstract suspend fun zadd(key: String, score: Double, member: String): Long
    
    /**
     * Platform-specific implementation to set TTL on a key
     * @return true if timeout was set, false if key doesn't exist
     */
    protected abstract suspend fun expire(key: String, seconds: Long): Boolean
    
    /**
     * Platform-specific implementation to get all keys matching a pattern
     * @return List of keys matching the pattern
     */
    protected abstract suspend fun keys(pattern: String): List<String>
    
    /**
     * Platform-specific implementation to remove a member from a sorted set
     * @return Number of members removed
     */
    protected abstract suspend fun zrem(key: String, member: String): Long
    
    /**
     * Platform-specific implementation to delete keys
     * @return Number of keys removed
     */
    protected abstract suspend fun del(vararg keys: String): Long
    
    /**
     * Platform-specific implementation to get the TTL of a key in seconds
     * @return TTL in seconds, -1 if key has no TTL, -2 if key doesn't exist
     */
    protected abstract suspend fun ttl(key: String): Long
    
    override suspend fun getCheckpoints(): List<AgentCheckpointData> {
        // Get all checkpoint IDs sorted by creation time
        val checkpointIds = zrange(checkpointsMetaKey, 0, -1)
        
        // Retrieve all checkpoints from individual keys, cleaning up expired entries
        val validCheckpoints = mutableListOf<AgentCheckpointData>()
        val expiredIds = mutableListOf<String>()
        
        for (checkpointId in checkpointIds) {
            val checkpointJson = get(checkpointKey(checkpointId))
            if (checkpointJson != null) {
                runCatching {
                    json.decodeFromString<AgentCheckpointData>(checkpointJson)
                }.getOrNull()?.let { validCheckpoints.add(it) }
            } else {
                // Checkpoint has expired or been deleted, clean up the metadata
                expiredIds.add(checkpointId)
            }
        }
        
        // Clean up expired entries from sorted set
        if (expiredIds.isNotEmpty()) {
            for (id in expiredIds) {
                zrem(checkpointsMetaKey, id)
            }
        }
        
        return validCheckpoints
    }
    
    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
        val checkpointJson = json.encodeToString(agentCheckpointData)
        val score = agentCheckpointData.createdAt.toEpochMilliseconds().toDouble()
        val key = checkpointKey(agentCheckpointData.checkpointId)
        
        // Note: We don't use Redis transactions here because:
        // 1. Lettuce coroutines transaction DSL has known blocking issues (issue #2371)
        // 2. These operations are logically related but can tolerate partial failure
        // 3. The cleanup logic in getCheckpoints() handles orphaned entries
        
        // Store checkpoint in its own key
        set(key, checkpointJson)
        
        // Add to sorted set for ordering by creation time
        zadd(checkpointsMetaKey, score, agentCheckpointData.checkpointId)
        
        // Set TTL on individual checkpoint if specified
        ttlSeconds?.let { ttl ->
            expire(key, ttl)
        }
    }
    
    override suspend fun getLatestCheckpoint(): AgentCheckpointData? {
        // Get checkpoint IDs in reverse order (latest first)
        val checkpointIds = zrange(checkpointsMetaKey, -10, -1).reversed()
        
        // Find the first non-expired checkpoint
        for (checkpointId in checkpointIds) {
            val checkpointJson = get(checkpointKey(checkpointId))
            if (checkpointJson != null) {
                return runCatching {
                    json.decodeFromString<AgentCheckpointData>(checkpointJson)
                }.getOrNull()
            } else {
                // Clean up expired entry
                zrem(checkpointsMetaKey, checkpointId)
            }
        }
        
        return null
    }
    
    /**
     * Deletes a specific checkpoint by ID
     */
    public open suspend fun deleteCheckpoint(checkpointId: String) {
        del(checkpointKey(checkpointId))
        zrem(checkpointsMetaKey, checkpointId)
    }
    
    /**
     * Deletes all checkpoints for this persistence ID
     */
    public open suspend fun deleteAllCheckpoints() {
        // Get all checkpoint IDs
        val checkpointIds = zrange(checkpointsMetaKey, 0, -1)
        
        // Delete all checkpoint keys
        if (checkpointIds.isNotEmpty()) {
            val keysToDelete = checkpointIds.map { checkpointKey(it) }.toTypedArray()
            del(*keysToDelete)
        }
        
        // Delete the metadata sorted set
        del(checkpointsMetaKey)
    }
    
    /**
     * Gets the total number of checkpoints stored
     */
    public open suspend fun getCheckpointCount(): Long {
        // Count keys matching the checkpoint pattern
        val pattern = "$keyPrefix:$persistenceId:checkpoint:*"
        return keys(pattern).size.toLong()
    }
}