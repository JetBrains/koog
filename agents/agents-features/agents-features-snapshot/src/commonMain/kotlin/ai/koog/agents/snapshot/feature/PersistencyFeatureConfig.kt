package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.snapshot.providers.NoPersistencyStorageProvider
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import kotlinx.serialization.json.JsonObject

/**
 * Configuration class for the Persistency feature with support for memory snapshots and custom data.
 * 
 * This configuration enables "PortableAgent" - comprehensive agent state capture that includes
 * execution context, memory facts, and domain-specific data for complete agent restoration.
 */
public class PersistencyFeatureConfig: FeatureConfig() {

    /**
     * Defines the storage mechanism for persisting snapshots in the feature.
     * This property accepts implementations of [PersistencyStorageProvider],
     * which manage how snapshots are stored and retrieved.
     *
     * By default, the storage is set to [NoPersistencyStorageProvider], a no-op
     * implementation that does not persist any data. To enable actual state
     * persistence, assign a custom implementation of [PersistencyStorageProvider]
     * to this property.
     */
    public var storage: PersistencyStorageProvider = NoPersistencyStorageProvider()

    /**
     * Controls whether the feature's state should be automatically persisted.
     * When enabled, changes to the checkpoint are saved after each node execution through the assigned
     * [PersistencyStorageProvider], ensuring the state can be restored later.
     *
     * Set this property to `true` to turn on automatic state persistency,
     * or `false` to disable it.
     */
    public var enableAutomaticPersistency: Boolean = false
    
    /**
     * Controls whether agent memory snapshots are included in checkpoints.
     * 
     * When enabled, the agent's memory facts will be captured and included
     * in each checkpoint, ensuring that memory state is synchronized with
     * execution state. This enables complete agent restoration including
     * learned facts and knowledge.
     * 
     * Requires the AgentMemory feature to be installed.
     * 
     * Default: false
     */
    public var includeMemorySnapshot: Boolean = false
    
    /**
     * Transformer used to capture and restore memory snapshots.
     * 
     * This abstraction allows different memory provider implementations
     * to use optimized snapshot formats while maintaining compatibility
     * with the persistency system.
     * 
     * Default: DefaultMemorySnapshotTransformer()
     */
    public var memorySnapshotTransformer: MemorySnapshotTransformer = DefaultMemorySnapshotTransformer()
    
    /**
     * Optional provider for custom snapshot data.
     * 
     * This lambda allows agents to include domain-specific state in checkpoints,
     * such as:
     * - Game world state (inventory, position, world data)
     * - IDE context (open files, cursor position, project state)  
     * - External system state (API tokens, connection state)
     * - Workflow progress (form data, multi-step process state)
     * 
     * The provider is called during checkpoint creation and should return
     * a JsonObject containing the custom data to be persisted.
     * 
     * Example:
     * ```kotlin
     * extraSnapshotDataProvider = {
     *     buildJsonObject {
     *         put("minecraft", buildJsonObject {
     *             put("position", encodeToJsonElement(playerPosition))
     *             put("inventory", encodeToJsonElement(playerInventory))
     *             put("health", playerHealth)
     *         })
     *     }
     * }
     * ```
     * 
     * Default: null (no extra data)
     */
    public var extraSnapshotDataProvider: (suspend AIAgentContextBase.() -> JsonObject)? = null
}