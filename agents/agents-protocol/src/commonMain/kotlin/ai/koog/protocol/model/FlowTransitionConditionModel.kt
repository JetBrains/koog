package ai.koog.protocol.model

import ai.koog.protocol.agent.InputBoolean
import ai.koog.protocol.agent.InputDouble
import ai.koog.protocol.agent.InputInt
import ai.koog.protocol.agent.InputString
import ai.koog.protocol.agent.Primitive
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
    public fun toFlowTransitionCondition(): FlowTransitionCondition {
        val operation = ConditionOperationKind.entries.find { it.id.equals(operation, ignoreCase = true) }
            ?: error("Unsupported operation: $operation")

        return FlowTransitionCondition(
            variable,
            operation,
            value.toFlowAgentInput()
        )
    }

    //region Private Methods

    private fun JsonPrimitive.toFlowAgentInput(): Primitive {
        return doubleOrNull?.let { InputDouble(it) }
            ?: intOrNull?.let { InputInt(it) }
            ?: booleanOrNull?.let { InputBoolean(it) }
            ?: contentOrNull?.let { InputString(it) }
            ?: error("Unsupported primitive type: $this")
    }

    //endregion Private Methods
}
