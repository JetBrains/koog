package ai.koog.agents.core.optimization.core

/**
 * Training or validation data for prompt optimization.
 *
 * An example pairs a typed input with an optional typed label (expected output).
 * The type parameters are tied to the strategy's input and output types,
 * giving compile-time safety throughout the optimization pipeline.
 *
 * @param TInput The type of the input (matches the strategy's input type).
 * @param TOutput The type of the expected output/label (matches the strategy's output type).
 * @property input The input value for this example.
 * @property label The expected output, or null for unlabeled examples.
 */
public data class Example<TInput, TOutput>(
    val input: TInput,
    val label: TOutput? = null,
) {
    /**
     * Checks if this example has a label.
     */
    public val hasLabel: Boolean
        get() = label != null
}

/**
 * Type alias for a dataset (list of examples).
 */
public typealias Dataset<TInput, TOutput> = List<Example<TInput, TOutput>>
