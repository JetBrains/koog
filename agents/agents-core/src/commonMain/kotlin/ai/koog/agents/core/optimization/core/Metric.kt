package ai.koog.agents.core.optimization.core

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
