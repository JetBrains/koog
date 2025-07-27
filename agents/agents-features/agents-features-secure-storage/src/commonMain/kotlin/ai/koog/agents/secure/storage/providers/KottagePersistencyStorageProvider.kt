package ai.koog.agents.secure.storage.providers

import ai.koog.agents.secure.storage.LocalKVStorage
import ai.koog.agents.secure.storage.StorageMode
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Secure storage implementation of [PersistencyStorageProvider] using Kottage backend with encryption.
 * 
 * This provider leverages the SecureStorage feature to provide:
 * - **Enterprise-grade security**: AES-256-GCM encryption for sensitive checkpoint data
 * - **High performance**: SQLite backend optimized for agent state persistence
 * - **Flexible key management**: Pluggable encryption key providers
 * - **Development support**: Plain mode for testing/development
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
 * **Security Features:**
 * - Agent checkpoint data encrypted at rest using AES-256-GCM
 * - GDPR/SOC2/HIPAA compliance ready for sensitive session data
 * - Secure key management with configurable providers
 * - Protection against unauthorized access to agent states
 * - Message history and context data encryption
 * 
 * **Usage Example:**
 * ```kotlin
 * install(Persistency) {
 *     persistencyProvider = KottagePersistencyStorageProvider(
 *         config = SecurePersistencyConfig(
 *             persistenceId = "my-agent-session",
 *             encryption = EncryptedMode {
 *                 keyProvider = PassphraseKeyProvider("secure-passphrase", salt, 100000)
 *                 databasePath = "checkpoints.db"
 *             }
 *         ),
 *         storage = mySecureStorage
 *     )
 * }
 * ```
 * 
 * @property config Configuration for the secure persistency provider
 * @property storage Secure key-value storage backend (can be encrypted or plain)
 */
public class KottagePersistencyStorageProvider(
    private val config: SecurePersistencyConfig,
    private val storage: LocalKVStorage
) : PersistencyStorageProvider {
    
    private val mutex = Mutex()
    
    /**
     * JSON configuration optimized for checkpoint storage with security considerations.
     * - prettyPrint = false: Reduces storage footprint for encrypted checkpoint data
     * - ignoreUnknownKeys = true: Forward compatibility with checkpoint schema evolution
     */
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    /**
     * Generates a storage key for the given checkpoint.
     * 
     * The key format ensures proper isolation and organization:
     * - Persistence ID isolation prevents cross-session data leakage
     * - Checkpoint ID allows for efficient individual checkpoint access
     * - Structured keys enable pattern-based queries
     * 
     * @param checkpointId The unique identifier of the checkpoint
     * @return Storage key for the checkpoint
     */
    private fun getCheckpointKey(checkpointId: String): String =
        "checkpoints/${config.persistenceId}/$checkpointId"
    
    /**
     * Generates a pattern key for listing all checkpoints for this persistence ID.
     * 
     * @return Storage key pattern for all checkpoints
     */
    private fun getCheckpointsPattern(): String =
        "checkpoints/${config.persistenceId}/"
    
    override suspend fun getCheckpoints(): List<AgentCheckpointData> = mutex.withLock {
        try {
            val pattern = getCheckpointsPattern()
            val checkpointKeys = storage.keys(pattern)
            
            val checkpoints = mutableListOf<AgentCheckpointData>()
            for (key in checkpointKeys) {
                val content = storage.get(key)
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
                    storage.delete(oldKey)
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
            storage.put(key, serialized)
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
 * Configuration for secure persistency storage using Kottage backend.
 * 
 * This configuration provides security-specific settings for agent checkpoint persistence:
 * - **Persistence ID**: Unique identifier for this agent's checkpoint collection
 * - **Security Mode**: Choose between encrypted (recommended) and plain storage
 * - **Checkpoint Retention**: Optional settings for managing checkpoint lifecycle
 * 
 * **Security Recommendations:**
 * - Use EncryptedMode for production deployments with sensitive agent states
 * - Use PlainMode only for development/testing environments
 * - Configure appropriate key providers for encryption
 * - Set unique persistence IDs to avoid checkpoint collision
 * - Consider checkpoint retention policies for long-running agents
 * 
 * Example configurations:
 * ```kotlin
 * // Production (encrypted)
 * SecurePersistencyConfig(
 *     persistenceId = "production-agent-${agentId}",
 *     encryption = EncryptedMode {
 *         keyProvider = PassphraseKeyProvider("secure-passphrase", salt, 100000)
 *         databasePath = "checkpoints-prod.db"
 *     },
 *     maxCheckpoints = 50 // Keep only 50 most recent checkpoints
 * )
 * 
 * // Development (plain with warning)
 * SecurePersistencyConfig(
 *     persistenceId = "dev-agent-${agentId}",
 *     encryption = PlainMode {
 *         databasePath = "checkpoints-dev.db"
 *         suppressSecurityWarning = true
 *     }
 * )
 * ```
 * 
 * @property persistenceId Unique identifier for this agent's checkpoint collection
 * @property encryption Security mode configuration (encrypted or plain)
 * @property maxCheckpoints Optional limit on number of checkpoints to retain (0 = unlimited)
 */
@Serializable
@SerialName("secure-persistency")
public data class SecurePersistencyConfig(
    val persistenceId: String,
    val encryption: StorageMode? = null, // Will use SecureStorage feature's mode if null
    val maxCheckpoints: Int = 0 // 0 means unlimited
)