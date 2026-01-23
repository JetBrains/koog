package ai.koog.flow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 *
 */
@Serializable
public data class TransitionCondition(
    val variable: String,
    val operation: ConditionOperationKind,
    val value: JsonPrimitive
)
