package ai.koog.agents.snapshot.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.isTombstone
import kotlinx.serialization.json.Json

@Deprecated(
    "`PersistencyUtils` has been renamed to `PersistenceUtils`",
    replaceWith = ReplaceWith(
        expression = "PersistenceUtils",
        "ai.koog.agents.snapshot.providers.PersistenceUtils"
    )
)
public typealias PersistencyUtils = PersistenceUtils

/**
 * Utility object containing configurations and utilities for handling persistence-related operations.
 */
public object PersistenceUtils {
    /**
     * A preconfigured JSON instance for handling serialization and deserialization of checkpoint data.
     *
     * This configuration aims to provide flexibility and readability by:
     * - Enabling pretty printing of JSON for easier debugging and inspection.
     * - Permitting deserialization of JSON with unknown keys, ensuring compatibility with extended or updated data structures.
     * - Disabling explicit null representation in serialized JSON, resulting in more concise outputs.
     */
    public val defaultCheckpointJson: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * The name used to identify tombstone checkpoints.
     *
     * Tombstone checkpoints are special markers indicating that an agent's session has been terminated
     * or is no longer valid. This constant helps in recognizing such checkpoints during retrieval and processing.
     */
    public const val TOMBSTONE_CHECKPOINT_NAME: String = "tombstone"

    /**
     * Retrieves the latest checkpoint from a given list of agent checkpoint data.
     * The latest checkpoint is determined based on the parent-child chain.
     *
     * @param checkpoints A list of `AgentCheckpointData` containing checkpoint details
     *                    from which the latest checkpoint will be extracted.
     * @return The `AgentCheckpointData` representing the latest checkpoint, or `null` if the list is empty.
     */
    public fun latestCheckpointOf(checkpoints: List<AgentCheckpointData>): AgentCheckpointData? {
        if (checkpoints.isEmpty()) return null
        val map = checkpoints.filter { it.parentId != null }.associateBy { it.parentId!! }
        val roots = checkpoints.filter { it.parentId == null }

        return roots.firstNotNullOfOrNull { processChain(it, map) }
    }

    private fun processChain(root: AgentCheckpointData, map: Map<String, AgentCheckpointData>): AgentCheckpointData? {
        if (root.isTombstone()) {
            // If the root itself is a tombstone, return null to indicate no valid checkpoint
            return null
        }

        var current: AgentCheckpointData = root
        while (true) {
            val parent = current.checkpointId
            val child = map[parent] ?: break
            if (child == current) {
                // Prevent potential infinite loop if there's a cycle
                break
            }

            current = child
        }

        if (current.isTombstone()) {
            // If the latest checkpoint is a tombstone, return null to indicate no valid checkpoint
            return null
        }

        return current
    }
}
