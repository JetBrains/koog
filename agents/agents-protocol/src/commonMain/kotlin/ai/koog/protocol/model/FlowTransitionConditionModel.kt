package ai.koog.protocol.model

import ai.koog.protocol.flow.ConditionOperationKind
import ai.koog.protocol.parser.toPrimitiveFlowAgentInput
import ai.koog.protocol.transition.FlowTransitionCondition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 *
 */
@Serializable
public data class FlowTransitionConditionModel(
    val variable: String, // input.data / input.success
    val operation: String, // less_than
    val value: JsonPrimitive, // 1 / 1.0 / "one" / true
) {

    /**
     *
     */
    public fun toFlowTransitionCondition(): FlowTransitionCondition {
        val operation = ConditionOperationKind.entries.find { it.id.equals(operation, ignoreCase = true) }
            ?: error("Unsupported operation: $operation")

        return FlowTransitionCondition(
            variable,
            operation,
            value.toPrimitiveFlowAgentInput()
        )
    }
}
