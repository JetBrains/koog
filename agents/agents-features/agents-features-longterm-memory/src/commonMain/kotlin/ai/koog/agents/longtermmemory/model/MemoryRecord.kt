package ai.koog.agents.longtermmemory.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * Represents a memory record that can be stored in a vector database.
 *
 * @property content The main textual content to be embedded and searched
 * @property id Unique identifier for the record
 * @property metadata Flexible key-value metadata for filtering and custom fields
 */
@Serializable
public data class MemoryRecord(
    /**
     * The main textual content to be embedded and searched
     */
    val content: String,

    /**
     * Unique identifier for the record
     */
    val id: String? = null,

    /**
     * Flexible key-value metadata for filtering and custom fields
     */
    val metadata: JsonObject? = null,

    /**
     * Timestamp indicating when the memory record was created
     */
    val timestamp: Instant? = null,
)
