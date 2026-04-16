package ai.koog.agents.longtermmemory.model

import ai.koog.rag.base.TextDocument

/**
 * Represents a memory record that can be stored in a vector database.
 *
 * @property content The main textual content to be embedded and searched
 * @property id Unique identifier for the record
 * @property metadata Flexible key-value metadata for filtering and custom fields.
 *   Values must be primitive types (String, Number, or Boolean) when used with
 *   Spring AI vector store backends.
 */
public data class MemoryRecord(
    /**
     * The main textual content to be embedded and searched
     */
    override val content: String,
    /**
     * Unique identifier for the record
     */
    override val id: String? = null,
    /**
     * Flexible key-value metadata for filtering and custom fields
     */
    override val metadata: Map<String, Any> = emptyMap(),
) : TextDocument
