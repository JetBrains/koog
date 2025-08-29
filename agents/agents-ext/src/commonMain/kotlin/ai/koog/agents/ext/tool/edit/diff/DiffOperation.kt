package ai.koog.agents.ext.tool.edit.diff

import kotlinx.serialization.Serializable

/**
 * Represents a single diff operation between two sequences.
 *
 * @param T The type of elements being compared (e.g., Lines for line-based diffs)
 * @property type The type of operation (KEEP, DELETE, INSERT)
 * @property value The value associated with this operation
 */
@Serializable
internal data class DiffOperation<T>(val type: Type, val value: T) {
    /**
     * Enum representing the type of diff operation.
     */
    @Serializable
    enum class Type {
        /**
         * Indicates that the element is present in both sequences and should be kept.
         */
        KEEP,

        /**
         * Indicates that the element is present in the source sequence but not in the target,
         * and should be deleted.
         */
        DELETE,

        /**
         * Indicates that the element is present in the target sequence but not in the source,
         * and should be inserted.
         */
        INSERT
    }
}
