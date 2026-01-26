package ai.koog.protocol.model

import ai.koog.protocol.agent.FlowAgentInput
import ai.koog.protocol.flow.ConditionOperationKind
import ai.koog.protocol.transition.FlowTransitionCondition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

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
    public fun toFlowTransitionCondition(): FlowTransitionCondition? {
        val operation = ConditionOperationKind.entries.find { op ->
            op.id.equals(operation, ignoreCase = true)
        } ?: return null

        return FlowTransitionCondition(
            variable,
            operation,
            value.toFlowAgentInput()
        )
    }

    //region Private Methods

    private fun JsonPrimitive.toFlowAgentInput(): FlowAgentInput.Primitive {
        return doubleOrNull?.let { FlowAgentInput.InputDouble(it) }
            ?: intOrNull?.let { FlowAgentInput.InputInt(it) }
            ?: booleanOrNull?.let { FlowAgentInput.InputBoolean(it) }
            ?: contentOrNull?.let { FlowAgentInput.InputString(it) }
            ?: error("Unsupported primitive type: $this")
    }

    //endregion Private Methods
}
