package ai.koog.agents.ext.tool.edit.diff

/**
 * Interface for diff algorithms that compute differences between two sequences.
 *
 * This interface allows for different implementations of diff algorithms
 * to be used interchangeably, such as Myers diff, LCS diff, etc.
 *
 * @param T The type of elements being compared (e.g., Lines for line-based diffs)
 */
internal interface DiffAlgorithm<T> {
    /**
     * Returns the name of this diff algorithm.
     *
     * @return A string identifier for this algorithm
     */
    val id: String

    /**
     * Computes the differences between source and target sequences.
     *
     * @param source The original sequence of elements
     * @param target The modified sequence of elements
     * @return A list of [DiffOperation] objects representing the operations needed
     *         to transform the source sequence into the target sequence
     */
    fun diff(source: List<T>, target: List<T>): Diff<T>
}
