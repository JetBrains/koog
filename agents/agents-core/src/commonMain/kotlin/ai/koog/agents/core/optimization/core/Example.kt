package ai.koog.agents.core.optimization.core

/**
 * Training or validation data for prompt optimization.
 *
 * An example consists of a map of mapping data field keys (node names) to data field values (node outputs).
 * When generating few-shot examples, the example for the node specified by the field key is converted to a few-shot example.
 * The field values can be any type, strings, data classes, enums, etc., matching whatever the corresponding [OptimizableNode][OptimizableNode]
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
     * Gets the label (expected output) value.
     *
     * @return The label value.
     * @throws IllegalStateException if [labelKey] is null or not present in [data].
     */
    public val label: Any
        get() = labelKey?.let { data[it] }
            ?: error("Example has no labelKey set. Check hasLabel before accessing label.")

    /**
     * Gets the label (expected output) value, or null if unavailable.
     *
     * @return The label value, or null if [labelKey] is null or not present in [data].
     */
    public val labelOrNull: Any?
        get() = labelKey?.let { data[it] }

    /**
     * Checks if this example has a label.
     */
    public val hasLabel: Boolean
        get() = labelKey != null && data.containsKey(labelKey)
}

/**
 * Type alias for a dataset (list of examples).
 */
public typealias Dataset = List<Example>
