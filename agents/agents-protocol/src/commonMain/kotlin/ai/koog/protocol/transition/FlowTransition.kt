package ai.koog.protocol.transition

import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class FlowTransition(
    public val from: String,
    public val to: String,
    public val condition: FlowTransitionCondition? = null
) {
    /**
     * A string representation of the transition defined by the `from` and `to` properties.
     *
     * @return A string in the format "from -> to", indicating the transition path.
     */
    public val transitionString: String = "$from -> $to"
}
