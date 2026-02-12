package ai.koog.protocol.agent.agents.parallel

import ai.koog.protocol.agent.FlowDataType
import ai.koog.protocol.flow.ConditionOperationKind
import kotlinx.serialization.Serializable

/**
 * Represents a condition used to compare or evaluate a variable's value during a parallel execution flow.
 *
 * This data class defines the structure for conditions that are evaluated to determine the result
 * of a parallel merge operation. The condition consists of a variable, a comparison or logical operation,
 * and a value against which the variable is evaluated.
 *
 * @property variable The fully qualified name of the variable to be evaluated (e.g., input.data or input.success).
 * @property operation The specific operation to be performed for evaluation (e.g., less_than, equals, or more).
 *                     The operation is defined by the `ConditionOperationKind` enum.
 * @property value The value to be used in the comparison or evaluation. This value is of type `FlowPrimitiveType`.
 */
@Serializable
public data class ParallelMergeCondition(
    val variable: String, // results.1.name / results.0.output
    val operation: ConditionOperationKind, // less_than
    val value: FlowDataType.FlowPrimitiveType, // 1 / 1.0 / "one" / true
)
