package ai.koog.agents.core.optimization.optimizers.utils

import ai.koog.agents.core.optimization.core.Demonstration
import kotlin.random.Random

/**
 * Randomly samples up to [k] demonstrations from [candidates].
 *
 * @param candidates The pool of demonstrations to sample from.
 * @param k Maximum number of demonstrations to return.
 * @param random The random source for shuffling.
 * @return A list of up to [k] randomly selected demonstrations.
 */
public fun <Input, Output> sampleLabeledDemonstrations(
    candidates: List<Demonstration<Input, Output>>,
    k: Int,
    random: Random,
): List<Demonstration<Input, Output>> {
    return candidates.shuffled(random).take(k.coerceAtMost(candidates.size))
}
