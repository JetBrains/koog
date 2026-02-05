package ai.koog.agents.core.optimization.core

/**
 * Training or validation data for prompt optimization.
 *
 * An example consists of a map of field names to values, where one field is designated
 * as the label (expected output). During optimization, examples are used to evaluate
 * candidate configurations and bootstrap few-shot demonstrations.
 *
 * Example usage:
 * ```kotlin
 * // For a classification task
 * val example = Example(
 *     data = mapOf(
 *         "text" to "The product exceeded my expectations!",
 *         "sentiment" to "positive"
 *     ),
 *     labelKey = "sentiment"
 * )
 *
 * // Input only (for inference)
 * val inputData = example.inputOnly() // {"text" -> "The product..."}
 *
 * // Expected label
 * val expected = example.label // "positive"
 * ```
 *
 * @property data Map from field name to string value. Contains both inputs and the label.
 * @property labelKey The key in [data] that contains the expected output/label.
 *  If null, this example has no label (unlabeled data for semi-supervised learning).
 */
public data class Example(
    val data: Map<String, String>,
    val labelKey: String? = null,
) {
    /**
     * Gets the input data only, excluding the label field.
     *
     * Used for inference where we don't want the expected answer in the prompt.
     *
     * @return A map containing all data fields except the label.
     */
    public fun inputOnly(): Map<String, String> {
        return if (labelKey != null) data - labelKey else data
    }

    /**
     * Gets the label (expected output) value.
     *
     * @return The label value, or null if this is an unlabeled example.
     */
    public val label: String?
        get() = labelKey?.let { data[it] }

    /**
     * Gets a specific field value from the data.
     *
     * @param key The field name.
     * @return The field value, or null if not present.
     */
    public operator fun get(key: String): String? = data[key]

    /**
     * Checks if this example has a label.
     */
    public val hasLabel: Boolean
        get() = labelKey != null && data.containsKey(labelKey)
}

/**
 * A metric function that scores how well an actual output matches an expected output.
 *
 * Metrics are used during optimization to evaluate candidate configurations. They should
 * return a score between 0.0 (no match) and 1.0 (perfect match), though other ranges are
 * acceptable depending on the optimization algorithm.
 *
 * Common metrics:
 * - Exact match: 1.0 if strings are equal, 0.0 otherwise
 * - Contains: 1.0 if expected is contained in actual
 * - F1 score: For classification tasks
 * - Custom domain-specific metrics
 *
 * Example:
 * ```kotlin
 * val exactMatch: Metric = { expected, actual ->
 *     if (expected.equals(actual, ignoreCase = true)) 1.0 else 0.0
 * }
 *
 * val containsMatch: Metric = { expected, actual ->
 *     if (actual.contains(expected, ignoreCase = true)) 1.0 else 0.0
 * }
 * ```
 */
public typealias Metric = (expected: String, actual: String) -> Double

/**
 * Type alias for a dataset (list of examples).
 */
public typealias Dataset = List<Example>

/**
 * Common built-in metrics for optimization.
 */
public object Metrics {
    /**
     * Exact string match (case-sensitive).
     * Returns 1.0 if strings are exactly equal, 0.0 otherwise.
     */
    public val exactMatch: Metric = { expected, actual ->
        if (expected == actual) 1.0 else 0.0
    }

    /**
     * Exact string match (case-insensitive).
     * Returns 1.0 if strings are equal ignoring case, 0.0 otherwise.
     */
    public val exactMatchIgnoreCase: Metric = { expected, actual ->
        if (expected.equals(actual, ignoreCase = true)) 1.0 else 0.0
    }

    /**
     * Contains match - checks if actual contains expected.
     * Returns 1.0 if actual contains expected substring, 0.0 otherwise.
     */
    public val contains: Metric = { expected, actual ->
        if (actual.contains(expected)) 1.0 else 0.0
    }

    /**
     * Contains match (case-insensitive).
     * Returns 1.0 if actual contains expected substring ignoring case, 0.0 otherwise.
     */
    public val containsIgnoreCase: Metric = { expected, actual ->
        if (actual.contains(expected, ignoreCase = true)) 1.0 else 0.0
    }

    /**
     * Normalized Levenshtein similarity.
     * Returns a value between 0.0 (completely different) and 1.0 (identical).
     */
    public val levenshteinSimilarity: Metric = { expected, actual ->
        if (expected == actual) {
            1.0
        } else {
            val maxLen = maxOf(expected.length, actual.length)
            if (maxLen == 0) {
                1.0
            } else {
                1.0 - (levenshteinDistance(expected, actual).toDouble() / maxLen)
            }
        }
    }
}

/**
 * Computes the Levenshtein (edit) distance between two strings.
 */
private fun levenshteinDistance(s1: String, s2: String): Int {
    val m = s1.length
    val n = s2.length

    if (m == 0) return n
    if (n == 0) return m

    val dp = Array(m + 1) { IntArray(n + 1) }

    for (i in 0..m) dp[i][0] = i
    for (j in 0..n) dp[0][j] = j

    for (i in 1..m) {
        for (j in 1..n) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,      // deletion
                dp[i][j - 1] + 1,      // insertion
                dp[i - 1][j - 1] + cost // substitution
            )
        }
    }

    return dp[m][n]
}
