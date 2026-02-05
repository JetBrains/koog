package ai.koog.agents.core.optimization.core

import kotlin.coroutines.CoroutineContext

/**
 * Immutable configuration for a single optimization trial.
 *
 * During MIPRO optimization, different candidate configurations (instructions, demonstrations)
 * are evaluated in parallel. Rather than mutating node instances or copying strategy graphs,
 * the configuration is passed through the coroutine context. Nodes read from this context
 * during execution, falling back to their default fields if no override is present.
 *
 * This approach enables:
 * - Parallel evaluation of different configurations without data races
 * - Immutable strategy graphs that can be safely shared across coroutines
 * - Clean separation between optimization infrastructure and node execution logic
 *
 * Example usage:
 * ```kotlin
 * // Evaluate a configuration
 * withContext(OptimizationConfig(instructions, demonstrations)) {
 *     agent.run(input)
 * }
 *
 * // In node lambda, read from context
 * val instruction = coroutineContext[OptimizationConfig]?.instructions?.get(name)
 *     ?: this@node.instruction
 * ```
 *
 * @property instructions Map from node name to instruction text override.
 * @property demonstrations Map from node name to demonstration list override.
 *  Note: Type erasure means demonstrations are stored as `Demonstration<*, *>`.
 *  Use [getTypedDemonstrations] for type-safe access with runtime casting.
 */
public class OptimizationConfig(
    public val instructions: Map<String, String> = emptyMap(),
    public val demonstrations: Map<String, List<Demonstration<*, *>>> = emptyMap(),
) : CoroutineContext.Element {

    override val key: CoroutineContext.Key<OptimizationConfig> get() = Key

    public companion object Key : CoroutineContext.Key<OptimizationConfig>

    /**
     * Gets the instruction override for a specific node.
     *
     * @param nodeName The name of the node.
     * @return The instruction override, or null if no override exists.
     */
    public fun getInstruction(nodeName: String): String? = instructions[nodeName]

    /**
     * Gets typed demonstrations for a specific node.
     *
     * This performs an unchecked cast from `Demonstration<*, *>` to the requested type.
     * The caller is responsible for ensuring type compatibility.
     *
     * @param TInput The expected input type of the demonstrations.
     * @param TOutput The expected output type of the demonstrations.
     * @param nodeName The name of the node.
     * @return The demonstrations cast to the requested type, or null if no override exists.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <TInput, TOutput> getTypedDemonstrations(
        nodeName: String
    ): List<Demonstration<TInput, TOutput>>? {
        return demonstrations[nodeName] as? List<Demonstration<TInput, TOutput>>
    }

    /**
     * Creates a new [OptimizationConfig] with additional or updated entries.
     *
     * @param additionalInstructions Instructions to add or override.
     * @param additionalDemonstrations Demonstrations to add or override.
     * @return A new config with merged values.
     */
    public fun plus(
        additionalInstructions: Map<String, String> = emptyMap(),
        additionalDemonstrations: Map<String, List<Demonstration<*, *>>> = emptyMap(),
    ): OptimizationConfig = OptimizationConfig(
        instructions = instructions + additionalInstructions,
        demonstrations = demonstrations + additionalDemonstrations,
    )

    /**
     * Creates a new [OptimizationConfig] with a single instruction override added.
     *
     * @param nodeName The node name.
     * @param instruction The instruction text.
     * @return A new config with the instruction added.
     */
    public fun withInstruction(nodeName: String, instruction: String): OptimizationConfig =
        plus(additionalInstructions = mapOf(nodeName to instruction))

    /**
     * Creates a new [OptimizationConfig] with demonstrations added for a node.
     *
     * @param TInput The input type of the demonstrations.
     * @param TOutput The output type of the demonstrations.
     * @param nodeName The node name.
     * @param demos The demonstrations to add.
     * @return A new config with the demonstrations added.
     */
    public fun <TInput, TOutput> withDemonstrations(
        nodeName: String,
        demos: List<Demonstration<TInput, TOutput>>,
    ): OptimizationConfig = plus(additionalDemonstrations = mapOf(nodeName to demos))

    override fun toString(): String =
        "OptimizationConfig(instructions=${instructions.keys}, demonstrations=${demonstrations.keys})"
}
