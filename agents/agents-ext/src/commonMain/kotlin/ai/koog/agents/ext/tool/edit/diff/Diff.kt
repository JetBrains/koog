package ai.koog.agents.ext.tool.edit.diff

import kotlinx.serialization.Serializable

/**
 * Represents a collection of diff operations that can transform one sequence into another.
 *
 * @param T The type of elements being compared (e.g., Lines for line-based diffs)
 * @property operations The ordered list of operations to be applied
 */
@Serializable
internal class Diff<T>(val operations: List<DiffOperation<T>>) {
    /**
     * Creates a Diff from a variable number of operations.
     *
     * This is a convenience constructor equivalent to passing a List of operations.
     *
     * @param operations The operations to include in order of execution
     */
    constructor(vararg operations: DiffOperation<T>) : this(
        operations.toList()
    )

    /**
     * Applies all diff operations to the original sequence in order, producing a new sequence.
     *
     * @param original The original sequence to transform
     * @return A new list with all operations applied
     */
    fun apply(original: List<T>): List<T> {
        val result = mutableListOf<T>()
        var originalIndex = 0

        for (operation in operations) {
            when (operation.type) {
                DiffOperation.Type.KEEP -> {
                    result.add(original[originalIndex])
                    originalIndex++
                }

                DiffOperation.Type.DELETE -> {
                    // Skip this element in the original sequence
                    originalIndex++
                }

                DiffOperation.Type.INSERT -> {
                    // Add the new element to the result
                    result.add(operation.value)
                }
            }
        }

        return result
    }
}
