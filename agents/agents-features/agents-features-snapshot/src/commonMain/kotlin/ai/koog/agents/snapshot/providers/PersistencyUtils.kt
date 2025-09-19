package ai.koog.agents.snapshot.providers

import kotlinx.serialization.json.Json

/**
 * Utility object containing configurations and utilities for handling persistency-related operations.
 */
public object PersistencyUtils {
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
}
