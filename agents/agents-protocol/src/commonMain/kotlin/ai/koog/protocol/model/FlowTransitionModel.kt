package ai.koog.protocol.model

import ai.koog.protocol.transition.FlowTransition
import kotlinx.serialization.Serializable

/**
 * Flow transitions section.
 */
@Serializable
public data class FlowTransitionModel(
    public val from: String,
    public val to: String,
    public val condition: FlowTransitionConditionModel? = null
) {

    /**
     *
     */
    public fun toFlowTransition(): FlowTransition {
        return FlowTransition(from, to, condition?.toFlowTransitionCondition())
    }
}
