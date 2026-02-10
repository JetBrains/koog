package ai.koog.agents.core.optimization.util

import ai.koog.agents.core.optimization.core.Demonstration


public fun <Input, Output> sampleLabeledDemonstrations(candidates: List<Demonstration<Input, Output>>, sample: Boolean, k: Int, random: kotlin.random.Random): List<Demonstration<Input, Output>> {
    val selected = if (sample) {
        candidates.shuffled(random).take(k.coerceAtMost(candidates.size))
    } else {
        candidates.take(k.coerceAtMost(candidates.size))
    }
    return selected
}
