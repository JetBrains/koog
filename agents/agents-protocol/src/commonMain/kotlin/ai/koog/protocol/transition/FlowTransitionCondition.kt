package ai.koog.protocol.transition

import ai.koog.protocol.agent.Primitive
import ai.koog.protocol.flow.ConditionOperationKind
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class FlowTransitionCondition(
    val variable: String, // input.data / input.success
    val operation: ConditionOperationKind, // less_than
    val value: Primitive, // 1 / 1.0 / "one" / true
)
