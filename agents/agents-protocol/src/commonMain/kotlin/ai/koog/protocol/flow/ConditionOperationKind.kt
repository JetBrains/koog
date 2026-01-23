package ai.koog.protocol.flow

/**
 * Supported comparison and logical operations for flow transition conditions.
 *
 * These operations are used to evaluate conditions that determine which transition path
 * to take when an agent completes execution.
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
