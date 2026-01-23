package ai.koog.protocol.transition

import ai.koog.protocol.agent.FlowAgentInput
import ai.koog.protocol.flow.ConditionOperationKind
import kotlinx.serialization.Serializable

/**
 * Runtime condition that must be satisfied for a flow transition to occur.
 *
 * Evaluates a comparison between a variable from the agent's output and a specified value.
 * The condition uses dot notation to access nested properties (e.g., "input.success").
 *
 * Example conditions:
 * - "input.success" equals true
 * - "input.score" more_than 0.8
 * - "input.status" equals "completed"
 *
 * @property variable Path to the variable in the agent output to evaluate (e.g., "input.data", "input.success")
 * @property operation The comparison or logical operation to perform (e.g., EQUALS, LESS, MORE, AND, OR)
 * @property value The primitive value to compare against (can be int, double, string, or boolean)
 */
@Serializable
public data class FlowTransitionCondition(
    val variable: String, // input.data / input.success
    val operation: ConditionOperationKind, // less_than
    val value: FlowAgentInput.Primitive, // 1 / 1.0 / "one" / true
)
