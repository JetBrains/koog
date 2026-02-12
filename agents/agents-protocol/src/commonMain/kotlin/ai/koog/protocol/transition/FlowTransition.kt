package ai.koog.protocol.transition

import ai.koog.protocol.agent.agents.transform.FlowDataTransformation
import kotlinx.serialization.Serializable

/**
 * Defines a transition (edge) between two agents in a flow graph.
 *
 * Transitions control how execution flows from one agent to another. When an agent completes,
 * the flow engine evaluates the transition's condition (if present) to determine which agent
 * should execute next.
 *
 * @property from The name of the source agent where execution is transitioning from
 * @property to The name of the destination agent where execution will transition to
 * @property condition Optional condition that must evaluate to true for this transition to be taken.
 *                      If null, the transition is always taken
 */
@Serializable
public data class FlowTransition(
    public val from: String,
    public val to: String,
    public val condition: FlowTransitionCondition? = null,
    public val transformation: FlowDataTransformation? = null
) {
    /**
     * Human-readable string representation of this transition for debugging and logging.
     *
     * Format: "from -> to" (e.g., "agentA -> agentB")
     */
    public val transitionString: String = "$from -> $to"
}
