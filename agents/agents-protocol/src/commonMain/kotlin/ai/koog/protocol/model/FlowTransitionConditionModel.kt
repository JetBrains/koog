package ai.koog.protocol.model

import ai.koog.protocol.flow.ConditionOperationKind
import ai.koog.protocol.parser.toPrimitiveFlowDataType
import ai.koog.protocol.transition.FlowTransitionCondition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * Serializable model for conditional flow transitions.
 *
 * Defines a condition that must be satisfied for a transition to be taken between agents.
 * Conditions compare a variable from the agent's output against a specified value.
 *
 * @property variable The variable path to evaluate (e.g., "input.data", "input.success")
 * @property operation The comparison operation to perform (e.g., "equals", "less_than", "more_than")
 * @property value The value to compare against (can be int, double, string, or boolean)
 */
@Serializable
public data class FlowTransitionConditionModel(
    val variable: String, // input.data / input.success
    val operation: String, // less_than
    val value: JsonPrimitive, // 1 / 1.0 / "one" / true
) {

    /**
     * Converts this serializable model to a runtime [FlowTransitionCondition] instance.
     *
     * @return A runtime [FlowTransitionCondition] object ready for evaluation
     * @throws IllegalStateException if the operation string is not recognized
     */
    public fun toFlowTransitionCondition(): FlowTransitionCondition {
        val operation = ConditionOperationKind.entries.find { it.id.equals(operation, ignoreCase = true) }
            ?: error("Unsupported operation: $operation")

        return FlowTransitionCondition(
            variable,
            operation,
            value.toPrimitiveFlowDataType()
        )
    }
}
