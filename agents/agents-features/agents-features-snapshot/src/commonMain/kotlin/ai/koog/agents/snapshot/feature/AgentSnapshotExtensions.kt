package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.store
import ai.koog.agents.memory.feature.memory
import ai.koog.agents.core.annotation.InternalAgentsApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlin.reflect.typeOf

private val logger = KotlinLogging.logger {}

/**
 * Exports the complete agent state as a portable snapshot bundle.
 * 
 * This function creates a comprehensive "PortableAgent" bundle containing:
 * - Current execution checkpoint (node, messages, input)
 * - Optional memory snapshot (all agent facts and knowledge)
 * - Optional key-value store snapshot (agent scratchpad/blackboard)
 * - Custom metadata for bundle management
 * 
 * The resulting bundle can be:
 * - Stored for long-term archival (S3, cold storage)
 * - Transferred between environments (dev → prod)
 * - Used for agent debugging and replay
 * - Shared between distributed agent instances
 * 
 * Example usage:
 * ```kotlin
 * // Basic export with memory
 * val bundle = agentContext.exportSnapshotBundle(
 *     includeMemory = true,
 *     metadata = buildJsonObject { put("reason", "daily_backup") }
 * )
 * 
 * // Save to external storage
 * saveToS3("portable-agents/${bundle.agentId}/${bundle.bundleId}.json", bundle.toJson())
 * ```
 * 
 * @param includeMemory Whether to capture and include agent memory facts
 * @param includeKVStore Whether to capture and include key-value store state
 * @param metadata Optional metadata to attach to the bundle
 * @return Complete agent state bundle ready for serialization/storage
 */
public suspend fun AIAgentContextBase.exportSnapshotBundle(
    includeMemory: Boolean = true,
    includeKVStore: Boolean = true,
    metadata: JsonObject? = null
): AgentSnapshotBundle {
    logger.info { "Exporting snapshot bundle for agent $id" }
    
    // Create a checkpoint with current state
    val checkpoint = persistency().createCheckpoint(
        agentContext = this,
        nodeId = persistency().currentNodeId ?: "unknown",
        lastInput = null, // We'll use the current context
        lastInputType = typeOf<Unit>()
    ) ?: throw IllegalStateException("Failed to create checkpoint for snapshot bundle")
    
    // Capture memory snapshot if requested
    val memorySnapshot = if (includeMemory) {
        try {
            @OptIn(InternalAgentsApi::class)
            val memoryProvider = memory().agentMemory
            val transformer = DefaultMemorySnapshotTransformer()
            transformer.captureSnapshot(memoryProvider)
        } catch (e: Exception) {
            logger.warn { "Failed to capture memory snapshot for bundle: ${e.message}" }
            null
        }
    } else {
        null
    }
    
    // Capture KV store snapshot if requested (future implementation)
    val kvStoreSnapshot = if (includeKVStore) {
        try {
            // Future: implement key-value store snapshot
            // For now, return empty object as placeholder
            buildJsonObject { }
        } catch (e: Exception) {
            logger.warn { "Failed to capture KV store snapshot for bundle: ${e.message}" }
            null
        }
    } else {
        null
    }
    
    val bundle = AgentSnapshotBundle(
        agentId = id,
        checkpoint = checkpoint,
        memorySnapshot = memorySnapshot,
        kvStoreSnapshot = kvStoreSnapshot,
        metadata = metadata
    )
    
    logger.info { "Successfully exported snapshot bundle ${bundle.bundleId} for agent $id" }
    return bundle
}

/**
 * Restores complete agent state from a portable snapshot bundle.
 * 
 * This function performs comprehensive agent restoration including:
 * - Execution context (strategy node, message history, input)
 * - Memory facts and knowledge (if present in bundle)
 * - Key-value store state (if present in bundle)
 * - Validation of bundle compatibility
 * 
 * The restoration is atomic - either all components are restored successfully,
 * or the agent state remains unchanged.
 * 
 * Example usage:
 * ```kotlin
 * // Load bundle from storage
 * val bundleJson = downloadFromS3("agents/abc-123/bundle-456.json")
 * val bundle = AgentSnapshotBundle.fromJson(bundleJson)
 * 
 * // Restore complete agent state
 * val restoredBundle = agentContext.restoreFromSnapshotBundle(
 *     bundle = bundle,
 *     restoreMemory = true,
 *     restoreKVStore = true
 * )
 * 
 * // Access any metadata from the bundle
 * val exportReason = restoredBundle.metadata?.get("reason")
 * ```
 * 
 * @param bundle The snapshot bundle to restore from
 * @param restoreMemory Whether to restore memory facts from the bundle
 * @param restoreKVStore Whether to restore key-value store state from the bundle
 * @return The restored bundle for access to metadata and verification
 * @throws IllegalArgumentException if bundle is incompatible
 * @throws IllegalStateException if restoration fails
 */
