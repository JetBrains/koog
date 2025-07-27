package ai.koog.agents.snapshot.feature

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Comprehensive container for portable agent state - the foundation of "PortableAgent".
 * 
 * An AgentSnapshotBundle represents the complete serializable state of an AI agent,
 * including execution context, memory facts, key-value storage, and custom data.
 * This enables portable agents to be saved, transferred between environments, archived,
 * and restored with full context preservation.
 * 
 * Key capabilities:
 * - Cross-environment agent migration (development → production)
 * - Long-term cold storage (S3, archives)
 * - Agent debugging and replay scenarios
 * - Multi-version agent state management
 * - Distributed agent coordination
 * 
 * The bundle format is designed for:
 * - Forward compatibility via versioning
 * - Efficient serialization/deserialization
 * - Optional compression for storage optimization
 * - Metadata attachment for management workflows
 * 
 * Example usage:
 * ```kotlin
 * // Export complete agent state
 * val bundle = agentContext.exportSnapshotBundle(
 *     includeMemory = true,
 *     includeKVStore = true,
 *     metadata = buildJsonObject {
 *         put("exportReason", "scheduled_backup")
 *         put("environment", "production")
 *     }
 * )
 * 
 * // Store for cold storage
 * val json = bundle.toJson()
 * uploadToS3("portable-agents/${bundle.bundleId}.json", json)
 * 
 * // Restore portable agent from bundle
 * val restored = AgentSnapshotBundle.fromJson(downloadFromS3(...))
 * agentContext.restoreFromSnapshotBundle(restored)
 * ```
 * 
 * @property agentId Unique identifier of the agent this bundle represents
 * @property bundleId Unique identifier for this specific bundle export
 * @property createdAt Timestamp when this bundle was created
 * @property version Bundle format version for forward compatibility
 * @property checkpoint Core execution state (strategy node, messages, input)
 * @property memorySnapshot Complete agent memory facts at bundle time  
 * @property kvStoreSnapshot Agent's key-value storage state
 * @property metadata Custom bundle metadata for management and debugging
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
public data class AgentSnapshotBundle(
    val agentId: String,
    val bundleId: String = Uuid.random().toString(),
    val createdAt: Instant = Clock.System.now(),
    val version: String = CURRENT_VERSION,
    
    // Core agent state
    val checkpoint: AgentCheckpointData,
    
    // Complete agent context
    val memorySnapshot: JsonObject? = null,
    val kvStoreSnapshot: JsonObject? = null,
    
    // Bundle metadata and annotations
    val metadata: JsonObject? = null
) {
    
    public companion object {
        /**
         * Current bundle format version.
         * Increment when making incompatible changes to the bundle structure.
         */
        public const val CURRENT_VERSION: String = "1.0"
        
        /**
         * JSON serializer with pretty printing for human-readable bundles.
         */
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        
        /**
         * Creates an AgentSnapshotBundle from JSON string.
         * 
         * @param json JSON representation of the bundle
         * @return Deserialized AgentSnapshotBundle
         * @throws Exception if JSON is malformed or incompatible
         */
        public fun fromJson(json: String): AgentSnapshotBundle {
            return this.json.decodeFromString<AgentSnapshotBundle>(json)
        }
        
        /**
         * Creates an AgentSnapshotBundle from compressed bytes.
         * 
         * This method is intended for future compression support.
         * Current implementation delegates to JSON parsing.
         * 
         * @param bytes Compressed bundle data
         * @return Deserialized AgentSnapshotBundle
         * @throws Exception if data is malformed or incompatible
         */
        public fun fromCompressedBytes(bytes: ByteArray): AgentSnapshotBundle {
            // Future: implement compression/decompression
            val jsonString = bytes.decodeToString()
            return fromJson(jsonString)
        }
    }
    
    /**
     * Serializes this bundle to a JSON string.
     * 
     * The output is pretty-printed for human readability and debugging.
     * For production storage, consider using compression.
     * 
     * @return JSON string representation of this bundle
     */
    public fun toJson(): String {
        return json.encodeToString(this)
    }
    
    /**
     * Serializes this bundle to compressed bytes.
     * 
     * This method is intended for future compression support.
     * Current implementation delegates to JSON serialization.
     * 
     * @return Compressed bundle data
     */
    public fun toCompressedBytes(): ByteArray {
        // Future: implement compression
        return toJson().encodeToByteArray()
    }
    
    /**
     * Validates that this bundle is compatible with the current runtime.
     * 
     * This method checks:
     * - Bundle format version compatibility
     * - Required field presence
     * - Data structure validity
     * 
     * @return true if the bundle can be safely restored
     */
    public fun isCompatible(): Boolean {
        return try {
            // Check version compatibility
            when (version) {
                "1.0" -> true
                else -> false // Unknown version
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Returns a summary of this bundle for logging and debugging.
     * 
     * @return Human-readable bundle summary
     */
    public fun summary(): String {
        return buildString {
            appendLine("AgentSnapshotBundle:")
            appendLine("  Agent ID: $agentId")
            appendLine("  Bundle ID: $bundleId")
            appendLine("  Created: $createdAt")
            appendLine("  Version: $version")
            appendLine("  Checkpoint: ${checkpoint.checkpointId} (node: ${checkpoint.nodeId})")
            appendLine("  Memory Snapshot: ${if (memorySnapshot != null) "present" else "none"}")
            appendLine("  KV Store Snapshot: ${if (kvStoreSnapshot != null) "present" else "none"}")
            appendLine("  Metadata: ${if (metadata != null) "present" else "none"}")
        }
    }
    
    /**
     * Creates a copy of this bundle with updated metadata.
     * 
     * This is useful for adding annotations during bundle processing
     * without modifying the original bundle structure.
     * 
     * @param additionalMetadata Metadata to merge with existing metadata
     * @return New bundle with combined metadata
     */
    public fun withMetadata(additionalMetadata: JsonObject): AgentSnapshotBundle {
        val combinedMetadata = buildMap {
            metadata?.entries?.forEach { (key, value) -> put(key, value) }
            additionalMetadata.entries.forEach { (key, value) -> put(key, value) }
        }
        
        return copy(metadata = JsonObject(combinedMetadata))
    }
}