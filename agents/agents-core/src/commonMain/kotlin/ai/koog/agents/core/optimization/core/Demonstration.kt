package ai.koog.agents.core.optimization.core

/**
 * A typed input-output pair for few-shot learning.
 *
 * Demonstrations are used to provide examples to LLM nodes during prompt optimization.
 * They can be manually created from labeled data or automatically generated via bootstrapping.
 *
 * @param TInput The type of the input data.
 * @param TOutput The type of the output data.
 * @property input The example input value.
 * @property output The example output value (the expected or generated response).
 * @property isBootstrapped True if this demonstration was generated via bootstrap execution
 *  rather than being a manually labeled example.
 */
public data class Demonstration<TInput, TOutput>(
    val input: TInput,
    val output: TOutput,
    val isBootstrapped: Boolean = false,
)