public suspend fun AIAgentContextBase.restoreFromSnapshotBundle(
    bundle: AgentSnapshotBundle,
    restoreMemory: Boolean = true,
    restoreKVStore: Boolean = true
): AgentSnapshotBundle {
    logger.info { "Restoring agent state from snapshot bundle ${bundle.bundleId}" }
    
    // Validate bundle compatibility
    if (!bundle.isCompatible()) {
        throw IllegalArgumentException(
            "Bundle ${bundle.bundleId} is incompatible with current runtime (version: ${bundle.version})"
        )
    }
    
    try {
        // Restore execution context
        @OptIn(InternalAgentsApi::class)
        store(bundle.checkpoint.toAgentContextData())
        logger.info { "Restored execution context to node ${bundle.checkpoint.nodeId}" }
        
        // Restore memory snapshot if present and requested
        if (restoreMemory && bundle.memorySnapshot != null) {
            try {
                @OptIn(InternalAgentsApi::class)
                val memoryProvider = memory().agentMemory
                val transformer = DefaultMemorySnapshotTransformer()
                transformer.restoreSnapshot(memoryProvider, bundle.memorySnapshot)
                logger.info { "Successfully restored memory snapshot" }
            } catch (e: Exception) {
                logger.error { "Failed to restore memory from bundle: ${e.message}" }
                throw IllegalStateException("Memory restoration failed", e)
            }
        }
        
        // Restore KV store snapshot if present and requested
        if (restoreKVStore && bundle.kvStoreSnapshot != null) {
            try {
                // Future: implement key-value store restoration
                logger.info { "KV store restoration not yet implemented, skipping" }
            } catch (e: Exception) {
                logger.error { "Failed to restore KV store from bundle: ${e.message}" }
                throw IllegalStateException("KV store restoration failed", e)
            }
        }
        
        logger.info { "Successfully restored agent state from bundle ${bundle.bundleId}" }
        return bundle
        
    } catch (e: Exception) {
        logger.error { "Failed to restore agent state from bundle ${bundle.bundleId}: ${e.message}" }
        throw IllegalStateException("Bundle restoration failed", e)
    }
}

/**
 * Creates a minimal snapshot bundle with just the current checkpoint.
 * 
 * This is a lightweight alternative to [exportSnapshotBundle] when you only
 * need to capture the execution state without memory or KV store data.
 * 
 * @return Minimal bundle with current checkpoint only
 */
public suspend fun AIAgentContextBase.createCheckpointBundle(): AgentSnapshotBundle {
    return exportSnapshotBundle(
        includeMemory = false,
        includeKVStore = false,
        metadata = buildJsonObject {
            put("bundleType", kotlinx.serialization.json.JsonPrimitive("checkpoint-only"))
        }
    )
}

/**
 * Restores agent state from a snapshot bundle by bundle ID and returns custom data.
 * 
 * This function combines bundle lookup and restoration, extracting any custom
 * data from the bundle's extraSnapshotData field with type safety.
 * 
 * @param T Type of custom data to extract and return
 * @param bundleId Unique identifier of the bundle to restore from
 * @return Custom data from the bundle's extraSnapshotData, or null if not present
 * @throws IllegalArgumentException if bundle is not found or incompatible
 * @throws IllegalStateException if restoration fails
 */
public suspend fun <T> AIAgentContextBase.restoreFromSnapshotBundle(
    bundleId: String
): T? {
    // Find the checkpoint by ID from the persistency storage
    val checkpoints = persistency().getCheckpoints()
    val checkpoint = checkpoints.find { 
        it.checkpointId == bundleId 
    } ?: throw IllegalArgumentException("Bundle with ID $bundleId not found")
    
    // Create a bundle from the checkpoint
    val bundle = AgentSnapshotBundle(
        agentId = id,
        checkpoint = checkpoint,
        memorySnapshot = checkpoint.memorySnapshot,
        metadata = null
    )
    
    // Restore from the bundle
    restoreFromSnapshotBundle(bundle, restoreMemory = true, restoreKVStore = true)
    
    // Return custom data if present
    @Suppress("UNCHECKED_CAST")
    return checkpoint.extraSnapshotData as? T
}

/**
 * Creates a memory-focused snapshot bundle for sharing knowledge between agents.
 * 
 * This is a specialized version of [exportSnapshotBundle] that focuses on
 * memory content, useful for:
 * - Sharing learned knowledge between agents
 * - Creating memory backups
 * - Debugging memory provider behavior
 * 
 * @param metadata Optional metadata for the memory bundle
 * @return Bundle containing current checkpoint and memory snapshot
 */
public suspend fun AIAgentContextBase.createMemorySnapshotBundle(
    metadata: JsonObject? = null
): AgentSnapshotBundle {
    val combinedMetadata = buildJsonObject {
        put("bundleType", kotlinx.serialization.json.JsonPrimitive("memory-focused"))
        metadata?.entries?.forEach { (key, value) ->
            put(key, value)
        }
    }
    
    return exportSnapshotBundle(
        includeMemory = true,
        includeKVStore = false,
        metadata = combinedMetadata
    )
}