package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Interface for secure checkpoint storage backends that can be used with SecurePersistencyProvider.
 * This allows the snapshot feature to work with different storage implementations while
 * maintaining security and encryption capabilities.
 */
public interface SecurePersistencyBackend {
    /** Get a value by key */
    public suspend fun get(key: String): String?
    
    /** Store a key-value pair */
    public suspend fun put(key: String, value: String)
    
    /** Delete a key */
    public suspend fun delete(key: String)
    
    /** Get all keys matching a prefix */
    public suspend fun keys(prefix: String): List<String>
    
    /** Close the backend and release resources */
    public suspend fun close()
}

/**
 * Secure persistency provider that uses a pluggable secure storage backend.
 * 
 * This provider offers:
 * - **Pluggable backends**: Works with any SecurePersistencyBackend implementation
 * - **Security**: Data encryption handled by the backend
 * - **Performance**: Efficient SQLite-based storage with B-tree indexes
 * - **Multiplatform**: Works across JVM, JS platforms
 * 
 * **Storage Structure:**
 * ```
 * Key format: "checkpoints/{persistence-id}/{checkpoint-id}"
 * 
 * Examples:
 * - "checkpoints/agent-session-123/checkpoint-001"
 * - "checkpoints/workflow-456/checkpoint-002"
 * ```
 * 
 * **Usage Example:**
 * ```kotlin
 * install(Persistency) {
 *     persistencyProvider = SecurePersistencyProvider(
 *         config = SecurePersistencyConfig(
 *             persistenceId = "my-agent-session",
 *             maxCheckpoints = 50
 *         ),
 *         backend = secureStorage.persistencyBackend() // From SecureStorage feature
 *     )
 * }
 * ```
 * 
 * @property config Configuration for the secure persistency provider
 * @property backend Secure checkpoint storage backend
 */
public class SecurePersistencyProvider(
    private val config: SecurePersistencyConfig,
    private val backend: SecurePersistencyBackend
) : PersistencyStorageProvider {
    
    private val mutex = Mutex()
    
    /**
     * JSON configuration optimized for checkpoint storage.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    /**
     * Generates a storage key for the given checkpoint.
     */
    private fun getCheckpointKey(checkpointId: String): String =
        "checkpoints/${config.persistenceId}/$checkpointId"
    
    /**
     * Generates a pattern key for listing all checkpoints for this persistence ID.
     */
    private fun getCheckpointsPattern(): String =
        "checkpoints/${config.persistenceId}/"
    
    override suspend fun getCheckpoints(): List<AgentCheckpointData> = mutex.withLock {
        try {
            val pattern = getCheckpointsPattern()
            val checkpointKeys = backend.keys(pattern)
            
            val checkpoints = mutableListOf<AgentCheckpointData>()
            for (key in checkpointKeys) {
                val content = backend.get(key)
                if (content != null) {
                    try {
                        val checkpoint = json.decodeFromString<AgentCheckpointData>(content)
                        checkpoints.add(checkpoint)
                    } catch (e: Exception) {
                        // Skip corrupted checkpoints but continue processing others
                        continue
                    }
                }
            }
            
            // Return sorted by creation time (newest first)
            val sortedCheckpoints = checkpoints.sortedByDescending { it.createdAt }
            
            // Apply max checkpoints limit if configured
            if (config.maxCheckpoints > 0 && sortedCheckpoints.size > config.maxCheckpoints) {
                // Keep only the most recent checkpoints
                val checkpointsToKeep = sortedCheckpoints.take(config.maxCheckpoints)
                val checkpointsToRemove = sortedCheckpoints.drop(config.maxCheckpoints)
                
                // Remove old checkpoints from storage
                for (oldCheckpoint in checkpointsToRemove) {
                    val oldKey = getCheckpointKey(oldCheckpoint.checkpointId)
                    backend.delete(oldKey)
                }
                
                checkpointsToKeep
            } else {
                sortedCheckpoints
            }
        } catch (e: Exception) {
            // Return empty list on error for graceful degradation
            emptyList()
        }
    }
    
    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData): Unit = mutex.withLock {
        try {
            val key = getCheckpointKey(agentCheckpointData.checkpointId)
            val serialized = json.encodeToString(agentCheckpointData)
            backend.put(key, serialized)
        } catch (e: Exception) {
            // Re-throw save errors as they're critical for persistence functionality
            throw e
        }
    }
    
    override suspend fun getLatestCheckpoint(): AgentCheckpointData? {
        val checkpoints = getCheckpoints()
        return checkpoints.maxByOrNull { it.createdAt }
    }
}

/**
 * Configuration for secure persistency storage.
 * 
 * This configuration allows the snapshot feature to use secure storage backends
 * without tight coupling to specific storage implementations.
 * 
 * @property persistenceId Unique identifier for this agent's checkpoint collection
 * @property maxCheckpoints Optional limit on number of checkpoints to retain (0 = unlimited)
 */
@Serializable
@SerialName("secure-persistency")
public data class SecurePersistencyConfig(
    val persistenceId: String,
    val maxCheckpoints: Int = 0 // 0 means unlimited
)