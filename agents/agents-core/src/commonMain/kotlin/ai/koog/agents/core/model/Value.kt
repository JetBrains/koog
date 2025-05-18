package ai.koog.agents.core.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

private object Precondition {
    const val TEMPERATURE = "Temperature must be between 0 and 1!"
}

/**
 * Value class that represents the randomness of the output in generative models.
 * Accepts values in range `[0, 1]` with each bound corresponding to deterministic
 * and un-deterministic generation outcomes respectively.
 *
 * @property value Temperature.
 * @throws IllegalArgumentException value is outside the permitted range.
 */
// TODO: unused
@JvmInline
@Serializable
internal value class Temperature(val value: Double) {

    init {
        require(value in MIN_VALUE..MAX_VALUE) { Precondition.TEMPERATURE }
    }

    override fun toString(): String = value.toString()

    companion object {
        private const val MIN_VALUE = 0.0
        private const val MAX_VALUE = 1.0

        val MIN = Temperature(MIN_VALUE)
        val MAX = Temperature(MAX_VALUE)
    }
}
