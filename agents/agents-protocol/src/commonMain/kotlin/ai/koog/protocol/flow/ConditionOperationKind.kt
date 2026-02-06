package ai.koog.protocol.flow

/**
 * Supported comparison and logical operations for flow transition conditions.
 *
 * These operations are used to evaluate conditions that determine which transition path
 * to take when an agent completes execution.
 *
 * Comparison operations (work with numbers and strings):
 * - EQUALS: Values are equal (==)
 * - NOT_EQUALS: Values are not equal (!=)
 * - MORE: Left value is greater than right value (>)
 * - LESS: Left value is less than right value (<)
 * - MORE_OR_EQUAL: Left value is greater than or equal to right value (>=)
 * - LESS_OR_EQUAL: Left value is less than or equal to right value (<=)
 *
 * Logical operations (work with booleans):
 * - NOT: Logical negation (!)
 * - AND: Logical AND (&&)
 * - OR: Logical OR (||)
 *
 * @property id String identifier used for serialization and configuration files
 */
public enum class ConditionOperationKind(public val id: String) {
    EQUALS("equals"),
    NOT_EQUALS("not_equals"),
    MORE("more"),
    LESS("less"),
    MORE_OR_EQUAL("more_or_equal"),
    LESS_OR_EQUAL("less_or_equal"),
    NOT("not"),
    AND("and"),
    OR("or")
}
