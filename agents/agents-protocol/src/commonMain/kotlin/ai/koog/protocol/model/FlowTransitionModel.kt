package ai.koog.protocol.model

import ai.koog.protocol.agent.agents.transform.FlowDataTransformation
import ai.koog.protocol.transition.FlowTransition
import kotlinx.serialization.Serializable

/**
 * Serializable model representing a transition between two agents in a flow configuration.
 *
 * A transition defines how control flows from one agent to another, optionally based on a condition.
 *
 * @property from The name of the source agent where the transition originates
 * @property to The name of the destination agent where the transition goes
 * @property condition Optional condition that must be satisfied for the transition to occur
 */
@Serializable
public data class FlowTransitionModel(
    public val from: String,
    public val to: String,
    public val condition: FlowTransitionConditionModel? = null,
    public val transformation: FlowDataTransformation? = null
) {

    /**
     * Converts this serializable model to a runtime [FlowTransition] instance.
     *
     * @return A runtime FlowTransition object ready for execution
     */
    public fun toFlowTransition(): FlowTransition {
        return FlowTransition(from, to, condition?.toFlowTransitionCondition(), transformation)
    }
}
