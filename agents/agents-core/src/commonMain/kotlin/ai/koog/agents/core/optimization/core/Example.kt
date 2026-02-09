package ai.koog.agents.core.optimization.core

/**
 * Training or validation data for prompt optimization.
 *
 * An example consists of a map of mapping data field keys (node names) to data field values (node outputs).
 * When generating few-shot examples, the example for the node specified by the field key is converted to a few-shot example.
 * The field values can be any type, strings, data classes, enums, etc., matching whatever the corresponding [OptimizableNode][ai.koog.agents.core.optimization.OptimizableNode]
 *
 * If given, `labelKey` defines the key in [data] that contains the expected output for the whole agent.
 *
 * @property data Map from field name to value. Contains both inputs and the label.
 *  Values can be any type that the corresponding node outputs.
 * @property labelKey The key in [data] that contains the expected output/label.
 *  If null, this example has no label (unlabeled data for semi-supervised learning).
 */
public data class Example(
    val data: Map<String, Any>,
    val labelKey: String? = null,
) {
    /**
     * Gets the input data only, excluding the label field.
     *
     * Used for inference where we don't want the expected answer in the prompt.
     *
     * @return A map containing all data fields except the label.
     */
    public fun inputOnly(): Map<String, Any> {
        return if (labelKey != null) data - labelKey else data
    }

    /**
     * Gets the label (expected output) value.
     *
     * @return The label value, or null if this is an unlabeled example.
     */
    public val label: Any?
        get() = labelKey?.let { data[it] }

    /**
     * Gets a specific field value from the data.
     *
     * @param key The field name.
     * @return The field value, or null if not present.
     */
    public operator fun get(key: String): Any? = data[key]

    /**
     * Checks if this example has a label.
     */
    public val hasLabel: Boolean
        get() = labelKey != null && data.containsKey(labelKey)
}

/**
 * A metric function that scores how well an actual output matches an expected output.
 *
 * Metrics are used during optimization to evaluate candidate configurations. For example, they may
 * return a score between 0.0 (no match) and 1.0 (perfect match), though other ranges are
 * acceptable depending on the optimization algorithm.
 *
 * The type parameter [T] is tied to the strategy's output type, giving compile-time safety
 * that the metric matches the pipeline being optimized.
 *
 * @param T The type of the values being compared (matches the strategy's output type).
 */
public typealias Metric<T> = (expected: T, actual: T) -> Double

/**
 * Type alias for a dataset (list of examples).
 */
public typealias Dataset = List<Example>
