package ai.koog.agents.snapshot.feature

import ai.koog.agents.memory.providers.AgentMemoryProvider
import kotlinx.serialization.json.JsonObject

/**
 * Abstraction for transforming agent memory to/from snapshot format.
 * 
 * This interface provides a clean bridge between AgentMemoryProvider instances
 * and the persistency snapshot system, enabling memory-synchronized checkpoints
 * while keeping the implementations decoupled.
 * 
 * Key design principles:
 * - Memory providers remain agnostic to snapshot formats
 * - Persistency system doesn't need to understand memory internals
 * - Transformations are pluggable and testable
 * - Forward compatibility with different memory provider types
 * 
 * Use cases:
 * - Standard fact-based memory serialization
 * - Custom memory provider optimizations
 * - Encrypted memory snapshot formats
 * - Compressed or differential memory snapshots
 */
public interface MemorySnapshotTransformer {
    /**
     * Captures a snapshot of the agent's memory state.
     * 
     * This method should:
     * 1. Extract all relevant facts from the memory provider
     * 2. Serialize them into a portable JSON format
     * 3. Include any metadata needed for restoration
     * 4. Handle provider-specific optimizations if applicable
     * 
     * @param memoryProvider The memory provider to capture state from
     * @return JSON representation of the memory state, or null if no memory to capture
     * @throws Exception if memory capture fails
     */
    public suspend fun captureSnapshot(memoryProvider: ai.koog.agents.memory.providers.AgentMemoryProvider): JsonObject?

    /**
     * Restores agent memory state from a snapshot.
     * 
     * This method should:
     * 1. Parse the JSON snapshot format
     * 2. Restore facts to the memory provider
     * 3. Handle any metadata or provider-specific details
     * 4. Ensure atomic restoration (all-or-nothing)
     * 
     * @param memoryProvider The memory provider to restore state to
     * @param snapshot JSON representation of the memory state to restore
     * @throws Exception if memory restoration fails
     */
    public suspend fun restoreSnapshot(memoryProvider: ai.koog.agents.memory.providers.AgentMemoryProvider, snapshot: JsonObject)

    /**
     * Validates that a snapshot is compatible with this transformer.
     * 
     * This method enables:
     * - Forward compatibility checks
     * - Schema validation
     * - Provider-specific format verification
     * 
     * Default implementation returns true (assumes compatibility).
     * Implementations can override to add validation logic.
     * 
     * @param snapshot The snapshot to validate
     * @return true if the snapshot can be restored by this transformer
     */
    public fun isSnapshotCompatible(snapshot: JsonObject): Boolean = true
}